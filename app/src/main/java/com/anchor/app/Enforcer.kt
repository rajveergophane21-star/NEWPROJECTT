package com.anchor.app

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * The single decision point. Given the package that just came to the foreground (or the feed that
 * just crossed its limit), decide whether to step in. When intervening, Tame first CLOSES the app
 * (presses Home via the supplied callback) so it stops running underneath, then shows the
 * block/friction screen — the app is never left playing behind the takeover.
 *
 * [closeApp] is supplied by the AccessibilityService and presses HOME via a global action.
 */
object Enforcer {
    @Volatile private var lastPkg = ""
    @Volatile private var lastAt = 0L
    @Volatile private var homePkg: String? = null
    private const val DEBOUNCE_MS = 600L

    /** Whole-app rules: intercept when a blocked app comes to the foreground. */
    fun handle(ctx: Context, pkg: String?, closeApp: (() -> Unit)? = null) {
        if (pkg.isNullOrEmpty() || pkg == ctx.packageName) return
        if (pkg == homePackage(ctx)) return

        val decision = Store.decisionFor(pkg) ?: return
        if (decision.mode == Mode.FRICTION && Store.hasPass(pkg)) return

        if (debounced(pkg)) return
        launch(ctx, pkg, decision, Kind.APP, closeApp)
    }

    /** Feed rules: intercept the short-form feed (over limit / scheduled feed block). */
    fun interceptFeed(ctx: Context, pkg: String, decision: Decision, closeApp: (() -> Unit)? = null) {
        if (decision.mode == Mode.FRICTION && Store.hasPass(pkg)) return
        if (debounced("feed:$pkg")) return
        launch(ctx, pkg, decision, Kind.FEED, closeApp)
    }

    private fun debounced(key: String): Boolean {
        val now = System.currentTimeMillis()
        if (key == lastPkg && now - lastAt < DEBOUNCE_MS) return true
        lastPkg = key; lastAt = now
        return false
    }

    private fun launch(ctx: Context, pkg: String, decision: Decision, kind: Kind, closeApp: (() -> Unit)?) {
        // Close the app so it stops running behind the takeover, then show the screen over Home.
        closeApp?.invoke()
        if (!Settings.canDrawOverlays(ctx)) return  // home press already removed the app from view
        val i = Intent(ctx, InterceptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(InterceptActivity.EXTRA_PKG, pkg)
            putExtra(InterceptActivity.EXTRA_MODE, decision.mode.name)
            putExtra(InterceptActivity.EXTRA_KIND, kind.name)
            putExtra(InterceptActivity.EXTRA_LEFT, decision.minutesLeft)
            putExtra(InterceptActivity.EXTRA_RULE, decision.ruleName)
        }
        try { ctx.startActivity(i) } catch (_: Exception) {}
    }

    /** Allow the next appearance of the same app/feed to re-trigger immediately. */
    fun reset() { lastPkg = ""; lastAt = 0L }

    private fun homePackage(ctx: Context): String? {
        homePkg?.let { return it }
        return try {
            val r = ctx.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            r?.activityInfo?.packageName?.also { homePkg = it }
        } catch (_: Exception) { null }
    }
}
