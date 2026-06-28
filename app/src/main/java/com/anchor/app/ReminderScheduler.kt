package com.anchor.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.LocalDateTime
import java.time.ZoneId

/** Schedules each habit's daily reminder as a one-shot alarm that re-arms itself when it fires. */
object ReminderScheduler {

    fun scheduleAll(ctx: Context) { Store.habits.forEach { schedule(ctx, it) } }

    fun schedule(ctx: Context, h: Habit) {
        val min = h.reminderMinutes ?: run { cancel(ctx, h); return }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            // Inexact (Doze-friendly, no special permission). Re-armed each time it fires.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(min), pendingIntent(ctx, h.id))
        } catch (_: Exception) {}
    }

    fun cancel(ctx: Context, h: Habit) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx, h.id))
    }

    private fun pendingIntent(ctx: Context, id: Long): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            data = Uri.parse("margin://habit/$id")   // unique per habit so PendingIntents don't collide
            putExtra(ReminderReceiver.EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(ctx, id.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun nextTrigger(min: Int): Long {
        val now = LocalDateTime.now()
        var t = now.toLocalDate().atTime(min / 60, min % 60)
        if (!t.isAfter(now)) t = t.plusDays(1)   // already passed today → tomorrow
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
