package com.anchor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Bring the shield back up after a reboot, if blocking is configured and permitted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        Store.init(context)
        // On Android 12+ starting a foreground service from a boot broadcast is a disallowed
        // background-start and can crash. The AccessibilityService is re-enabled by the system
        // on boot and is the real enforcer, so only start the polling fallback on older devices.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            Perms.coreReady(context) && Store.rules.any { it.enabled }) {
            MonitorService.start(context)
        }
        // Alarms are cleared on reboot — re-arm every habit reminder.
        ReminderScheduler.scheduleAll(context)
    }
}
