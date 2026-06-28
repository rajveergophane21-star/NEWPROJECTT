package com.anchor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires a habit's daily reminder: re-arm the next day, then ring the alarm if it's not done. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        Store.init(ctx)
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val h = Store.habits.firstOrNull { it.id == id } ?: return
        ReminderScheduler.schedule(ctx, h)            // re-arm tomorrow FIRST so the chain can't break
        if (!Store.isDoneToday(h)) AlarmService.start(ctx, h.id)
    }

    companion object {
        const val ACTION_FIRE = "com.anchor.app.REMIND"
        const val EXTRA_ID = "id"
    }
}
