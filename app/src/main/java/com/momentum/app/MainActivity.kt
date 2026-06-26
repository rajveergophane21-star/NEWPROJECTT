package com.momentum.app

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.momentum.app.databinding.ActivityMainBinding
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var selectedColor = Store.palette[0]

    // stat tile value views
    private lateinit var tvDone: TextView
    private lateinit var tvHabits: TextView
    private lateinit var tvBest: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvMin: TextView
    private lateinit var tvSess: TextView
    private lateinit var tvUrges: TextView
    private lateinit var tvLongest: TextView

    // detox session state
    private var sessionTimer: CountDownTimer? = null
    private var sessionTotalMs = 0L
    private var selectedMinutes = 25
    private val durationButtons = mutableListOf<TextView>()

    // urge surfing
    private val handler = Handler(Looper.getMainLooper())
    private var breathRunnable: Runnable? = null
    private var urgeAnimator: ValueAnimator? = null
    private var urgeStartMs = 0L

    private val focusTips = listOf(
        "Notice the pull to check your phone — then let it pass.",
        "Boredom is the doorway to focus. Stay a moment longer.",
        "One task. This breath. Right now.",
        "The urge to scroll fades on its own if you don't feed it.",
        "You're rebuilding your attention, one minute at a time."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        buildStatTiles()
        buildSwatches()
        buildDurations()
        setupTabs()

        b.btnAdd.setOnClickListener { addHabit() }
        b.btnScience.setOnClickListener { showScience() }
        b.btnStart.setOnClickListener { startFocus(selectedMinutes) }
        b.btnEnd.setOnClickListener { endFocus(completed = false) }
        b.btnUrge.setOnClickListener { openUrge() }
        b.btnUrgeDone.setOnClickListener { closeUrge(surfed = true) }

        refreshAll()
    }

    // ---------------------------------------------------------------- tabs
    private fun setupTabs() {
        b.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                val habits = tab.position == 0
                b.scrollHabits.visibility = if (habits) View.VISIBLE else View.GONE
                b.scrollDetox.visibility = if (habits) View.GONE else View.VISIBLE
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    // ------------------------------------------------------------ stat tiles
    private fun buildStatTiles() {
        val (rowA, refsA) = statRow(listOf("done today", "habits"))
        val (rowB, refsB) = statRow(listOf("best streak", "check-ins"))
        b.habitStats.addView(rowA); b.habitStats.addView(rowB)
        tvDone = refsA[0]; tvHabits = refsA[1]; tvBest = refsB[0]; tvTotal = refsB[1]

        val (rowC, refsC) = statRow(listOf("focus minutes", "sessions today"))
        val (rowD, refsD) = statRow(listOf("urges surfed", "longest (min)"))
        b.detoxStats.addView(rowC); b.detoxStats.addView(rowD)
        tvMin = refsC[0]; tvSess = refsC[1]; tvUrges = refsD[0]; tvLongest = refsD[1]
    }

    /** Builds a horizontal row of stat tiles; returns the row and the value TextViews. */
    private fun statRow(labels: List<String>): Pair<LinearLayout, List<TextView>> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }
        val refs = mutableListOf<TextView>()
        labels.forEachIndexed { i, label ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.rounded_surface2)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                    if (i > 0) marginStart = dp(8)
                }
            }
            val num = TextView(this).apply {
                text = "0"
                setTextColor(0xFFE6EDF3.toInt())
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val lbl = TextView(this).apply {
                text = label
                setTextColor(0xFF8B98A5.toInt())
                textSize = 11f
            }
            tile.addView(num); tile.addView(lbl)
            row.addView(tile)
            refs.add(num)
        }
        return row to refs
    }

    // ------------------------------------------------------------- swatches
    private fun buildSwatches() {
        Store.palette.forEachIndexed { i, color ->
            val dot = View(this).apply {
                background = ContextCompat.getDrawable(context, R.drawable.circle)
                backgroundTintList = ColorStateList.valueOf(color)
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                    if (i > 0) marginStart = dp(10)
                }
                setOnClickListener { selectedColor = color; markSwatch(this) }
            }
            b.swatchRow.addView(dot)
        }
        if (b.swatchRow.childCount > 0) markSwatch(b.swatchRow.getChildAt(0))
    }

    private fun markSwatch(selected: View) {
        for (i in 0 until b.swatchRow.childCount) {
            val v = b.swatchRow.getChildAt(i)
            v.scaleX = if (v == selected) 1.25f else 1f
            v.scaleY = if (v == selected) 1.25f else 1f
            v.alpha = if (v == selected) 1f else 0.55f
        }
    }

    // ------------------------------------------------------------ durations
    private fun buildDurations() {
        val mins = listOf(15, 25, 45, 60)
        mins.forEachIndexed { i, m ->
            val chip = TextView(this).apply {
                text = "$m min"
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(0xFFE6EDF3.toInt())
                background = ContextCompat.getDrawable(context, R.drawable.pill)
                setPadding(0, dp(12), 0, dp(12))
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                    if (i > 0) marginStart = dp(8)
                }
                setOnClickListener { selectedMinutes = m; markDuration(this) }
            }
            durationButtons.add(chip)
            b.durationRow.addView(chip)
        }
        markDuration(durationButtons[1]) // default 25
    }

    private fun markDuration(selected: TextView) {
        durationButtons.forEach {
            val on = it == selected
            it.backgroundTintList = ColorStateList.valueOf(if (on) 0xFF4F8CFF.toInt() else 0xFF1F2630.toInt())
            it.setTextColor(if (on) 0xFFFFFFFF.toInt() else 0xFF8B98A5.toInt())
        }
    }

    // --------------------------------------------------------------- habits
    private fun addHabit() {
        val name = b.inputName.text.toString().trim()
        if (name.isEmpty()) { toast("Name your habit first"); return }
        Store.addHabit(name, b.inputAnchor.text.toString(), selectedColor)
        b.inputName.setText(""); b.inputAnchor.setText("")
        refreshAll()
        toast("Habit added — keep it tiny 🌱")
    }

    private fun renderHabits() {
        b.habitContainer.removeAllViews()
        b.emptyState.visibility = if (Store.habits.isEmpty()) View.VISIBLE else View.GONE

        for (h in Store.habits) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.rounded_surface)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    bottomMargin = dp(12)
                }
            }

            // header row
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val dot = View(this).apply {
                background = ContextCompat.getDrawable(context, R.drawable.circle)
                backgroundTintList = ColorStateList.valueOf(h.color)
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(10) }
            }
            val titleCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
            titleCol.addView(TextView(this).apply {
                text = h.name
                setTextColor(0xFFE6EDF3.toInt())
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
            if (h.anchor.isNotEmpty()) {
                titleCol.addView(TextView(this).apply {
                    text = "After I ${h.anchor}"
                    setTextColor(0xFF8B98A5.toInt())
                    textSize = 12f
                })
            }
            val done = Store.isDoneToday(h)
            val check = TextView(this).apply {
                text = if (done) "✓" else ""
                gravity = Gravity.CENTER
                textSize = 20f
                setTextColor(0xFFFFFFFF.toInt())
                background = ContextCompat.getDrawable(context, R.drawable.circle)
                backgroundTintList = ColorStateList.valueOf(if (done) h.color else 0xFF1F2630.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                setOnClickListener {
                    val wasDone = Store.isDoneToday(h)
                    Store.toggleToday(h)
                    if (!wasDone) celebrate(h)
                    refreshAll()
                }
            }
            header.addView(dot); header.addView(titleCol); header.addView(check)
            card.addView(header)

            // streak line
            val cur = Store.currentStreak(h)
            val best = Store.bestStreak(h)
            card.addView(TextView(this).apply {
                text = "🔥 $cur day streak  ·  best $best"
                setTextColor(0xFF8B98A5.toInt())
                textSize = 13f
                setPadding(0, dp(10), 0, dp(10))
            })

            // heatmap
            val heat = HeatmapView(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                setColor(h.color)
                setDays(Store.heatDays(h, 17 * 7), 17)
            }
            card.addView(heat)

            card.setOnLongClickListener { confirmDelete(h); true }
            b.habitContainer.addView(card)
        }
    }

    private fun confirmDelete(h: Habit) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"${h.name}\"?")
            .setMessage("This removes the habit and its history. This can't be undone.")
            .setPositiveButton("Delete") { _, _ -> Store.deleteHabit(h); refreshAll() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun celebrate(h: Habit) {
        val streak = Store.currentStreak(h)
        val msg = when {
            streak >= 30 -> "🏆 $streak days! This is who you are now."
            streak >= 7 -> "🔥 $streak-day streak — don't break the chain!"
            streak >= 3 -> "💪 $streak days in a row. Momentum building."
            else -> "✅ Done. Celebrate it — that feeling wires the habit."
        }
        toast(msg)
    }

    // ---------------------------------------------------------- detox focus
    private fun startFocus(minutes: Int) {
        sessionTotalMs = minutes * 60_000L
        lastProgress = 0f
        b.focusOverlay.visibility = View.VISIBLE
        b.ringFocus.setActiveColor(0xFF4F8CFF.toInt())
        b.focusTip.text = focusTips[0]
        b.focusState.text = "Phone down. You've got this."

        sessionTimer?.cancel()
        sessionTimer = object : CountDownTimer(sessionTotalMs, 1000) {
            override fun onTick(msLeft: Long) {
                val elapsed = sessionTotalMs - msLeft
                lastProgress = elapsed.toFloat() / sessionTotalMs
                b.ringFocus.setProgress(lastProgress)
                b.ringFocus.setCenterText(fmt(msLeft))
                b.ringFocus.setSubText("focus")
                val idx = ((elapsed / 20_000L) % focusTips.size).toInt()
                b.focusTip.text = focusTips[idx]
            }
            override fun onFinish() {
                b.ringFocus.setProgress(1f)
                endFocus(completed = true)
            }
        }.start()
    }

    private fun endFocus(completed: Boolean) {
        sessionTimer?.cancel(); sessionTimer = null
        // also dismiss urge overlay if open
        stopBreathing()
        b.urgeOverlay.visibility = View.GONE

        val minutes = (selectedMinutes)
        if (b.focusOverlay.visibility == View.VISIBLE) {
            if (completed) {
                Store.addSession(minutes, true)
                celebrateSession(minutes)
            } else {
                // count the minutes actually focused
                val elapsedMin = elapsedFocusMinutes()
                if (elapsedMin >= 1) Store.addSession(elapsedMin, false)
                toast("Session ended. ${if (elapsedMin >= 1) "$elapsedMin min counted." else ""}")
            }
        }
        b.focusOverlay.visibility = View.GONE
        refreshAll()
    }

    private fun elapsedFocusMinutes(): Int {
        // derive from ring progress
        val p = currentFocusProgress()
        return (sessionTotalMs * p / 60_000.0).toInt()
    }

    private var lastProgress = 0f
    private fun currentFocusProgress(): Float = lastProgress

    private fun celebrateSession(minutes: Int) {
        AlertDialog.Builder(this)
            .setTitle("Session complete 🎉")
            .setMessage("$minutes phone-free minutes. Every session retrains your attention and weakens the pull of the feed.")
            .setPositiveButton("Nice") { _, _ -> }
            .show()
    }

    // ---------------------------------------------------------- urge surfing
    private fun openUrge() {
        b.urgeOverlay.visibility = View.VISIBLE
        urgeStartMs = System.currentTimeMillis()
        startBreathing()
    }

    private fun closeUrge(surfed: Boolean) {
        stopBreathing()
        b.urgeOverlay.visibility = View.GONE
        if (surfed) {
            Store.addUrgeSurfed()
            toast("You rode the wave 🌊 The urge passed — and you didn't.")
            refreshAll()
        }
    }

    /** Box breathing: in 4s · hold 4s · out 4s · hold 4s, looping ~90s. */
    private fun startBreathing() {
        val phases = listOf(
            Triple("Breathe in", 4000L, 1.0f),
            Triple("Hold", 4000L, 1.0f),
            Triple("Breathe out", 4000L, 0.55f),
            Triple("Hold", 4000L, 0.55f)
        )
        var idx = 0
        breathRunnable = object : Runnable {
            override fun run() {
                val (label, dur, target) = phases[idx % phases.size]
                b.breathText.text = label
                val from = b.breathOrb.scaleX
                urgeAnimator?.cancel()
                urgeAnimator = ValueAnimator.ofFloat(from, target).apply {
                    duration = dur
                    addUpdateListener { a ->
                        val v = a.animatedValue as Float
                        b.breathOrb.scaleX = v; b.breathOrb.scaleY = v
                    }
                    start()
                }
                val elapsed = (System.currentTimeMillis() - urgeStartMs) / 1000
                val left = (90 - elapsed).coerceAtLeast(0)
                b.urgeCountdown.text = if (left > 0) "The wave usually passes in ~${left}s" else "Notice — it already eased."
                idx++
                handler.postDelayed(this, dur)
            }
        }
        b.breathOrb.scaleX = 0.55f; b.breathOrb.scaleY = 0.55f
        handler.post(breathRunnable!!)
    }

    private fun stopBreathing() {
        breathRunnable?.let { handler.removeCallbacks(it) }
        breathRunnable = null
        urgeAnimator?.cancel(); urgeAnimator = null
    }

    // --------------------------------------------------------------- science
    private fun showScience() {
        val msg = """
            Every feature here is built on established behavior-change research:

            • Make it tiny (Fogg, B=MAP). Behavior = Motivation × Ability × Prompt. Shrink a habit until it's almost effortless so it survives low-motivation days.

            • Anchor it to a cue (Gollwitzer's implementation intentions). "After I ⟨routine⟩, I will ⟨habit⟩" hands control to an automatic trigger and roughly 2–3× follow-through.

            • Don't break the chain (loss aversion). Streaks turn progress into something you won't want to lose.

            • Celebrate immediately. A jolt of positive emotion right after the act is what wires it in.

            • Add friction to compulsion. Phones run on variable rewards — unpredictable dopamine hits. A phone-free focus session removes the slot machine.

            • Surf the urge (mindfulness/CBT). Cravings are waves. Breathe through one for ~90s instead of acting, and it weakens over time.

            Momentum is a self-help tool, not medical care.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("The science behind Momentum")
            .setMessage(msg)
            .setPositiveButton("Got it", null)
            .show()
    }

    // --------------------------------------------------------------- refresh
    private fun refreshAll() {
        val total = Store.habits.size
        val done = Store.doneTodayCount()
        val pct = if (total == 0) 0f else done.toFloat() / total
        b.ringToday.setProgress(pct)
        b.ringToday.setCenterText("${(pct * 100).toInt()}%")
        b.ringToday.setSubText("today")

        tvDone.text = done.toString()
        tvHabits.text = total.toString()
        tvBest.text = Store.bestStreakAll().toString()
        tvTotal.text = Store.totalCheckins().toString()

        tvMin.text = Store.totalFocusMinutes().toString()
        tvSess.text = Store.sessionsToday().toString()
        tvUrges.text = Store.urgesSurfed.toString()
        tvLongest.text = Store.longestSession().toString()

        renderHabits()
        renderSessions()
    }

    private fun renderSessions() {
        b.sessionContainer.removeAllViews()
        if (Store.sessions.isEmpty()) {
            b.sessionContainer.addView(TextView(this).apply {
                text = "No sessions yet. Your first detox is one tap away."
                setTextColor(0xFF8B98A5.toInt())
                textSize = 13f
                setPadding(0, dp(6), 0, dp(6))
            })
            return
        }
        Store.sessions.takeLast(8).reversed().forEach { s ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.rounded_surface2)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            }
            row.addView(TextView(this).apply {
                text = if (s.completed) "✅" else "•"
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(12) }
            })
            row.addView(TextView(this).apply {
                text = "${s.minutes} min focus"
                setTextColor(0xFFE6EDF3.toInt())
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = relativeDay(s.day)
                setTextColor(0xFF8B98A5.toInt())
                textSize = 12f
            })
            b.sessionContainer.addView(row)
        }
    }

    private fun relativeDay(epochDay: Long): String {
        val diff = LocalDate.now().toEpochDay() - epochDay
        return when (diff) {
            0L -> "today"
            1L -> "yesterday"
            else -> "$diff days ago"
        }
    }

    // ----------------------------------------------------------------- utils
    private fun fmt(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        sessionTimer?.cancel()
        stopBreathing()
    }
}
