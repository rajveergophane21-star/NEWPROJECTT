package com.anchor.app

import android.content.Context
import android.content.Intent

/**
 * The single decision point. Given the package that just came to the foreground,
 * decide whether to intervene and, if so, bring up the intercept screen.
 *
 * [goHome] is supplied by the AccessibilityService (performGlobalAction HOME). Pressing
 * home first is the trick real blockers (TimeLimit, DetoxDroid) use: it reliably pulls
 * the user out of the blocked app before we show our screen, instead of fighting
 * Android's background-activity-launch limits over a foreground app.
 */
object Enforcer {
    @Volatile private var lastPkg = ""
    @Volatile private var lastAt = 0L
    private const val DEBOUNCE_MS = 1200L

    fun handle(ctx: Context, pkg: String?, goHome: (() -> Unit)?) {
        if (pkg.isNullOrEmpty() || pkg == ctx.packageName) return
        if (Store.hasPass(pkg)) return
        val decision = Store.decisionFor(pkg) ?: return

        val now = System.currentTimeMillis()
        if (pkg == lastPkg && now - lastAt < DEBOUNCE_MS) return
        lastPkg = pkg; lastAt = now

        goHome?.invoke()

        val i = Intent(ctx, InterceptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(InterceptActivity.EXTRA_PKG, pkg)
            putExtra(InterceptActivity.EXTRA_MODE, decision.mode.name)
            putExtra(InterceptActivity.EXTRA_LEFT, decision.minutesLeft)
            putExtra(InterceptActivity.EXTRA_RULE, decision.ruleName)
            putExtra(InterceptActivity.EXTRA_REASON, decision.reason)
        }
        ctx.startActivity(i)
    }

    /** Allow the next appearance of the same app to re-trigger immediately. */
    fun reset() { lastPkg = ""; lastAt = 0L }
}
