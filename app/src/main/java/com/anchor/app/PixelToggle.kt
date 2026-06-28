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
        setMeasuredDimension(px(54f).toInt(), px(30f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val frame = px(2f)
        // recessed track: dark frame, then fill
        paint.color = Ui.CARD_BORDER
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.color = if (checkedState) Ui.SAGE else Ui.SURFACE2
        canvas.drawRect(frame, frame, w - frame, h - frame, paint)

        // raised knob (square), parked left when off / right when on
        val knob = h - frame * 2
        val left = if (checkedState) w - frame - knob else frame
        val top = frame
        paint.color = 0xFF8A7459.toInt()                       // knob shadow frame
        canvas.drawRect(left, top, left + knob, top + knob, paint)
        paint.color = 0xFFFFFDF5.toInt()                       // knob face (panel highlight)
        canvas.drawRect(left + frame, top + frame, left + knob - frame, top + knob - frame, paint)
    }
}
