package com.anchor.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * The engine of Tame. Event-driven (no polling). It does three things from the foreground stream:
 *
 *  1. App rules — the instant a fully-blocked app comes forward, close it and show the takeover.
 *  2. Feed rules — while a short-form feed is open, keep a live reel counter on screen, and when
 *     the feed is scheduled-blocked or the daily reel limit is hit, close it and show the takeover.
 *  3. Counting — each swipe to the next reel ticks the day's count.
 *
 * It reads only which app is forward and which view ids are present — never content text.
 */
class AnchorAccessibilityService : AccessibilityService() {

    private var feedPkg: String? = null            // feed currently open + being counted
    private var lastScrollAt = 0L
    private var lastFeedCheckAt = 0L

    private val closeApp: () -> Unit = { performGlobalAction(GLOBAL_ACTION_HOME) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        connected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()
        if (pkg == packageName) { clearFeed(); return }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg.isNullOrEmpty()) return
                // Whole-app block first (closes the app, shows the takeover over Home).
                Enforcer.handle(this, pkg, closeApp)
                // Then feed handling — or tear the counter down if we left the feed host.
                if (FeedDetector.isFeedHost(pkg) && Store.feedRuleActive(pkg) != null) {
                    evaluateFeed(pkg, force = true)
                } else if (feedPkg != null && pkg != feedPkg) {
                    clearFeed()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg.isNullOrEmpty()) return
                if (FeedDetector.isFeedHost(pkg) && Store.feedRuleActive(pkg) != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastFeedCheckAt > FEED_CHECK_MS) { lastFeedCheckAt = now; evaluateFeed(pkg, force = false) }
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (pkg != null && pkg == feedPkg) countReel(pkg)
            }
        }
    }

    /** Is the feed open right now? Start/refresh the counter, or intervene if blocked/over-limit. */
    private fun evaluateFeed(pkg: String, force: Boolean) {
        val open = FeedDetector.isFeedOpen(rootInActiveWindow, pkg)
        if (!open) { if (feedPkg == pkg) clearFeed(); return }

        // Feed is open. Scheduled feed-block or limit already reached → close + takeover.
        val decision = Store.feedDecision(pkg)
        if (decision != null && !(decision.mode == Mode.FRICTION && Store.hasPass(pkg))) {
            clearFeed()
            Enforcer.interceptFeed(this, pkg, decision, closeApp)
            return
        }
        // Otherwise just count: show / refresh the floating counter.
        feedPkg = pkg
        ReelCounter.show(this, pkg, Store.reelCountToday(pkg), Store.reelLimitFor(pkg))
    }

    private fun countReel(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastScrollAt < SCROLL_DEBOUNCE_MS) return
        lastScrollAt = now
        val n = Store.incReel(pkg)
        ReelCounter.update(this, n, Store.reelLimitFor(pkg))
        // Crossing the limit flips the feed to its mode for the rest of the day.
        val decision = Store.feedDecision(pkg)
        if (decision != null && !(decision.mode == Mode.FRICTION && Store.hasPass(pkg))) {
            clearFeed()
            Enforcer.interceptFeed(this, pkg, decision, closeApp)
        }
    }

    private fun clearFeed() {
        if (feedPkg != null) feedPkg = null
        if (ReelCounter.isShowing) ReelCounter.hide()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        clearFeed(); connected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearFeed(); connected = false
        super.onDestroy()
    }

    companion object {
        @Volatile var connected = false
        private const val SCROLL_DEBOUNCE_MS = 650L
        private const val FEED_CHECK_MS = 700L
    }
}
