package com.anchor.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Fires a habit's daily reminder notification, then re-arms the alarm for the next day. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent?) {
        Store.init(ctx)
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        val h = Store.habits.firstOrNull { it.id == id } ?: return
        when (intent?.action) {
            ACTION_FIRE -> {
                if (!Store.isDoneToday(h)) notify(ctx, h)
                ReminderScheduler.schedule(ctx, h)   // chain to tomorrow
            }
            ACTION_DONE -> {
                if (!Store.isDoneToday(h)) Store.toggleToday(h)
                NotificationManagerCompat.from(ctx).cancel(h.id.toInt())
            }
        }
    }

    private fun notify(ctx: Context, h: Habit) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Habit reminders", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Daily reminders for your habits." })
        }
        val openPi = PendingIntent.getActivity(ctx, h.id.toInt(),
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val donePi = PendingIntent.getBroadcast(ctx, (h.id + 1_000_000).toInt(),
            Intent(ctx, ReminderReceiver::class.java).apply {
                action = ACTION_DONE; data = Uri.parse("margin://done/${h.id}"); putExtra(EXTRA_ID, h.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_habits)
            .setContentTitle("Time for a habit")
            .setContentText(h.name)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(0, "Mark done", donePi)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(h.id.toInt(), n) } catch (_: SecurityException) {}
    }

    companion object {
        const val ACTION_FIRE = "com.anchor.app.REMIND"
        const val ACTION_DONE = "com.anchor.app.REMIND_DONE"
        const val EXTRA_ID = "id"
        private const val CHANNEL = "habit_reminders"
    }
}
