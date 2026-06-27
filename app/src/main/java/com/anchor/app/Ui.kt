package com.anchor.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

/** Small, consistent building blocks so every screen shares one visual language. */
object Ui {
    const val TEXT = 0xFF161616.toInt()   // ink
    const val MUTED = 0xFF6E6B64.toInt()
    const val FAINT = 0xFF9A968D.toInt()
    const val SAGE = 0xFFC2674A.toInt()   // primary accent (clay)
    const val CLAY = 0xFFB23A2E.toInt()   // destructive / warning
    const val INK = 0xFFFFFFFF.toInt()    // text on accent
    const val SURFACE2 = 0xFFF4F2ED.toInt()
    const val LINE = 0xFFE7E4DD.toInt()
    const val SELECT = 0xFFF3E3DB.toInt() // clay-tint selected fill

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun scroll(c: Context): Pair<ScrollView, LinearLayout> {
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 16), dp(c, 24), dp(c, 16), dp(c, 48))
        }
        val sv = ScrollView(c).apply {
            isFillViewport = true
            clipToPadding = false
            addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return sv to col
    }

    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = ContextCompat.getDrawable(c, R.drawable.card)
        setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(c, 16) }
    }

    fun eyebrow(c: Context, t: String) = TextView(c).apply {
        text = t.uppercase(); setTextColor(FAINT); textSize = 11f
        letterSpacing = 0.18f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    /** Card / inline headline — medium weight, never bold. */
    fun title(c: Context, t: String, size: Float = 17f) = TextView(c).apply {
        text = t; setTextColor(TEXT); textSize = size
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = -0.01f
        setLineSpacing(0f, 1.1f)
    }

    /** Screen headline — light weight at large size (the editorial move). */
    fun display(c: Context, t: String) = TextView(c).apply {
        text = t; setTextColor(TEXT); textSize = 24f
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        letterSpacing = -0.015f; setLineSpacing(0f, 1.05f)
    }

    fun body(c: Context, t: String, color: Int = MUTED, size: Float = 14f) = TextView(c).apply {
        text = t; setTextColor(color); textSize = size
        setLineSpacing(dp(c, 3).toFloat(), 1f)
    }

    fun primary(c: Context, t: String): MaterialButton = MaterialButton(c).apply {
        text = t; setTextColor(INK); textSize = 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        backgroundTintList = ColorStateList.valueOf(SAGE)
        cornerRadius = dp(c, 12); isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 50))
    }

    fun ghost(c: Context, t: String): MaterialButton = MaterialButton(c,
        null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = t; setTextColor(TEXT); textSize = 15f; isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        cornerRadius = dp(c, 12); strokeColor = ColorStateList.valueOf(LINE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 50))
    }

    /** 1dp editorial hairline with vertical breathing room. */
    fun hairline(c: Context) = View(c).apply {
        setBackgroundColor(LINE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1).coerceAtLeast(1))
            .also { it.topMargin = dp(c, 12); it.bottomMargin = dp(c, 12) }
    }

    /** Eyebrow + a trailing rule that fills the remaining width (magazine kicker). */
    fun sectionHeader(c: Context, t: String): LinearLayout = row(c).apply {
        addView(eyebrow(c, t))
        addView(View(c).apply {
            setBackgroundColor(LINE)
            layoutParams = LinearLayout.LayoutParams(0, dp(c, 1).coerceAtLeast(1), 1f)
                .also { it.marginStart = dp(c, 12) }
        })
    }

    /** Large, light, tabular numeral for stats. */
    fun numeral(c: Context, n: String) = TextView(c).apply {
        text = n; setTextColor(TEXT); textSize = 30f
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        letterSpacing = -0.02f; fontFeatureSettings = "tnum"
    }

    /** Quiet editorial empty state: faint kicker, one light line, one muted sentence. */
    fun emptyState(c: Context, kicker: String, line: String, sentence: String): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(c, 20), 0, dp(c, 20))
            addView(eyebrow(c, kicker))
            addView(display(c, line).also { it.setPadding(0, dp(c, 8), 0, dp(c, 8)) })
            addView(body(c, sentence, MUTED, 14f))
        }

    fun spacer(c: Context, h: Int) = View(c).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, h))
    }

    /** A big number + label, used in stat rows. */
    fun statTile(c: Context, num: String, label: String): Pair<LinearLayout, TextView> {
        val tile = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(c, R.drawable.card2)
            setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14))
        }
        val n = numeral(c, num)
        val l = TextView(c).apply {
            text = label.uppercase(); setTextColor(MUTED); textSize = 11f
            letterSpacing = 0.12f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(c, 4), 0, 0)
        }
        tile.addView(n); tile.addView(l)
        return tile to n
    }

    fun row(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // ---- Motion -----------------------------------------------------------

    /** A light tap so actions feel physical. */
    fun haptic(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    /** Commit an action. Motion policy: no scale bounce — state changes instantly. */
    fun pop(v: View, then: (() -> Unit)? = null) {
        then?.invoke()
    }

    /** Intentionally a no-op. Content is simply present; no entrance choreography. */
    fun stagger(parent: LinearLayout) { /* motion restraint: nothing */ }
}
