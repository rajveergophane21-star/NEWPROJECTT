package com.anchor.app

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

/** The Tame design kit: warm paper, evergreen accent, one warm-yellow pop, deep ink — clean sans. */
object Ui {
    // palette — Tame
    const val TEXT = 0xFF19211C.toInt()         // deep ink
    const val MUTED = 0xFF7B827A.toInt()
    const val FAINT = 0xFFA0A69D.toInt()
    const val SAGE = 0xFF2F7A5A.toInt()         // evergreen accent — brand / active
    const val SAGE_DEEP = 0xFF246048.toInt()
    const val POP = 0xFFF2B705.toInt()          // warm-yellow pop
    const val GREEN = 0xFF2F7A5A.toInt()        // done / progress / momentum
    const val GREEN_TEXT = 0xFF1F7A52.toInt()
    const val GREEN_WASH = 0xFFE4F2EA.toInt()
    const val BLUE = 0xFF3E8FD0.toInt()
    const val ACC_SOFT = 0xFFE4F2EA.toInt()
    const val ACC_TEXT = 0xFF1F7A52.toInt()
    const val ACC_GLOW = 0xFF2F7A5A.toInt()
    const val INK = 0xFFFFFFFF.toInt()          // white text on accent
    const val BG = 0xFFF5F3EC.toInt()           // warm paper canvas
    const val SURFACE = 0xFFFFFFFF.toInt()      // white card
    const val SURFACE2 = 0xFFF1EEE2.toInt()
    const val LINE = 0xFFECE8DC.toInt()
    const val CARD_BORDER = 0xFFECE8DC.toInt()
    const val SELECT = 0xFFE4F2EA.toInt()
    const val CLAY = 0xFFC0392B.toInt()         // danger / block
    const val CLAY_WASH = 0xFFFBEAE6.toInt()
    const val TERRA = 0xFFF2B705.toInt()        // friction / amber pop
    const val DARK = 0xFF19211C.toInt()         // ink card / takeover
    const val DARK_TEXT = 0xFFFFFFFF.toInt()
    const val OVER = 0xFFE1574C.toInt()         // over-limit warm red

    // fonts — clean modern sans (Bricolage/Hanken-flavoured): chunky black display + humanist body.
    private var displayT: Typeface? = null
    private var sansT: Typeface? = null
    private var sansMedT: Typeface? = null
    private var sansBoldT: Typeface? = null
    private var italicT: Typeface? = null

    /** Chunky display face for headlines + wordmark + big numerals. */
    fun serif(c: Context): Typeface = displayT
        ?: Typeface.create("sans-serif-black", Typeface.NORMAL).also { displayT = it }
    fun sans(c: Context): Typeface = sansT ?: Typeface.create("sans-serif", Typeface.NORMAL).also { sansT = it }
    fun sansMed(c: Context): Typeface = sansMedT ?: Typeface.create("sans-serif-medium", Typeface.NORMAL).also { sansMedT = it }
    fun sansBold(c: Context): Typeface = sansBoldT ?: Typeface.create("sans-serif", Typeface.BOLD).also { sansBoldT = it }
    fun serifItalic(c: Context): Typeface = italicT ?: Typeface.create("sans-serif", Typeface.ITALIC).also { italicT = it }
    fun mono(c: Context): Typeface = sansMed(c)
    fun monoMed(c: Context): Typeface = sansMed(c)
    fun vt(c: Context): Typeface = serif(c)
    fun press(c: Context): Typeface = serif(c)

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    // ---- Frank the monkey ------------------------------------------------
    fun frankRes(mood: String): Int = when (mood) {
        "calm", "zen", "breathe" -> R.drawable.frank_calm
        "happy", "normal" -> R.drawable.frank_normal
        "neutral" -> R.drawable.frank_normal
        "worried", "sad" -> R.drawable.frank_sad
        "crying", "over" -> R.drawable.frank_crying
        "panic" -> R.drawable.frank_panic
        "angry", "fullstop" -> R.drawable.frank_angry
        else -> R.drawable.frank_normal
    }

    /** Frank at a given mood, sized to [size] dp. */
    fun frank(c: Context, mood: String, size: Int): ImageView = ImageView(c).apply {
        setImageResource(frankRes(mood))
        adjustViewBounds = true
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(dp(c, size), dp(c, size))
    }

