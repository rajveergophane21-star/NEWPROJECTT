package com.anchor.app

import android.content.Context
import android.content.Intent

/**
 * The single decision point. Given the package that just came to the foreground,
 * decide whether to intervene and, if so, show the intercept screen ON TOP of the app.
 *
 * Unlike a "kick you to the home screen" blocker, we never press HOME — we simply
 * launch a full-screen Activity over the blocked app (allowed because we hold the
 * "draw over other apps" / SYSTEM_ALERT_WINDOW permission). The app keeps running
 * underneath; the user just can't reach it. This is the "show a screen instead of
 * closing the app" behaviour.
 */
object Enforcer {
    @Volatile private var lastPkg = ""
    @Volatile private var lastAt = 0L
    private const val DEBOUNCE_MS = 1500L

    fun handle(ctx: Context, pkg: String?) {
        if (pkg.isNullOrEmpty() || pkg == ctx.packageName) return
        if (Store.hasPass(pkg)) return
        val decision = Store.decisionFor(pkg) ?: return

        val now = System.currentTimeMillis()
        if (pkg == lastPkg && now - lastAt < DEBOUNCE_MS) return
        lastPkg = pkg; lastAt = now

        val i = Intent(ctx, InterceptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(InterceptActivity.EXTRA_PKG, pkg)
            putExtra(InterceptActivity.EXTRA_MODE, decision.mode.name)
            putExtra(InterceptActivity.EXTRA_LEFT, decision.minutesLeft)
            putExtra(InterceptActivity.EXTRA_RULE, decision.ruleName)
        }
        try { ctx.startActivity(i) } catch (_: Exception) {}
    }

    /** Allow the next appearance of the same app to re-trigger immediately. */
    fun reset() { lastPkg = ""; lastAt = 0L }
}
