package com.anchor.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * A chunky, hard-edged pixel toggle that matches the bevel kit — no rounded Material
 * switch. Drop-in for the few `isChecked` / `setOnCheckedChangeListener` call sites.
 */
class PixelToggle(context: Context) : View(context) {

    private val dens = context.resources.displayMetrics.density
    private fun px(v: Float) = v * dens
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var checkedState = false
    var isChecked: Boolean
        get() = checkedState
        set(value) { if (checkedState != value) { checkedState = value; invalidate() } }

    private var listener: ((PixelToggle, Boolean) -> Unit)? = null
    fun setOnCheckedChangeListener(l: (PixelToggle, Boolean) -> Unit) { listener = l }

    init {
        isClickable = true; isFocusable = true
        setOnClickListener {
            if (!isEnabled) return@setOnClickListener
            checkedState = !checkedState
            Ui.haptic(this)
            invalidate()
            listener?.invoke(this, checkedState)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled); alpha = if (enabled) 1f else 0.4f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(px(50f).toInt(), px(30f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = h / 2f
        // soft rounded track — evergreen when on, warm grey when off
        paint.color = if (checkedState) Ui.SAGE else 0xFFD9D5C8.toInt()
        canvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
        // round white knob, parked left when off / right when on
        val pad = px(3f)
        val d = h - pad * 2
        val cx = if (checkedState) w - pad - d / 2f else pad + d / 2f
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, h / 2f, d / 2f, paint)
    }
}