    fun scroll(c: Context): Pair<ScrollView, LinearLayout> {
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 20), dp(c, 10), dp(c, 20), dp(c, 52))
        }
        val sv = ScrollView(c).apply {
            isFillViewport = true; clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return sv to col
    }

    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = ContextCompat.getDrawable(c, R.drawable.card)
        setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(c, 14) }
    }

    /** Uppercase, tracked label. */
    fun eyebrow(c: Context, t: String) = TextView(c).apply {
        text = t.uppercase(); setTextColor(MUTED); textSize = 11f
        letterSpacing = 0.14f; typeface = sansMed(c); includeFontPadding = false
    }

    fun mono(c: Context, t: String, color: Int = FAINT, size: Float = 12f) = TextView(c).apply {
        text = t; setTextColor(color); textSize = size; letterSpacing = 0.02f
        typeface = sansMed(c); includeFontPadding = false
    }

    /** Chunky display headline (screen titles). */
    fun display(c: Context, t: String, size: Float = 30f) = TextView(c).apply {
        text = t; setTextColor(TEXT); textSize = size
        typeface = serif(c); includeFontPadding = false; setLineSpacing(0f, 1.02f)
        letterSpacing = -0.01f
    }

    /** Card headline — semibold sans. */
    fun serifHead(c: Context, t: String, size: Float = 20f) = TextView(c).apply {
        text = t; setTextColor(TEXT); textSize = size; typeface = sansBold(c); includeFontPadding = false
    }

    fun serifQuote(c: Context, t: String, color: Int = TEXT, size: Float = 19f) = TextView(c).apply {
        text = t; setTextColor(color); textSize = size; typeface = sansMed(c)
        setLineSpacing(0f, 1.25f)
    }

    /** Sans semibold — item names, button-like labels. */
    fun title(c: Context, t: String, size: Float = 15.5f) = TextView(c).apply {
        text = t; setTextColor(TEXT); textSize = size; typeface = sansMed(c)
    }

    fun body(c: Context, t: String, color: Int = MUTED, size: Float = 14f) = TextView(c).apply {
        text = t; setTextColor(color); textSize = size; typeface = sans(c)
        setLineSpacing(dp(c, 3).toFloat(), 1f)
    }

    /** Evergreen pill button. */
    fun primary(c: Context, t: String): TextView = TextView(c).apply {
        text = t; gravity = Gravity.CENTER; setTextColor(INK); textSize = 15f
        typeface = sansBold(c); includeFontPadding = false
        background = ContextCompat.getDrawable(c, R.drawable.btn_primary)
        setPadding(dp(c,16), dp(c,15), dp(c,16), dp(c,15))
        isClickable = true; isFocusable = true
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** White outlined button. */
    fun ghost(c: Context, t: String): TextView = TextView(c).apply {
        text = t; gravity = Gravity.CENTER; setTextColor(TEXT); textSize = 15f
        typeface = sansMed(c); includeFontPadding = false
        background = ContextCompat.getDrawable(c, R.drawable.btn_ghost)
        setPadding(dp(c,16), dp(c,15), dp(c,16), dp(c,15))
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

    /** Big bold numeral. */
    fun numeral(c: Context, n: String, size: Float = 34f, color: Int = TEXT) = TextView(c).apply {
        text = n; setTextColor(color); textSize = size; typeface = serif(c); includeFontPadding = false
    }

    fun emptyState(c: Context, kicker: String, line: String, sentence: String): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(c, 18), 0, dp(c, 18))
            addView(eyebrow(c, kicker))
            addView(display(c, line, 26f).also { it.setPadding(0, dp(c, 10), 0, dp(c, 8)) })
            addView(body(c, sentence, MUTED, 14f))
        }

    fun statTile(c: Context, num: String, label: String, color: Int = SAGE): Pair<LinearLayout, TextView> {
        val tile = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(c, R.drawable.card2)
            setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16))
        }
        val n = numeral(c, num, 28f, color)
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

    /** A soft rounded chip with text. [bg] fill, [fg] text colour. */
    fun chip(c: Context, t: String, fg: Int, bg: Int): TextView = TextView(c).apply {
        text = t; setTextColor(fg); textSize = 11.5f; typeface = sansBold(c)
        letterSpacing = 0.02f; includeFontPadding = false
        gravity = Gravity.CENTER
        setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 6))
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(c, 20).toFloat(); setColor(bg)
        }
    }

    fun switch(c: Context): PixelToggle = PixelToggle(c)

    // ---- Motion (restrained) ---------------------------------------------
    fun haptic(v: View) = v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    fun pop(v: View, then: (() -> Unit)? = null) { then?.invoke() }
    fun stagger(parent: LinearLayout) { /* none */ }
}
