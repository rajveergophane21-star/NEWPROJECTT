package com.anchor.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * The Shield. A persistent foreground service that samples the foreground app a
 * few times a second and, when a blocked app surfaces during an active rule,
 * launches the intercept screen over it.
 *
 * Non-root. Detection uses UsageStatsManager (PACKAGE_USAGE_STATS); the intercept
 * is a normal Activity launched from the background, which is permitted because we
 * hold SYSTEM_ALERT_WINDOW.
 */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usm: UsageStatsManager
    private var lastForeground = ""

    private val tick = object : Runnable {
        override fun run() {
            try { sample() } catch (_: Exception) {}
            handler.postDelayed(this, SAMPLE_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        // On Android 12+ a background/boot start can be disallowed. The AccessibilityService
        // is the real enforcer, so degrade gracefully rather than crash.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (_: Exception) {
            stopSelf(); return
        }
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -----------------------------------------------------------------------

    private fun sample() {
        // The AccessibilityService is the real enforcer. Only fall back to polling
        // (which can't press HOME and is less reliable) when it isn't enabled.
        if (AnchorAccessibilityService.connected) return
        val pkg = foregroundPackage() ?: return
        if (pkg == lastForeground) return   // already handled this foreground; wait for a change
        lastForeground = pkg
        Enforcer.handle(this, pkg)
    }

    /** Most recently foregrounded package over the last few seconds. */
    private fun foregroundPackage(): String? {
        val end = System.currentTimeMillis()
        val begin = end - 8_000
        val events = usm.queryEvents(begin, end)
        val e = UsageEvents.Event()
        var pkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                e.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                pkg = e.packageName
            }
        }
        return pkg
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Shield", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps your blocking rules running."
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Tame is on watch")
            .setContentText("Frank's keeping an eye on your feeds.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val CHANNEL = "anchor_shield"
        const val NOTIF_ID = 1001
        private const val SAMPLE_MS = 800L

        fun start(ctx: Context) {
            val i = Intent(ctx, MonitorService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (_: Exception) { /* accessibility service still enforces */ }
        }
        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, MonitorService::class.java)) } catch (_: Exception) {}
        }
    }
}
