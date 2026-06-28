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
    private val tabs = listOf("Today", "Shield", "Habits", "Review", "Insights")
    private val ticks = mutableListOf<View>()
    private val labels = mutableListOf<TextView>()
    private val dueDots = mutableListOf<View>()
    private var current = 0
    private var firstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        buildBar()
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
                foreground = ContextCompatRipple()
                setOnClickListener { Ui.haptic(this); select(i) }
            }
            val tick = View(this).apply {
                setBackgroundColor(Ui.SAGE)
                layoutParams = FrameLayout.LayoutParams(Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 2), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            }
            val label = TextView(this).apply {
                text = name.uppercase(); textSize = 10f; letterSpacing = 0.08f
                typeface = Ui.monoMed(this@MainActivity)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            }
            val dot = View(this).apply {
                background = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.circle)
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(Ui.dp(this@MainActivity, 5), Ui.dp(this@MainActivity, 5), Gravity.CENTER).also {
                    it.leftMargin = Ui.dp(this@MainActivity, 44); it.topMargin = Ui.dp(this@MainActivity, 16)
                }
            }
            cell.addView(tick); cell.addView(label); cell.addView(dot)
            ticks.add(tick); labels.add(label); dueDots.add(dot)
            b.bottomBar.addView(cell)
        }
    }

    /** A subtle bounded ripple for the tab cells. */
    private fun ContextCompatRipple(): android.graphics.drawable.Drawable? {
        val a = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
        val d = a.getDrawable(0); a.recycle(); return d
    }

    private fun select(i: Int) {
        chromeOnly(i)
        show(when (i) {
            0 -> TodayFragment(); 1 -> ShieldFragment(); 2 -> HabitsFragment()
            3 -> ReviewFragment(); else -> InsightsFragment()
        })
    }

    /** Update the bar indicator only — used when FragmentManager already restored the fragment. */
    private fun chromeOnly(i: Int) {
        current = i
        ticks.forEachIndexed { idx, t -> t.visibility = if (idx == i) View.VISIBLE else View.INVISIBLE }
        labels.forEachIndexed { idx, l -> l.setTextColor(if (idx == i) Ui.TEXT else Ui.MUTED) }
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putInt("tab", current) }

    override fun onResume() {
        super.onResume()
        if (Perms.coreReady(this) && (Store.rules.any { it.enabled } || Store.focusActive())) MonitorService.start(this)
        if (dueDots.size >= 4) dueDots[3].visibility = if (Store.reviewDue()) View.VISIBLE else View.GONE
        // Skip the post-create double-render; only refresh when genuinely returning to a
        // live screen, so typed-but-unsaved input (Review/Habits) isn't blown away.
        if (!firstResume) {
            (supportFragmentManager.findFragmentById(R.id.container) as? Refreshable)?.refresh()
        }
        firstResume = false
    }

    private fun show(f: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
    }

    private fun onboardedFlag(): Boolean =
        getSharedPreferences("anchor_flags", MODE_PRIVATE).getBoolean("onboarded", false)
}

interface Refreshable { fun refresh() }
