package com.anchor.app

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/* ============================== shared helpers ============================== */

private fun appLabel(c: Context, pkg: String): String = try {
    val pm = c.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (_: Exception) { pkg.substringAfterLast('.') }

private fun hourBand(h: Int): String = when (h) {
    in 5..10 -> "the morning"
    in 11..13 -> "midday"
    in 14..17 -> "the afternoon"
    in 18..20 -> "the evening"
    else -> "late at night"
}

/** A small editable single-line field on a recessed surface. */
private fun field(c: Context, hintText: String, prefill: String = "", lines: Int = 1): EditText = EditText(c).apply {
    hint = hintText; setText(prefill)
    setHintTextColor(Ui.FAINT); setTextColor(Ui.TEXT); textSize = 15f
    background = ContextCompat.getDrawable(c, R.drawable.input)
    setPadding(Ui.dp(c,14), Ui.dp(c,12), Ui.dp(c,14), Ui.dp(c,12))
    if (lines > 1) { isSingleLine = false; minLines = lines; gravity = Gravity.TOP }
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .also { it.topMargin = Ui.dp(c,8) }
}

/** A selectable pill, used for reflection ratings. */
private fun choicePill(c: Context, label: String, selected: Boolean, onTap: () -> Unit): TextView = TextView(c).apply {
    text = label; gravity = Gravity.CENTER; textSize = 13f
    setTextColor(if (selected) Ui.SAGE else Ui.MUTED)
    background = ContextCompat.getDrawable(c, R.drawable.pill)
    backgroundTintList = ColorStateList.valueOf(if (selected) Ui.SELECT else Ui.SURFACE2)
    setPadding(Ui.dp(c,14), Ui.dp(c,10), Ui.dp(c,14), Ui.dp(c,10))
    setOnClickListener { Ui.haptic(this); onTap() }
}

/* ============================== TODAY ============================== */

class TodayFragment : BaseFragment() {
    private val handler = Handler(Looper.getMainLooper())
    private var focusLabel: TextView? = null
    private var focusFill: View? = null
    private var focusEmpty: View? = null
    private val tick = object : Runnable {
        override fun run() {
            if (focusLabel == null) return
            if (!Store.focusActive()) { refresh(); return }
            val remain = Store.focusRemainingMs(); val total = Store.focusTotalMs.coerceAtLeast(1)
            focusLabel?.text = mmss(remain)
            val pct = (total - remain).toFloat() / total
            focusFill?.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct.coerceIn(0.0001f, 1f))
            focusEmpty?.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (1f - pct).coerceIn(0.0001f, 1f))
            focusFill?.requestLayout()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }
    override fun onResume() { super.onResume(); if (Store.focusActive() && focusLabel != null) { handler.removeCallbacks(tick); handler.post(tick) } }

    private fun mmss(ms: Long): String { val s = (ms / 1000).toInt(); return "%d:%02d".format(s / 60, s % 60) }

    override fun render() {
        val c = requireContext()
        focusLabel = null; handler.removeCallbacks(tick)
        val hour = LocalTime.now().hour
        val part = when { hour < 12 -> "Morning"; hour < 18 -> "Afternoon"; else -> "Evening" }

        // masthead row
        val dateRow = Ui.row(c).also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,12)) }
        dateRow.addView(Ui.eyebrow(c, LocalDate.now().format(DateTimeFormatter.ofPattern("EEE · d MMM"))))
        dateRow.addView(View(c).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        dateRow.addView(Ui.eyebrow(c, part))
        col.addView(dateRow)
        col.addView(thinLine(c))

        col.addView(Ui.display(c, "Good ${part.lowercase()}.", 40f).also { it.setPadding(0, Ui.dp(c,18),0,0) })

        // identity — your own words
        col.addView(Ui.eyebrow(c, "You're someone who").also { it.setPadding(0, Ui.dp(c,24),0,0) })
        val idText = if (Store.identity.isEmpty()) "…name who you're becoming." else Store.identity
        col.addView(Ui.serifQuote(c, idText, Ui.TEXT, 25f).also {
            it.setPadding(0, Ui.dp(c,10),0,0); it.setOnClickListener { identityDialog(c) }
        })

        statusRow(c, hour)
        focusSection(c)
        habitsSection(c)
        if (hour >= 18 || Store.dayNoteFor(Store.today()) != null) reflectionSection(c)
    }

    private fun thinLine(c: Context) = View(c).apply {
        setBackgroundColor(Ui.LINE)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c,1).coerceAtLeast(1))
    }

    private fun statusRow(c: Context, hour: Int) {
        col.addView(View(c).apply { setBackgroundColor(Ui.LINE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c,1).coerceAtLeast(1)).also { it.topMargin = Ui.dp(c,24) } })
        val row = Ui.row(c).also { it.setPadding(0, Ui.dp(c,16),0, Ui.dp(c,16)) }
        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val dow = LocalDate.now().dayOfWeek.value
        val active = Store.rules.count { it.activeNow(nowMin, dow) }
        val ready = Perms.coreReady(c)
        val armed = ready && (active > 0 || Store.focusActive() || Store.rules.any { it.enabled })
        val (t, s) = when {
            !ready -> "Shield is off" to "Tap to finish setup"
            Store.focusActive() -> "In a focus session" to "${mmss(Store.focusRemainingMs())} remaining · distractions held back"
            active > 0 -> "Shield is up" to "$active ${if (active==1) "rule" else "rules"} holding right now"
            Store.rules.any { it.enabled } -> "Armed and watching" to "${Store.resistedToday()} urges turned away today"
            else -> "Nothing's blocked right now" to "Set a boundary in Shield"
        }
        val dot = View(c).apply {
            background = ContextCompat.getDrawable(c, R.drawable.circle)
            backgroundTintList = ColorStateList.valueOf(if (armed) Ui.SAGE else 0xFFC9BFAD.toInt())
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c,8), Ui.dp(c,8)).also { it.marginEnd = Ui.dp(c,14) }
        }
        val tcol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        tcol.addView(Ui.title(c, t, 15f))
        tcol.addView(Ui.body(c, s, Ui.MUTED, 12.5f).also { it.setPadding(0, Ui.dp(c,2),0,0) })
        row.addView(dot); row.addView(tcol)
        if (!ready) row.setOnClickListener { startActivity(Intent(c, OnboardingActivity::class.java)) }
        col.addView(row)
        col.addView(thinLine(c))
    }

    private fun focusSection(c: Context) {
        val head = Ui.row(c).also { it.setPadding(0, Ui.dp(c,26),0,0) }
        head.addView(Ui.eyebrow(c, "Focus now"))
        head.addView(View(c).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        if (Store.focusActive()) {
            val stop = Ui.eyebrow(c, "Stop"); stop.setOnClickListener { Ui.haptic(it); Store.stopFocus(); refresh() }
            head.addView(stop)
        }
        col.addView(head)

        if (Store.focusActive()) {
            val numRow = Ui.row(c).also { it.setPadding(0, Ui.dp(c,10),0,0); it.gravity = Gravity.BOTTOM }
            val label = Ui.numeral(c, mmss(Store.focusRemainingMs()), 56f)
            focusLabel = label
            numRow.addView(label)
            numRow.addView(Ui.eyebrow(c, "remaining").also { it.setPadding(Ui.dp(c,12),0,0, Ui.dp(c,10)) })
            col.addView(numRow)
            val bar = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c,3)).also { it.topMargin = Ui.dp(c,14) }
            }
            val pct = ((Store.focusTotalMs - Store.focusRemainingMs()).toFloat() / Store.focusTotalMs.coerceAtLeast(1)).coerceIn(0.0001f, 1f)
            val fill = View(c).apply { setBackgroundColor(Ui.SAGE); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct) }
            val empty = View(c).apply { setBackgroundColor(Ui.LINE); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - pct) }
            focusFill = fill; focusEmpty = empty
            bar.addView(fill); bar.addView(empty); col.addView(bar)
            handler.removeCallbacks(tick); handler.post(tick)
        } else {
            col.addView(Ui.body(c, "Hold your distractions back for a stretch.").also { it.setPadding(0, Ui.dp(c,9),0, Ui.dp(c,14)) })
            val rowB = Ui.row(c)
            listOf(25, 45, 60).forEachIndexed { i, m ->
                val btn = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    background = ContextCompat.getDrawable(c, R.drawable.card)
                    setPadding(0, Ui.dp(c,16),0, Ui.dp(c,14))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { if (i>0) it.marginStart = Ui.dp(c,10) }
                    isClickable = true
                    setOnClickListener { Ui.haptic(this); startFocus(m) }
                }
                btn.addView(Ui.numeral(c, "$m", 27f))
                btn.addView(Ui.eyebrow(c, "min").also { it.setPadding(0, Ui.dp(c,3),0,0) })
                rowB.addView(btn)
            }
            col.addView(rowB)
        }
    }

    private fun habitsSection(c: Context) {
        if (Store.habits.isEmpty()) return
        col.addView(Ui.serifHead(c, "Today's habits", 23f).also { it.setPadding(0, Ui.dp(c,30),0, Ui.dp(c,2)) })
        Store.habits.forEach { h ->
            col.addView(thinLine(c).also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dp(c,2) })
            col.addView(habitRow(c, h))
        }
    }

    private fun habitRow(c: Context, h: Habit): View {
        val row = Ui.row(c).also { it.setPadding(0, Ui.dp(c,14),0, Ui.dp(c,14)) }
        val done = Store.isDoneToday(h)
        val check = TextView(c).apply {
            text = if (done) "✓" else ""; gravity = Gravity.CENTER; textSize = 13f; setTextColor(Ui.INK)
            background = ContextCompat.getDrawable(c, if (done) R.drawable.circle else R.drawable.ring)
            if (done) backgroundTintList = ColorStateList.valueOf(Ui.SAGE)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c,22), Ui.dp(c,22)).also { it.marginEnd = Ui.dp(c,14) }
            setOnClickListener { Ui.haptic(this); Store.toggleToday(h); refresh() }
        }
        val tcol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        tcol.addView(Ui.title(c, h.name, 15f))
        tcol.addView(Ui.mono(c, if (h.anchor.isNotEmpty()) "after ${h.anchor}" else "replacement habit", Ui.FAINT, 10f).also { it.setPadding(0, Ui.dp(c,3),0,0) })
        val streak = Ui.numeral(c, "${Store.currentStreak(h)}", 21f)
        val d = Ui.mono(c, "d", Ui.FAINT, 9.5f)
        val sRow = Ui.row(c); sRow.addView(streak); sRow.addView(d)
        row.addView(check); row.addView(tcol); row.addView(sRow)
        return row
    }

    private fun reflectionSection(c: Context) {
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(c, R.drawable.card_dark)
            setPadding(Ui.dp(c,22), Ui.dp(c,22), Ui.dp(c,22), Ui.dp(c,22))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = Ui.dp(c,28) }
        }
        val existing = Store.dayNoteFor(Store.today())
        card.addView(Ui.eyebrow(c, "Tonight · 10 seconds").also { it.setTextColor(Ui.ACC_GLOW) })
        card.addView(Ui.serifQuote(c, "How close was today to who you're becoming?", Ui.DARK_TEXT, 21f).also { it.setPadding(0, Ui.dp(c,11),0, Ui.dp(c,16)) })

        var sel = existing?.alignment ?: -1
        val labels = listOf("Not yet", "Closer", "There")
        val pills = mutableListOf<TextView>()
        val pillRow = Ui.row(c)
        fun restyle() {
            pills.forEachIndexed { i, p ->
                val on = i == sel
                p.setTextColor(if (on) Ui.DARK_TEXT else 0xFF8E8675.toInt())
                p.backgroundTintList = ColorStateList.valueOf(if (on) 0xFF35312A.toInt() else 0xFF2B2820.toInt())
            }
        }
        labels.forEachIndexed { i, l ->
            val p = TextView(c).apply {
                text = l; gravity = Gravity.CENTER; textSize = 13f; typeface = Ui.sans(c)
                background = ContextCompat.getDrawable(c, R.drawable.pill)
                setPadding(0, Ui.dp(c,12),0, Ui.dp(c,12))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { if (i>0) it.marginStart = Ui.dp(c,9) }
                setOnClickListener {
                    Ui.haptic(this); sel = i; restyle()
                    Store.setDayNote(Store.today(), sel, existing?.note ?: "")
                }
            }
            pills.add(p); pillRow.addView(p)
        }
        card.addView(pillRow)
        restyle()
        col.addView(card)
    }

    private fun identityDialog(c: Context) {
        val input = field(c, "I'm someone who…", Store.identity)
        AlertDialog.Builder(c)
            .setTitle("Who are you becoming?")
            .setView(LinearLayout(c).apply { setPadding(Ui.dp(c,20),Ui.dp(c,8),Ui.dp(c,20),0); addView(input) })
            .setPositiveButton("This is who I'm becoming") { _, _ -> Store.updateIdentity(input.text.toString()); refresh() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun startFocus(min: Int) {
        val c = requireContext()
        val union = Store.rules.flatMap { it.packages }.toSet()
        if (union.isEmpty()) { Toast.makeText(c, "Add a rule with some apps first", Toast.LENGTH_SHORT).show(); return }
        if (!Perms.coreReady(c)) { startActivity(Intent(c, OnboardingActivity::class.java)); return }
        Store.startFocus(union, min); MonitorService.start(c); refresh()
    }
}

/* ============================== SHIELD ============================== */

class ShieldFragment : BaseFragment() {
    override fun render() {
        val c = requireContext()
        col.addView(Ui.eyebrow(c, "Shield"))
        col.addView(Ui.display(c, "Your boundaries.").also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,18)) })

        if (!Perms.coreReady(c)) {
            val card = Ui.card(c)
            card.addView(Ui.title(c, "Setup unfinished", 16f))
            card.addView(Ui.body(c, "Rules won't enforce until permissions are granted.").also { it.setPadding(0, Ui.dp(c,6),0, Ui.dp(c,12)) })
            val b = Ui.ghost(c, "Finish setup"); b.setOnClickListener { startActivity(Intent(c, OnboardingActivity::class.java)) }
            card.addView(b); col.addView(card)
        }

        val add = Ui.primary(c, "New rule")
        add.setOnClickListener { Ui.haptic(it); startActivity(Intent(c, RuleEditorActivity::class.java)) }
        col.addView(add); col.addView(Ui.spacer(c,18))

        if (Store.rules.isEmpty()) {
            col.addView(Ui.emptyState(c, "Shield", "No boundaries yet.",
                "A rule is a set of apps, a schedule, and what happens when you reach for them. Start with the one that costs you the most time."))
            return
        }

        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val dow = LocalDate.now().dayOfWeek.value
        Store.rules.forEach { r -> col.addView(ruleCard(c, r, nowMin, dow)) }
    }

    private fun ruleCard(c: Context, r: Rule, nowMin: Int, dow: Int): View {
        val card = Ui.card(c)
        val header = Ui.row(c)
        val tcol = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleRow = Ui.row(c)
        titleRow.addView(Ui.title(c, r.name, 17f))
        if (Store.isLocked(r)) titleRow.addView(TextView(c).apply { text = "  🔒"; textSize = 13f })
        tcol.addView(titleRow)
        val isBlock = r.mode == Mode.BLOCK
        val modeChip = TextView(c).apply {
            text = (if (isBlock) "Block · hard stop" else "Friction · 12s pause").uppercase()
            textSize = 9f; letterSpacing = 0.08f; typeface = Ui.monoMed(c)
            setTextColor(if (isBlock) Ui.SAGE else Ui.TERRA)
            setPadding(Ui.dp(c,11), Ui.dp(c,6), Ui.dp(c,11), Ui.dp(c,6))
            background = ContextCompat.getDrawable(c, R.drawable.pill)
            backgroundTintList = ColorStateList.valueOf(if (isBlock) 0xFFE8EDE2.toInt() else 0xFFF2E7D4.toInt())
        }
        tcol.addView(LinearLayout(c).apply { setPadding(0, Ui.dp(c,6),0,0); addView(modeChip) })

        val sw = MaterialSwitch(c).apply {
            isChecked = r.enabled; isEnabled = !Store.isLocked(r)
            setOnCheckedChangeListener { _, v ->
                r.enabled = v; Store.save()
                if (Perms.coreReady(c)) { if (Store.rules.any { it.enabled }) MonitorService.start(c) else MonitorService.stop(c) }
            }
        }
        header.addView(tcol); header.addView(sw)
        card.addView(header)

        val apps = if (r.packages.size == 1) "1 app" else "${r.packages.size} apps"
        card.addView(Ui.body(c, apps, Ui.MUTED, 13f).also { it.setPadding(0, Ui.dp(c,10),0, Ui.dp(c,2)) })
        r.windows.forEach { w -> card.addView(Ui.body(c, w.label(), Ui.MUTED, 13f)) }
        if (r.activeNow(nowMin, dow)) card.addView(Ui.body(c, "● Active now — ${r.minutesLeft(nowMin, dow)} min left", Ui.SAGE, 12f).also { it.setPadding(0, Ui.dp(c,8),0,0) })

        card.setOnClickListener {
            startActivity(Intent(c, RuleEditorActivity::class.java).putExtra(RuleEditorActivity.EXTRA_RULE_ID, r.id))
        }
        return card
    }
}

