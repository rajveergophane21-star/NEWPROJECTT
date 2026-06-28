package com.anchor.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.anchor.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val tabs = listOf("Blocks", "Habits")
    private val ticks = mutableListOf<View>()
    private val labels = mutableListOf<TextView>()
    private var current = 0
    private var firstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        buildBar()
        ReminderScheduler.scheduleAll(this)   // make sure habit reminders are armed
        current = savedInstanceState?.getInt("tab", 0) ?: 0
        if (savedInstanceState == null) select(current) else chromeOnly(current)

        if (!Perms.coreReady(this) && !onboardedFlag()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    private fun buildBar() {
        tabs.forEachIndexed { i, name ->
            val cell = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                isClickable = true; isFocusable = true
                foreground = ripple()
                setOnClickListener { Ui.haptic(this); select(i) }
            }
            val tick = View(this).apply {
                setBackgroundColor(Ui.SAGE)
                layoutParams = FrameLayout.LayoutParams(Ui.dp(this@MainActivity, 22), Ui.dp(this@MainActivity, 3), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            }
            val label = TextView(this).apply {
                text = name.uppercase(); textSize = 11f; letterSpacing = 0.1f
                typeface = Ui.monoMed(this@MainActivity)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            }
            cell.addView(tick); cell.addView(label)
            ticks.add(tick); labels.add(label)
            b.bottomBar.addView(cell)
        }
    }

    private fun ripple(): android.graphics.drawable.Drawable? {
        val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
        val d = a.getDrawable(0); a.recycle(); return d
    }

    fun goTab(i: Int) { select(i) }

    private fun select(i: Int) {
        if (i == current && supportFragmentManager.findFragmentById(R.id.container) != null) return
        chromeOnly(i)
        show(if (i == 0) BlocksFragment() else HabitsFragment())
    }

    private fun chromeOnly(i: Int) {
        current = i
        ticks.forEachIndexed { idx, t -> t.visibility = if (idx == i) View.VISIBLE else View.INVISIBLE }
        labels.forEachIndexed { idx, l -> l.setTextColor(if (idx == i) Ui.ACC_TEXT else Ui.MUTED) }
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putInt("tab", current) }

    override fun onResume() {
        super.onResume()
        if (Perms.coreReady(this) && Store.anyEnabled()) MonitorService.start(this)
        if (!firstResume) (supportFragmentManager.findFragmentById(R.id.container) as? Refreshable)?.refresh()
        firstResume = false
    }

    private fun show(f: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
    }

    private fun onboardedFlag(): Boolean =
        getSharedPreferences("margin_flags", MODE_PRIVATE).getBoolean("onboarded", false)
}

interface Refreshable { fun refresh() }
