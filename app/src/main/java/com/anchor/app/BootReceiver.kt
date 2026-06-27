package com.anchor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Bring the shield back up after a reboot, if blocking is configured and permitted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        Store.init(context)
        if (Perms.coreReady(context) && Store.rules.any { it.enabled }) {
            MonitorService.start(context)
        }
    }
}
