package com.anchor.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * The engine of the shield. Event-driven (no polling), this fires the instant a
 * window comes to the foreground. If it belongs to a blocked app during an active
 * rule, we press HOME and show the intercept.
 *
 * This is how every reliable non-root blocker works; UsageStats polling in
 * MonitorService is only a fallback for when this service isn't enabled.
 */
class AnchorAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        connected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        Enforcer.handle(this, pkg) { performGlobalAction(GLOBAL_ACTION_HOME) }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        super.onDestroy()
    }

    companion object {
        @Volatile var connected = false
    }
}
