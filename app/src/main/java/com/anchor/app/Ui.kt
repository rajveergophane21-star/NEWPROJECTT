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
    const val TEXT = 0xFFECEAE4.toInt()
    const val MUTED = 0xFF878D99.toInt()
    const val FAINT = 0xFF5B616C.toInt()
    const val SAGE = 0xFF86B49A.toInt()
    const val CLAY = 0xFFD08B5F.toInt()
    const val INK = 0xFF0B0C0F.toInt()

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun scroll(c: Context): Pair<ScrollView, LinearLayout> {
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 40))
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
        setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(c, 14) }
    }

    fun eyebrow(c: Context, t: String) = TextView(c).apply {
        text = t.uppercase(); setTextColor(MUTED); textSize = 11f
        letterSpacing = 0.14f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun title(c: Context, t: String, size: Float = 20f) = TextView(c).apply {
        text = t; setTextColor(TEXT); textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = -0.01f
    }

    fun body(c: Context, t: String, color: Int = MUTED, size: Float = 14f) = TextView(c).apply {
        text = t; setTextColor(color); textSize = size
        setLineSpacing(dp(c, 3).toFloat(), 1f)
    }

    fun primary(c: Context, t: String): MaterialButton = MaterialButton(c).apply {
        text = t; setTextColor(INK); textSize = 15f
        backgroundTintList = ColorStateList.valueOf(SAGE)
        cornerRadius = dp(c, 14); isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 52))
    }

    fun ghost(c: Context, t: String): MaterialButton = MaterialButton(c,
        null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = t; setTextColor(TEXT); textSize = 15f; isAllCaps = false
        cornerRadius = dp(c, 14); strokeColor = ColorStateList.valueOf(0xFF282C35.toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 52))
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
        val n = TextView(c).apply {
            text = num; setTextColor(TEXT); textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val l = TextView(c).apply { text = label; setTextColor(MUTED); textSize = 11f; setPadding(0, dp(c,2),0,0) }
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

    /** Spring a view, then run [then] — used for check-ins and confirmations. */
    fun pop(v: View, then: (() -> Unit)? = null) {
        v.animate().scaleX(1.18f).scaleY(1.18f).setDuration(90).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            then?.invoke()
        }.start()
    }

    /** Staggered rise-and-fade for a container's direct children. */
    fun stagger(parent: LinearLayout) {
        val d = dp(parent.context, 14).toFloat()
        for (i in 0 until parent.childCount) {
            val v = parent.getChildAt(i)
            v.alpha = 0f; v.translationY = d
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay((i * 45).toLong()).setDuration(300)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }
}
