package com.anchor.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * The engine of the shield. Event-driven (no polling): the instant a window comes to
 * the foreground, if it's a blocked app during an active rule, we show the intercept
 * screen over it. We do NOT touch or close the app — we only read which app is in front.
 */
class AnchorAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        connected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Ignore the system UI / launcher noise quickly; Enforcer also guards our own package.
        if (pkg == packageName) return
        Enforcer.handle(this, pkg)
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
