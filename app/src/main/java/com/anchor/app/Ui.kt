package com.anchor.app

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

/** The Margin editorial design kit: warm paper, evergreen accent, serif + mono + sans. */
object Ui {
    // palette — pixel · daylight (warm parchment)
    const val TEXT = 0xFF3B2F2A.toInt()
    const val MUTED = 0xFF8A7459.toInt()
    const val FAINT = 0xFFB59B73.toInt()
    const val SAGE = 0xFFE07A3E.toInt()        // terracotta accent
    const val GREEN = 0xFF4FA64F.toInt()       // success / done
    const val BLUE = 0xFF3E8FD0.toInt()        // info
    const val ACC_SOFT = 0xFFF6E2CE.toInt()
    const val ACC_TEXT = 0xFFB85C28.toInt()
    const val ACC_GLOW = 0xFFE07A3E.toInt()
    const val INK = 0xFFFFFBF0.toInt()         // light text on accent
    const val SURFACE = 0xFFFBF6E9.toInt()
    const val SURFACE2 = 0xFFE4D6B8.toInt()
    const val LINE = 0xFFB59B73.toInt()
    const val CARD_BORDER = 0xFFC2A87E.toInt()
    const val SELECT = 0xFFF6E2CE.toInt()
    const val CLAY = 0xFFC8485B.toInt()        // danger
    const val TERRA = 0xFFD98E3E.toInt()       // amber / friction
    const val DARK = 0xFF2A2018.toInt()        // warm-dark card
    const val DARK_TEXT = 0xFFF4ECD8.toInt()

    // fonts (cached) — pixel set: Pixelify Sans (display/body), Silkscreen (labels),
    // VT323 (numerals/timers), Press Start 2P (logo wordmark).
    private var pixT: Typeface? = null
    private var silkT: Typeface? = null
    private var silkBoldT: Typeface? = null
    private var vtT: Typeface? = null
    private var pressT: Typeface? = null

    fun serif(c: Context): Typeface = pixT ?: ResourcesCompat.getFont(c, R.font.pixelify_sans)!!.also { pixT = it }
    fun serifItalic(c: Context): Typeface = serif(c)
    fun sans(c: Context): Typeface = serif(c)
    fun mono(c: Context): Typeface = silkT ?: ResourcesCompat.getFont(c, R.font.silkscreen)!!.also { silkT = it }
    fun monoMed(c: Context): Typeface = silkBoldT ?: ResourcesCompat.getFont(c, R.font.silkscreen_bold)!!.also { silkBoldT = it }
    fun vt(c: Context): Typeface = vtT ?: ResourcesCompat.getFont(c, R.font.vt323)!!.also { vtT = it }
    fun press(c: Context): Typeface = pressT ?: ResourcesCompat.getFont(c, R.font.press_start_2p)!!.also { pressT = it }

