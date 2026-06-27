package com.anchor.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** GitHub-style contribution grid. [weeks] columns × 7 rows; last cell is today. */
class HeatmapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var done = BooleanArray(0)
    private var weeks = 16
    private var activeColor = 0xFFC2674A.toInt()
    private val emptyColor = 0xFFF4F2ED.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun setColor(c: Int) { activeColor = c; invalidate() }
    fun setDays(days: BooleanArray, weeksToShow: Int) { weeks = weeksToShow; done = days; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val gap = dp(3f)
        val cell = (w - gap * (weeks - 1)) / weeks
        setMeasuredDimension(w, (cell * 7 + gap * 6).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        if (weeks <= 0) return
        val gap = dp(3f)
        val cell = (width - gap * (weeks - 1)) / weeks
        val radius = cell * 0.28f
        for (i in 0 until weeks * 7) {
            val col = i / 7; val row = i % 7
            val isDone = i < done.size && done[i]
            paint.color = if (isDone) activeColor else emptyColor
            val left = col * (cell + gap); val top = row * (cell + gap)
            rect.set(left, top, left + cell, top + cell)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