/* ============================== HABITS ============================== */

class HabitsFragment : BaseFragment() {
    override fun render() {
        val c = requireContext()
        col.addView(Ui.eyebrow(c, "Replacement behaviours"))
        col.addView(Ui.display(c, "What you do instead.").also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,8)) })
        col.addView(Ui.body(c, "Removing a habit leaves a gap. Fill it deliberately — that's what makes the change hold.")
            .also { it.setPadding(0,0,0, Ui.dp(c,18)) })

        if (Store.habits.isNotEmpty()) {
            val total = Store.habits.size; val done = Store.doneTodayCount()
            val overview = Ui.card(c)
            val orow = Ui.row(c)
            val ring = RingView(c).apply {
                setProgress(if (total == 0) 0f else done.toFloat() / total)
                setCenterText(if (total == 0) "—" else "${(done * 100 / total)}%"); setSubText("today")
                layoutParams = LinearLayout.LayoutParams(Ui.dp(c,92), Ui.dp(c,92))
            }
            val stats = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = Ui.dp(c,16) }
            }
            stats.addView(Ui.body(c, "$done of $total done today", Ui.TEXT, 15f))
            stats.addView(Ui.body(c, "Best streak · ${Store.habits.maxOfOrNull { Store.bestStreak(it) } ?: 0} days", Ui.MUTED, 13f))
            orow.addView(ring); orow.addView(stats); overview.addView(orow)
            col.addView(overview)
        }

        val addCard = Ui.card(c)
        addCard.addView(Ui.eyebrow(c, "Add one"))
        val name = field(c, "A tiny habit — e.g. 10 push-ups")
        val anchor = field(c, "After I… (an existing routine to attach it to)")
        val addBtn = Ui.primary(c, "Add habit").also { it.setPadding(0, Ui.dp(c,12),0,0) }
        addBtn.setOnClickListener {
            val n = name.text.toString().trim()
            if (n.isEmpty()) { Toast.makeText(c, "Name it first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            Ui.haptic(it); Store.addHabit(n, anchor.text.toString()); refresh()
        }
        addCard.addView(name); addCard.addView(anchor); addCard.addView(Ui.spacer(c,4)); addCard.addView(addBtn)
        col.addView(addCard)

        if (Store.habits.isEmpty()) {
            col.addView(Ui.emptyState(c, "Habits", "Nothing to grow yet.",
                "Add one tiny habit above — small enough that you can't talk yourself out of it."))
        }
        Store.habits.forEach { h -> col.addView(habitCard(c, h)) }
    }

    private fun habitCard(c: Context, h: Habit): View {
        val card = Ui.card(c)
        val header = Ui.row(c)
        val tcol = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tcol.addView(Ui.title(c, h.name, 16f))
        if (h.anchor.isNotEmpty()) tcol.addView(Ui.body(c, "After I ${h.anchor}", Ui.MUTED, 12f))
        val done = Store.isDoneToday(h)
        val check = TextView(c).apply {
            text = if (done) "✓" else ""; gravity = Gravity.CENTER; textSize = 20f; setTextColor(Ui.INK)
            background = ContextCompat.getDrawable(c, R.drawable.circle)
            backgroundTintList = ColorStateList.valueOf(if (done) Ui.SAGE else Ui.SURFACE2)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c,44), Ui.dp(c,44))
            setOnClickListener { Ui.haptic(this); Store.toggleToday(h); refresh() }
        }
        header.addView(tcol); header.addView(check)
        card.addView(header)
        card.addView(Ui.body(c, "${Store.currentStreak(h)}-day streak · best ${Store.bestStreak(h)}", Ui.FAINT, 12f)
            .also { it.setPadding(0, Ui.dp(c,12),0, Ui.dp(c,12)) })
        card.addView(HeatmapView(c).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setDays(Store.heatDays(h, 16 * 7), 16)
        })
        card.setOnLongClickListener {
            AlertDialog.Builder(c).setTitle("Delete \"${h.name}\"?")
                .setPositiveButton("Delete") { _, _ -> Store.deleteHabit(h); refresh() }
                .setNegativeButton("Cancel", null).show()
            true
        }
        return card
    }
}

