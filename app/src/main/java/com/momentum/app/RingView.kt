package com.momentum.app

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

    private var progress = 0f          // 0..1
    private var centerText = "0%"
    private var subText = "today"
    private var activeColor = 0xFF4F8CFF.toInt()
    private var trackColor = 0xFF262E39.toInt()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE6EDF3.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8B98A5.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setProgress(p: Float) { progress = p.coerceIn(0f, 1f); invalidate() }
    fun setCenterText(t: String) { centerText = t; invalidate() }
    fun setSubText(t: String) { subText = t; invalidate() }
    fun setActiveColor(c: Int) { activeColor = c; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val pad = dp(10f)
        val size = min(width, height).toFloat()
        val stroke = size * 0.085f
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        trackPaint.color = trackColor
        arcPaint.color = activeColor

        val cx = width / 2f
        val cy = height / 2f
        val r = size / 2f - pad - stroke / 2f
        rect.set(cx - r, cy - r, cx + r, cy + r)

        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        canvas.drawArc(rect, -90f, 360f * progress, false, arcPaint)

        centerPaint.textSize = size * 0.20f
        subPaint.textSize = size * 0.085f
        canvas.drawText(centerText, cx, cy + centerPaint.textSize * 0.34f, centerPaint)
        canvas.drawText(subText, cx, cy + size * 0.20f, subPaint)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
