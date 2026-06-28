package com.anchor.app

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/** Full-screen "ringing" screen for a habit alarm. The AlarmService plays the sound; this is the UI. */
class AlarmActivity : AppCompatActivity() {

    private var habitId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show over the lock screen and wake the display.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        Store.init(this)
        habitId = intent.getLongExtra(AlarmService.EXTRA_ID, -1L)
        val name = intent.getStringExtra(AlarmService.EXTRA_NAME) ?: "Habit"

        setContentView(buildUi(name))
        window.statusBarColor = bg; window.navigationBarColor = bg

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stop()
        })
    }

    private val bg = 0xFF241C16.toInt()
    private val ink = 0xFFF4ECD8.toInt()
    private val accent = 0xFFE07A3E.toInt()
    private val glow = 0xFFFFCD75.toInt()

    private fun buildUi(name: String): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(bg); fitsSystemWindows = true
            setPadding(dp(34), dp(24), dp(34), dp(34))
        }
        root.addView(TextView(this).apply {
            text = "HABIT REMINDER"; setTextColor(accent); textSize = 12f
            letterSpacing = 0.18f; typeface = Ui.monoMed(this@AlarmActivity); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = name; setTextColor(ink); textSize = 30f; gravity = Gravity.CENTER
            typeface = Ui.sansMed(this@AlarmActivity); setPadding(0, dp(16), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "Time to do it."; setTextColor(0xFFC2A87E.toInt()); textSize = 15f
            gravity = Gravity.CENTER; typeface = Ui.sans(this@AlarmActivity)
        })
        root.addView(button("Mark done", glow, 0xFF241C16.toInt()) { markDone() }, btnParams(36))
        root.addView(button("Stop", 0x22FFFFFF, ink) { stop() }, btnParams(12))
        return root
    }

    private fun markDone() { AlarmService.done(this, habitId); finish() }
    private fun stop() { AlarmService.stop(this); finish() }

    private fun button(label: String, fill: Int, textColor: Int, onTap: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; setTextColor(textColor); textSize = 16f
        typeface = Ui.sansMed(this@AlarmActivity)
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(fill) }
        setPadding(0, dp(15), 0, dp(15)); isClickable = true
        setOnClickListener { Ui.haptic(this); onTap() }
    }

    private fun btnParams(topDp: Int) = LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT)
        .also { it.topMargin = dp(topDp) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