/* ============================== REVIEW ============================== */

class ReviewFragment : BaseFragment() {
    override fun render() {
        val c = requireContext()
        col.addView(Ui.eyebrow(c, "Review"))
        col.addView(Ui.display(c, "Look back.").also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,18)) })

        val ws = Store.weekStartOf(Store.today())
        val existing = Store.reviewFor(ws)

        becomingCard(c)

        if (existing != null) { completedCard(c, existing); return }
        if (!Store.reviewDue()) { holdingCard(c); return }
        reviewForm(c, ws)
    }

    private fun becomingCard(c: Context) {
        if (Store.identity.isEmpty()) return
        val card = Ui.card(c)
        card.addView(Ui.eyebrow(c, "Becoming"))
        card.addView(Ui.title(c, Store.identity, 18f).also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,8)) })
        card.addView(Ui.body(c, "${Store.alignedDaysTotal()} days that felt like you · ${Store.reviewsCount()} weeks looked back on · ${Store.resistedTotal()} urges turned away in all", Ui.FAINT, 12f))
        col.addView(card)
    }

    private fun holdingCard(c: Context) {
        val card = Ui.card(c)
        val dayName = java.time.DayOfWeek.of(Store.reviewDow).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        card.addView(Ui.title(c, "Your week is still being written.", 18f))
        card.addView(Ui.body(c, "Margin gathers the week as you live it. Come back $dayName to look back and choose what's next.")
            .also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,12)) })
        val (resisted, _, aligned, top, _) = weekMirror(c)
        card.addView(Ui.body(c, "So far: $resisted urges turned away · $aligned ${if (aligned==1) "day" else "days"} that felt like you${if (top != null) " · $top pulled at you most" else ""}", Ui.FAINT, 12f))
        col.addView(card)
    }

    private fun completedCard(c: Context, r: WeeklyReview) {
        val card = Ui.card(c)
        card.addView(Ui.eyebrow(c, "This week"))
        card.addView(Ui.title(c, "Reviewed. Onward.", 18f).also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,8)) })
        if (r.focus.isNotEmpty()) card.addView(Ui.body(c, "Your focus: “${r.focus}”", Ui.TEXT, 15f))
        if (r.noticed.isNotEmpty()) card.addView(Ui.body(c, "You noticed: ${r.noticed}", Ui.MUTED, 13f).also { it.setPadding(0, Ui.dp(c,8),0,0) })
        col.addView(card)
    }

    private fun reviewForm(c: Context, ws: Long) {
        val (resisted, proceeded, aligned, top, peak) = weekMirror(c)

        val mirror = Ui.card(c)
        mirror.addView(Ui.sectionHeader(c, "The mirror"))
        mirror.addView(Ui.spacer(c,10))
        mirror.addView(Ui.body(c, "This week you turned away $resisted urges, and opened anyway $proceeded times.", Ui.TEXT, 15f))
        mirror.addView(Ui.body(c, "$aligned of 7 days felt like the person you're becoming.", Ui.MUTED, 14f).also { it.setPadding(0, Ui.dp(c,8),0,0) })
        if (top != null) mirror.addView(Ui.body(c, "$top pulled at you the most${if (peak != null) ", usually around ${hourBand(peak)}" else ""}.", Ui.MUTED, 14f).also { it.setPadding(0, Ui.dp(c,6),0,0) })
        col.addView(mirror)

        // last week's focus, if any
        val last = Store.lastReview()
        var lastOutcome = -1
        if (last != null && last.focus.isNotEmpty()) {
            val card = Ui.card(c)
            card.addView(Ui.eyebrow(c, "Last week you chose"))
            card.addView(Ui.title(c, "“${last.focus}”", 16f).also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,12)) })
            val row = Ui.row(c); val pills = mutableListOf<TextView>()
            listOf("Drifted","Some of it","Lived it").forEachIndexed { i, l ->
                val p = choicePill(c, l, false) {
                    lastOutcome = i
                    pills.forEachIndexed { j, pp -> val on = j==i; pp.setTextColor(if(on) Ui.SAGE else Ui.MUTED); pp.backgroundTintList = ColorStateList.valueOf(if(on) Ui.SELECT else Ui.SURFACE2) }
                }
                p.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { if (i>0) it.marginStart = Ui.dp(c,8) }
                pills.add(p); row.addView(p)
            }
            card.addView(row); col.addView(card)
        }

        val reflect = Ui.card(c)
        reflect.addView(Ui.eyebrow(c, "Reflect"))
        reflect.addView(Ui.title(c, "What's one thing you noticed about yourself this week?", 16f).also { it.setPadding(0, Ui.dp(c,8),0,0) })
        val noticed = field(c, "No right answer. Just what's true.", lines = 3)
        reflect.addView(noticed); col.addView(reflect)

        val focusCard = Ui.card(c)
        focusCard.addView(Ui.eyebrow(c, "Next week"))
        focusCard.addView(Ui.title(c, "Pick one thing to lean into.", 16f).also { it.setPadding(0, Ui.dp(c,8),0,0) })
        val focus = field(c, "Small and specific — e.g. 'phone stays out of the bedroom'.")
        focusCard.addView(focus); col.addView(focusCard)

        val save = Ui.primary(c, "Begin the week")
        save.setOnClickListener {
            Ui.haptic(it)
            Store.saveReview(ws, noticed.text.toString(), focus.text.toString(), lastOutcome)
            Toast.makeText(c, "Week reviewed. Begin again.", Toast.LENGTH_SHORT).show()
            refresh()
        }
        col.addView(save)
    }

    /** (resisted, proceeded, alignedDays, topAppLabel?, peakHour?) for the current week so far. */
    private fun weekMirror(c: Context): Quint {
        val ws = Store.weekStartOf(Store.today())
        val inWeek = Store.interceptions.filter { it.day in ws..Store.today() }
        val resisted = inWeek.count { !it.proceeded }
        val proceeded = inWeek.count { it.proceeded }
        val aligned = (ws..Store.today()).count { Store.dayNoteFor(it)?.let { n -> n.alignment >= 1 } == true }
        val top = Store.topInterceptedPackage(7)?.let { appLabel(c, it) }
        val peak = Store.peakInterceptionHour(7)
        return Quint(resisted, proceeded, aligned, top, peak)
    }
}

