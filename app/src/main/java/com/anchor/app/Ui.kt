package com.anchor.app

import android.content.Context
import android.content.res.ColorStateList
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
import com.google.android.material.button.MaterialButton

/** The Margin editorial design kit: warm paper, evergreen accent, serif + mono + sans. */
object Ui {
    // palette
    const val TEXT = 0xFF1B1813.toInt()
    const val MUTED = 0xFF6E6456.toInt()
    const val FAINT = 0xFFA99D89.toInt()
    const val SAGE = 0xFF3B5141.toInt()        // evergreen accent
    const val ACC_SOFT = 0xFFE7ECE0.toInt()
    const val ACC_TEXT = 0xFF36493A.toInt()
    const val ACC_GLOW = 0xFF9DB48F.toInt()
    const val INK = 0xFFF6F1E7.toInt()         // text on accent / on dark
    const val SURFACE2 = 0xFFFBF8F1.toInt()
    const val LINE = 0xFFE7DECE.toInt()
    const val CARD_BORDER = 0xFFEBE3D5.toInt()
    const val SELECT = 0xFFE7ECE0.toInt()
    const val CLAY = 0xFF7E332B.toInt()
    const val TERRA = 0xFF8A5A20.toInt()
    const val DARK = 0xFF232019.toInt()
    const val DARK_TEXT = 0xFFF1EAD9.toInt()

    // fonts (cached)
    private var serifT: Typeface? = null
    private var serifItalicT: Typeface? = null
    private var monoT: Typeface? = null
    private var monoMedT: Typeface? = null
    private var sansT: Typeface? = null

    fun serif(c: Context): Typeface = serifT ?: ResourcesCompat.getFont(c, R.font.instrument_serif_regular)!!.also { serifT = it }
    fun serifItalic(c: Context): Typeface = serifItalicT ?: ResourcesCompat.getFont(c, R.font.instrument_serif_italic)!!.also { serifItalicT = it }
    fun mono(c: Context): Typeface = monoT ?: ResourcesCompat.getFont(c, R.font.ibm_plex_mono_regular)!!.also { monoT = it }
    fun monoMed(c: Context): Typeface = monoMedT ?: ResourcesCompat.getFont(c, R.font.ibm_plex_mono_medium)!!.also { monoMedT = it }
    fun sans(c: Context): Typeface = sansT ?: ResourcesCompat.getFont(c, R.font.hanken_grotesk)!!.also { sansT = it }

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
        text = t.uppercase(); setTextColor(FAINT); textSize = 10f
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

    fun primary(c: Context, t: String): MaterialButton = MaterialButton(c).apply {
        text = t; setTextColor(INK); textSize = 15f; typeface = sans(c); weight(600)
        backgroundTintList = ColorStateList.valueOf(SAGE)
        cornerRadius = dp(c, 14); isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 52))
    }

    fun ghost(c: Context, t: String): MaterialButton = MaterialButton(c,
        null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = t; setTextColor(TEXT); textSize = 15f; isAllCaps = false; typeface = sans(c); weight(600)
        cornerRadius = dp(c, 14); strokeColor = ColorStateList.valueOf(CARD_BORDER)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 52))
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

    /** Big serif numeral. */
    fun numeral(c: Context, n: String, size: Float = 28f, color: Int = TEXT) = TextView(c).apply {
        text = n; setTextColor(color); textSize = size; typeface = serif(c); includeFontPadding = false
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

    // ---- Motion (restrained) ---------------------------------------------
    fun haptic(v: View) = v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    fun pop(v: View, then: (() -> Unit)? = null) { then?.invoke() }
    fun stagger(parent: LinearLayout) { /* none */ }
}
