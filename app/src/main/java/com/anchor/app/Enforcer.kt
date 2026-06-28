package com.anchor.app

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * The single decision point. Given the package that just came to the foreground, decide whether
 * to intervene and, if so, show the intercept screen ON TOP of the app (never close it).
 *
 * [goHome] (supplied by the AccessibilityService) presses HOME via a global action. It's used only
 * as a fallback when the overlay permission has been revoked and we can't draw the intercept.
 */
object Enforcer {
    @Volatile private var lastPkg = ""
    @Volatile private var lastAt = 0L
    @Volatile private var homePkg: String? = null
    private const val DEBOUNCE_MS = 600L

    fun handle(ctx: Context, pkg: String?, goHome: (() -> Unit)? = null) {
        if (pkg.isNullOrEmpty() || pkg == ctx.packageName) return
        // Never intercept the home launcher — blocking it would trap the user on every Home press.
        if (pkg == homePackage(ctx)) return

        val decision = Store.decisionFor(pkg) ?: return
        // A friction "open anyway" pass excuses FRICTION only — it can never let a BLOCK through.
        if (decision.mode == Mode.FRICTION && Store.hasPass(pkg)) return

        val now = System.currentTimeMillis()
        if (pkg == lastPkg && now - lastAt < DEBOUNCE_MS) return
        lastPkg = pkg; lastAt = now

        // The overlay permission is what lets us launch the intercept over the app from the
        // background. If it's been revoked, fall back to kicking HOME so the app isn't usable.
        if (!Settings.canDrawOverlays(ctx)) { goHome?.invoke(); return }

        val i = Intent(ctx, InterceptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(InterceptActivity.EXTRA_PKG, pkg)
            putExtra(InterceptActivity.EXTRA_MODE, decision.mode.name)
            putExtra(InterceptActivity.EXTRA_LEFT, decision.minutesLeft)
            putExtra(InterceptActivity.EXTRA_RULE, decision.ruleName)
        }
        try { ctx.startActivity(i) } catch (_: Exception) { goHome?.invoke() }
    }

    /** Allow the next appearance of the same app to re-trigger immediately. */
    fun reset() { lastPkg = ""; lastAt = 0L }

    /** The current home/launcher package (cached). */
    private fun homePackage(ctx: Context): String? {
        homePkg?.let { return it }
        return try {
            val r = ctx.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            r?.activityInfo?.packageName?.also { homePkg = it }
        } catch (_: Exception) { null }
    }
}