    private fun TextView.weight(w: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) fontVariationSettings = "'wght' $w"
    }

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun scroll(c: Context): Pair<ScrollView, LinearLayout> {
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 22), dp(c, 12), dp(c, 22), dp(c, 48))
        }
        val sv = ScrollView(c).apply {
            isFillViewport = true; clipToPadding = false
            addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return sv to col
    }

    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = ContextCompat.getDrawable(c, R.drawable.card)
        setPadding(dp(c, 19), dp(c, 19), dp(c, 19), dp(c, 19))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(c, 13) }
    }

    /** Mono, uppercase, widely tracked label. */
    fun eyebrow(c: Context, t: String) = TextView(c).apply {
        text = t.uppercase(); setTextColor(MUTED); textSize = 10f
        letterSpacing = 0.2f; typeface = monoMed(c); includeFontPadding = false
    }

    fun mono(c: Context, t: String, color: Int = FAINT, size: Float = 10.5f) = TextView(c).apply {
        text = t; setTextColor(color); textSize = size; letterSpacing = 0.04f
        typeface = mono(c); includeFontPadding = false
    }

    /** Serif screen headline (Instrument Serif). */
    fun display(c: Context, t: String, size: Float = 36f) = TextView(c).apply {
        text = t; setTextColor(TEXT); textSize = size
        typeface = serif(c); includeFontPadding = false; setLineSpacing(0f, 0.98f)
    }

    /** Serif card headline. */
    fun serifHead(c: Context, t: String, size: Float = 22f) = TextView(c).apply {
        text = t; setTextColor(TEXT); textSize = size; typeface = serif(c); includeFontPadding = false
    }

    /** Serif italic — for the user's own words (identity, prompts). */
    fun serifQuote(c: Context, t: String, color: Int = TEXT, size: Float = 21f) = TextView(c).apply {
        text = t; setTextColor(color); textSize = size; typeface = serifItalic(c)
        setLineSpacing(0f, 1.3f)
    }

    /** Sans semibold — item names, button-like labels. */
    fun title(c: Context, t: String, size: Float = 15.5f) = TextView(c).apply {
        text = t; setTextColor(TEXT); textSize = size; typeface = sans(c); weight(600)
    }

    fun body(c: Context, t: String, color: Int = MUTED, size: Float = 14f) = TextView(c).apply {
        text = t; setTextColor(color); textSize = size; typeface = sans(c)
        setLineSpacing(dp(c, 3).toFloat(), 1f)
    }

    /** Raised gold pixel button. */
    fun primary(c: Context, t: String): TextView = TextView(c).apply {
        text = t; gravity = Gravity.CENTER; setTextColor(INK); textSize = 14f
        typeface = serif(c); weight(700); includeFontPadding = false
        background = ContextCompat.getDrawable(c, R.drawable.btn_primary)
        setPadding(dp(c,16), dp(c,12), dp(c,18), dp(c,16))
        isClickable = true; isFocusable = true
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** Raised panel-coloured pixel button. */
    fun ghost(c: Context, t: String): TextView = TextView(c).apply {
        text = t; gravity = Gravity.CENTER; setTextColor(TEXT); textSize = 14f
        typeface = serif(c); weight(600); includeFontPadding = false
        background = ContextCompat.getDrawable(c, R.drawable.btn_ghost)
        setPadding(dp(c,16), dp(c,12), dp(c,18), dp(c,16))
        isClickable = true; isFocusable = true
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun hairline(c: Context) = View(c).apply {
        setBackgroundColor(LINE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1).coerceAtLeast(1))
            .also { it.topMargin = dp(c, 12); it.bottomMargin = dp(c, 12) }
    }

    fun sectionHeader(c: Context, t: String): LinearLayout = row(c).apply {
        addView(eyebrow(c, t))
        addView(View(c).apply {
            setBackgroundColor(LINE)
            layoutParams = LinearLayout.LayoutParams(0, dp(c, 1).coerceAtLeast(1), 1f).also { it.marginStart = dp(c, 12) }
        })
    }

    /** Big pixel numeral (VT323, monospaced). */
    fun numeral(c: Context, n: String, size: Float = 36f, color: Int = TEXT) = TextView(c).apply {
        text = n; setTextColor(color); textSize = size; typeface = vt(c); includeFontPadding = false
    }

    fun emptyState(c: Context, kicker: String, line: String, sentence: String): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(c, 20), 0, dp(c, 20))
            addView(eyebrow(c, kicker))
            addView(display(c, line, 28f).also { it.setPadding(0, dp(c, 10), 0, dp(c, 8)) })
            addView(body(c, sentence, MUTED, 14f))
        }

    fun statTile(c: Context, num: String, label: String): Pair<LinearLayout, TextView> {
        val tile = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(c, R.drawable.card2)
            setPadding(dp(c, 15), dp(c, 15), dp(c, 15), dp(c, 15))
        }
        val n = numeral(c, num, 30f)
        val l = eyebrow(c, label).also { it.setPadding(0, dp(c, 5), 0, 0) }
        tile.addView(n); tile.addView(l)
        return tile to n
    }

    fun row(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun spacer(c: Context, h: Int) = View(c).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, h))
    }

    /** A hard-edged pixel toggle that matches the bevel kit. */
    fun switch(c: Context): PixelToggle = PixelToggle(c)

    // ---- Motion (restrained) ---------------------------------------------
    fun haptic(v: View) = v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    fun pop(v: View, then: (() -> Unit)? = null) { then?.invoke() }
    fun stagger(parent: LinearLayout) { /* none */ }
}
