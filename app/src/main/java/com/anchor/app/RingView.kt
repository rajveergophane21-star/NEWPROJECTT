package com.anchor.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** A circular progress ring with centered text. */
class RingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var progress = 0f
    private var centerText = ""
    private var subText = ""
    private var activeColor = 0xFF3E7D5A.toInt()   // evergreen — progress
    private val trackColor = 0xFFEAE2D4.toInt()    // warm recessed track

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2B2622.toInt(); textAlign = Paint.Align.CENTER
        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.vt323)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF857A6B.toInt(); textAlign = Paint.Align.CENTER
        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.silkscreen)
    }
    private val rect = RectF()

    fun setProgress(p: Float) { progress = p.coerceIn(0f, 1f); invalidate() }
    fun setCenterText(t: String) { centerText = t; invalidate() }
    fun setSubText(t: String) { subText = t; invalidate() }
    fun setActiveColor(c: Int) { activeColor = c; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val pad = dp(8f)
        val size = min(width, height).toFloat()
        val stroke = size * 0.055f
        trackPaint.strokeWidth = stroke; arcPaint.strokeWidth = stroke
        trackPaint.color = trackColor; arcPaint.color = activeColor
        val cx = width / 2f; val cy = height / 2f
        val r = size / 2f - pad - stroke / 2f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        canvas.drawArc(rect, -90f, 360f * progress, false, arcPaint)
        centerPaint.textSize = size * 0.22f
        subPaint.textSize = size * 0.082f
        canvas.drawText(centerText, cx, cy + centerPaint.textSize * 0.34f, centerPaint)
        if (subText.isNotEmpty()) canvas.drawText(subText, cx, cy + size * 0.21f, subPaint)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
