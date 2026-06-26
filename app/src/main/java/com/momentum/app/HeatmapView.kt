package com.momentum.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil

/**
 * GitHub-style contribution grid. Shows the most recent [weeks] weeks (columns),
 * 7 rows per column. The last cell is "today".
 */
class HeatmapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var done = BooleanArray(0)     // chronological, oldest -> newest, length = weeks*7
    private var weeks = 17
    private var activeColor = 0xFF4F8CFF.toInt()
    private val emptyColor = 0xFF222A34.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun setColor(c: Int) { activeColor = c; invalidate() }

    /** [days] chronological oldest->newest. */
    fun setDays(days: BooleanArray, weeksToShow: Int) {
        weeks = weeksToShow
        done = days
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val gap = dp(3f)
        val cell = (w - gap * (weeks - 1)) / weeks
        val h = (cell * 7 + gap * 6).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        if (weeks <= 0) return
        val gap = dp(3f)
        val cell = (width - gap * (weeks - 1)) / weeks
        val radius = cell * 0.28f
        val total = weeks * 7
        for (i in 0 until total) {
            val col = i / 7
            val row = i % 7
            val isDone = i < done.size && done[i]
            paint.color = if (isDone) activeColor else emptyColor
            val left = col * (cell + gap)
            val top = row * (cell + gap)
            rect.set(left, top, left + cell, top + cell)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
