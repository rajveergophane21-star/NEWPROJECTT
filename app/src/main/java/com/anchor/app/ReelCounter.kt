package com.anchor.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The live reel counter: a small floating box that sits in the corner while the user is on a
 * short-form feed, showing how many reels they've watched today against their limit, with Frank
 * reacting as the number climbs. Purely informational — it never steals touches from the feed.
 */
object ReelCounter {

    private var wm: WindowManager? = null
    private var view: View? = null
    private var face: ImageView? = null
    private var bigNum: TextView? = null
    private var limitText: TextView? = null
    private var bar: View? = null
    private var card: LinearLayout? = null
    private var shownPkg: String? = null
    private var lastMood = ""

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    val isShowing: Boolean get() = view != null

    fun show(ctx: Context, pkg: String, count: Int, limit: Int?) {
        if (!Settings.canDrawOverlays(ctx)) return
        if (view != null) { shownPkg = pkg; update(ctx, count, limit); return }
        shownPkg = pkg
        val c = ctx.applicationContext
        val root = buildView(c)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(c, 12); y = dp(c, 86)
        }
        try {
            val mgr = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            mgr.addView(root, lp)
            wm = mgr; view = root
            update(c, count, limit)
        } catch (_: Exception) { view = null; wm = null }
    }

    fun update(ctx: Context, count: Int, limit: Int?) {
        bigNum?.text = count.toString()
        limitText?.text = if (limit != null) "/ $limit" else "reels"
        val ratio = if (limit != null && limit > 0) count.toFloat() / limit else 0f
        val mood = when {
            ratio >= 1f -> "panic"
            ratio >= 0.82f -> "worried"
            ratio >= 0.5f -> "neutral"
            else -> "happy"
        }
        if (mood != lastMood) { face?.setImageResource(Ui.frankRes(mood)); lastMood = mood }
        val accent = when {
            ratio >= 1f -> Ui.OVER
            ratio >= 0.82f -> Ui.POP
            else -> Ui.SAGE
        }
        bigNum?.setTextColor(accent)
        // progress bar width
        bar?.let { b ->
            val full = dp(ctx, 116)
            val w = if (limit != null) (full * ratio.coerceIn(0f, 1f)).toInt() else 0
            b.layoutParams = (b.layoutParams as LinearLayout.LayoutParams).apply { width = w.coerceAtLeast(if (limit != null) dp(ctx, 4) else 0) }
            (b.background as? GradientDrawable)?.setColor(accent)
            b.requestLayout()
        }
    }

    fun hide() {
        val v = view; val mgr = wm
        if (v != null && mgr != null) { try { mgr.removeView(v) } catch (_: Exception) {} }
        view = null; wm = null; face = null; bigNum = null; limitText = null; bar = null; card = null
        shownPkg = null; lastMood = ""
    }

    private fun buildView(c: Context): View {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 18).toFloat()
                setColor(0xF21A211D.toInt())            // near-opaque ink
                setStroke(dp(c, 1), 0x33FFFFFF)
            }
            setPadding(dp(c, 11), dp(c, 9), dp(c, 13), dp(c, 9))
            elevation = dp(c, 8).toFloat()
        }
        card = box
        val f = ImageView(c).apply {
            setImageResource(Ui.frankRes("happy"))
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(c, 30), dp(c, 30)).apply { marginEnd = dp(c, 9) }
        }
        face = f; box.addView(f)

        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val numRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val big = TextView(c).apply {
            text = "0"; setTextColor(Ui.SAGE); textSize = 21f
            typeface = Ui.serif(c); includeFontPadding = false
        }
        bigNum = big
        val lim = TextView(c).apply {
            text = "reels"; setTextColor(0xCCFFFFFF.toInt()); textSize = 11.5f
            typeface = Ui.sansMed(c); includeFontPadding = false
            setPadding(dp(c, 4), 0, 0, dp(c, 3))
        }
        limitText = lim
        numRow.addView(big); numRow.addView(lim)
        col.addView(numRow)

        // progress track + fill
        val track = LinearLayout(c).apply {
            background = GradientDrawable().apply { cornerRadius = dp(c, 3).toFloat(); setColor(0x33FFFFFF) }
            layoutParams = LinearLayout.LayoutParams(dp(c, 116), dp(c, 5)).apply { topMargin = dp(c, 5) }
        }
        val fill = View(c).apply {
            background = GradientDrawable().apply { cornerRadius = dp(c, 3).toFloat(); setColor(Ui.SAGE) }
            layoutParams = LinearLayout.LayoutParams(0, dp(c, 5))
        }
        bar = fill; track.addView(fill)
        col.addView(track)
        box.addView(col)
        return box
    }
}
