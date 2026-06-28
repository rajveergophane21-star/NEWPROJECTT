package com.anchor.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * Rings a habit reminder like an alarm: a looping alarm sound + repeating vibration that keeps
 * going until the user taps Stop or Mark done (or a safety timeout). Shows a full-screen alarm
 * screen even over the lock screen.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vib: Vibrator? = null
    private var habitId = -1L
    private val autoStop = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        when (intent?.action) {
            ACTION_DONE -> {
                val id = intent.getLongExtra(EXTRA_ID, habitId)
                Store.habits.firstOrNull { it.id == id }?.let { if (!Store.isDoneToday(it)) Store.toggleToday(it) }
                stopEverything()
            }
            ACTION_RING -> {
                habitId = intent.getLongExtra(EXTRA_ID, -1L)
                val name = Store.habits.firstOrNull { it.id == habitId }?.name ?: "Habit"
                startForegroundAlarm(name)
                startRinging()
                autoStop.removeCallbacksAndMessages(null)
                autoStop.postDelayed({ stopEverything() }, MAX_RING_MS)
            }
            // ACTION_STOP, null (sticky restart), or anything else → stop. NOT_STICKY below means
            // the OS never restarts us with a null intent and rings a phantom alarm.
            else -> stopEverything()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundAlarm(name: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Habit alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null); enableVibration(false); description = "Ringing habit reminders."
                })
        }
        val fullScreen = PendingIntent.getActivity(this, habitId.toInt(),
            Intent(this, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_ID, habitId); putExtra(EXTRA_NAME, name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopPi = PendingIntent.getService(this, (habitId + 2_000_000).toInt(),
            Intent(this, AlarmService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val donePi = PendingIntent.getService(this, (habitId + 3_000_000).toInt(),
            Intent(this, AlarmService::class.java).apply { action = ACTION_DONE; putExtra(EXTRA_ID, habitId) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_habits)
            .setContentTitle("Time for a habit")
            .setContentText(name)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Stop", stopPi)
            .addAction(0, "Mark done", donePi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun startRinging() {
        // Stop any previous ring first so a second reminder can't stack a second MediaPlayer.
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        try { vib?.cancel() } catch (_: Exception) {}
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setDataSource(this@AlarmService, uri)
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                isLooping = true
                prepare(); start()
            }
        } catch (_: Exception) {}
        vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        try {
            val pattern = longArrayOf(0, 700, 700)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vib?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else @Suppress("DEPRECATION") vib?.vibrate(pattern, 0)
        } catch (_: Exception) {}
    }

    private fun stopEverything() {
        autoStop.removeCallbacksAndMessages(null)
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        try { vib?.cancel() } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopEverything(); super.onDestroy() }

    companion object {
        const val ACTION_RING = "com.anchor.app.ALARM_RING"
        const val ACTION_STOP = "com.anchor.app.ALARM_STOP"
        const val ACTION_DONE = "com.anchor.app.ALARM_DONE"
        const val EXTRA_ID = "id"
        const val EXTRA_NAME = "name"
        private const val CHANNEL = "habit_alarms"
        private const val NOTIF_ID = 2002
        private const val MAX_RING_MS = 120_000L   // safety: give up after 2 minutes

        fun start(ctx: Context, id: Long) {
            val i = Intent(ctx, AlarmService::class.java).apply { action = ACTION_RING; putExtra(EXTRA_ID, id) }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (_: Exception) {}
        }
        fun stop(ctx: Context) {
            try { ctx.startService(Intent(ctx, AlarmService::class.java).apply { action = ACTION_STOP }) } catch (_: Exception) {}
        }
        fun done(ctx: Context, id: Long) {
            try { ctx.startService(Intent(ctx, AlarmService::class.java).apply { action = ACTION_DONE; putExtra(EXTRA_ID, id) }) } catch (_: Exception) {}
        }
    }
}