private data class Quint(val a: Int, val b: Int, val c: Int, val d: String?, val e: Int?)

/* ============================== INSIGHTS ============================== */

class InsightsFragment : BaseFragment() {
    override fun render() {
        val c = requireContext()
        col.addView(Ui.eyebrow(c, "Insights"))
        col.addView(Ui.display(c, "What the log is telling you.").also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,18)) })

        actionableCards(c)

        // two stats, not four
        val row = Ui.row(c)
        val (t1, _) = Ui.statTile(c, "${Store.resistedTotal()}", "turned away in all")
        val (t2, _) = Ui.statTile(c, "${Store.resistedMomentum()}", "day momentum")
        t1.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = Ui.dp(c,10) }
        t2.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(t1); row.addView(t2)
        col.addView(row); col.addView(Ui.spacer(c,16))

        val chartCard = Ui.card(c)
        chartCard.addView(Ui.sectionHeader(c, "Last 14 days"))
        chartCard.addView(Ui.body(c, "Filled = turned away · faint = opened anyway").also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,12)) })
        chartCard.addView(buildChart(c))
        col.addView(chartCard)

        val note = Ui.card(c)
        note.addView(Ui.body(c,
            "Friction stops the impulse, but lasting change comes from pairing it with a commitment you set in advance and a better thing to do instead.",
            Ui.MUTED, 13f))
        col.addView(note)
    }

    private fun actionableCards(c: Context) {
        val total14 = Store.interceptions.count { it.day >= Store.today() - 13 }
        if (total14 < 4) {
            col.addView(Ui.emptyState(c, "Still listening", "Patterns take a few days.",
                "Keep going and Margin will start showing you when and what you reach for — and a way to act on it."))
            return
        }
        val top = Store.topInterceptedPackage(14)
        val peak = Store.peakInterceptionHour(14)
        if (top != null && peak != null) {
            val label = appLabel(c, top)
            val band = hourBand(peak)
            val n = Store.interceptionsByPackage(14)[top] ?: 0
            val card = Ui.card(c)
            card.addView(Ui.eyebrow(c, "A pattern"))
            card.addView(Ui.title(c, "$label pulls at you most around $band.", 18f).also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,8)) })
            card.addView(Ui.body(c, "That's $n of your last $total14 reaches. The cleanest fix is to close it off before it starts."))
            val start = (peak * 60).coerceIn(0, 1380)
            val end = ((peak + 2) * 60).coerceAtMost(1440)
            val act = Ui.primary(c, "Block $label around $band").also { it.setPadding(0, Ui.dp(c,12),0,0) }
            act.setOnClickListener {
                Ui.haptic(it)
                startActivity(Intent(c, RuleEditorActivity::class.java)
                    .putExtra(RuleEditorActivity.EXTRA_PREFILL_PKG, top)
                    .putExtra(RuleEditorActivity.EXTRA_PREFILL_START, start)
                    .putExtra(RuleEditorActivity.EXTRA_PREFILL_END, end))
            }
            card.addView(Ui.spacer(c,12)); card.addView(act)
            col.addView(card)
        }
        val ratio = Store.resistRatio(7)
        if (ratio >= 0.6f) {
            val card = Ui.card(c)
            card.addView(Ui.eyebrow(c, "You're shifting"))
            card.addView(Ui.title(c, "You turned away ${(ratio*100).toInt()}% of urges this week.", 17f).also { it.setPadding(0, Ui.dp(c,8),0,0) })
            col.addView(card)
        }
    }

    private fun buildChart(c: Context): View {
        val today = Store.today()
        val resisted = (0..13).map { i -> val d = today - 13 + i; Store.interceptions.count { it.day == d && !it.proceeded } }
        val proceeded = (0..13).map { i -> val d = today - 13 + i; Store.interceptions.count { it.day == d && it.proceeded } }
        val max = (0..13).maxOf { resisted[it] + proceeded[it] }.coerceAtLeast(1)
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c,110))
        }
        for (i in 0..13) {
            val total = resisted[i] + proceeded[i]
            val colmn = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    .also { it.marginStart = Ui.dp(c,2); it.marginEnd = Ui.dp(c,2) }
            }
            colmn.addView(View(c).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, (max - total).toFloat()) })
            if (proceeded[i] > 0) colmn.addView(View(c).apply { setBackgroundColor(0xFFD8CFC4.toInt()); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, proceeded[i].toFloat()) })
            if (resisted[i] > 0) colmn.addView(View(c).apply { setBackgroundColor(Ui.SAGE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, resisted[i].toFloat()) })
            row.addView(colmn)
        }
        return row
    }
}
