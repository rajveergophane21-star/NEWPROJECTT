package com.anchor.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/* ============================== TODAY ============================== */

class TodayFragment : BaseFragment() {
    override fun render() {
        val c = requireContext()
        val hour = LocalTime.now().hour
        val greet = when { hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
        col.addView(Ui.eyebrow(c, LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))))
        col.addView(Ui.title(c, "$greet.", 28f).also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,18)) })

        // Shield status
        if (!Perms.coreReady(c)) {
            val card = Ui.card(c)
            card.addView(Ui.eyebrow(c, "Shield · offline"))
            card.addView(Ui.title(c, "Your shield isn't armed yet.", 18f).also { it.setPadding(0, Ui.dp(c,6),0, Ui.dp(c,8)) })
            card.addView(Ui.body(c, "Blocking needs a few system permissions. It takes about a minute."))
            val b = Ui.primary(c, "Finish setup").also { it.setPadding(0, Ui.dp(c,10),0,0) }
            b.setOnClickListener { startActivity(Intent(c, OnboardingActivity::class.java)) }
            card.addView(Ui.spacer(c,10)); card.addView(b)
            col.addView(card)
        } else {
            val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
            val dow = LocalDate.now().dayOfWeek.value
            val active = Store.rules.count { it.activeNow(nowMin, dow) }
            val card = Ui.card(c)
            card.addView(Ui.eyebrow(c, "Shield"))
            val headline = when {
                Store.focusActive() -> "Focus session running."
                active > 0 -> "$active ${if (active==1) "boundary" else "boundaries"} holding right now."
                Store.rules.any { it.enabled } -> "Armed and watching."
                else -> "No boundaries set."
            }
            card.addView(Ui.title(c, headline, 18f).also { it.setPadding(0, Ui.dp(c,6),0, Ui.dp(c,10)) })
            card.addView(Ui.body(c, "${Store.resistedToday()} urges turned away today · ${Store.interceptionsToday()} times Anchor stepped in."))
            col.addView(card)
        }

        // Focus now
        val fcard = Ui.card(c)
        if (Store.focusActive()) {
            fcard.addView(Ui.eyebrow(c, "Focus session"))
            fcard.addView(Ui.title(c, "${Store.focusMinutesLeft()} minutes left.", 20f).also { it.setPadding(0, Ui.dp(c,6),0, Ui.dp(c,8)) })
            fcard.addView(Ui.body(c, "Your distractions are sealed off. Stay with what matters."))
            val end = Ui.ghost(c, "End focus early").also { it.setPadding(0, Ui.dp(c,10),0,0) }
            end.setOnClickListener { Store.stopFocus(); refresh() }
            fcard.addView(Ui.spacer(c,10)); fcard.addView(end)
        } else {
            fcard.addView(Ui.eyebrow(c, "Focus now"))
            fcard.addView(Ui.title(c, "Seal off distractions, right now.", 20f).also { it.setPadding(0, Ui.dp(c,6),0, Ui.dp(c,8)) })
            fcard.addView(Ui.body(c, "Blocks every app in your rules for a set stretch — no schedule needed."))
            val rowB = Ui.row(c).also { it.setPadding(0, Ui.dp(c,12),0,0) }
            listOf(25, 45, 60).forEachIndexed { i, m ->
                val btn = Ui.ghost(c, "${m}m").apply {
                    layoutParams = LinearLayout.LayoutParams(0, Ui.dp(c,48), 1f).also { if (i>0) it.marginStart = Ui.dp(c,8) }
                }
                btn.setOnClickListener { startFocus(m) }
                rowB.addView(btn)
            }
            fcard.addView(rowB)
        }
        col.addView(fcard)

        // Today's replacement habits
        if (Store.habits.isNotEmpty()) {
            val hcard = Ui.card(c)
            hcard.addView(Ui.eyebrow(c, "Instead, do this"))
            hcard.addView(Ui.spacer(c,6))
            Store.habits.forEach { h -> hcard.addView(habitRow(c, h)) }
            col.addView(hcard)
        }
    }

    private fun startFocus(min: Int) {
        val c = requireContext()
        val union = Store.rules.flatMap { it.packages }.toSet()
        if (union.isEmpty()) { Toast.makeText(c, "Add a rule with some apps first", Toast.LENGTH_SHORT).show(); return }
        if (!Perms.coreReady(c)) { startActivity(Intent(c, OnboardingActivity::class.java)); return }
        Store.startFocus(union, min)
        MonitorService.start(c)
        refresh()
    }

    private fun habitRow(c: android.content.Context, h: Habit): View {
        val row = Ui.row(c).also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,8)) }
        val done = Store.isDoneToday(h)
        val tcol = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tcol.addView(Ui.body(c, h.name, Ui.TEXT, 15f))
        tcol.addView(Ui.body(c, "🔥 ${Store.currentStreak(h)}-day streak", Ui.MUTED, 12f))
        val check = TextView(c).apply {
            text = if (done) "✓" else ""; gravity = Gravity.CENTER; textSize = 18f; setTextColor(Ui.INK)
            background = ContextCompat.getDrawable(c, R.drawable.circle)
            backgroundTintList = ColorStateList.valueOf(if (done) Ui.SAGE else 0xFF1C1F26.toInt())
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c,38), Ui.dp(c,38))
            setOnClickListener { Store.toggleToday(h); refresh() }
        }
        row.addView(tcol); row.addView(check)
        return row
    }
}

/* ============================== SHIELD ============================== */

class ShieldFragment : BaseFragment() {
    override fun render() {
        val c = requireContext()
        col.addView(Ui.eyebrow(c, "Shield"))
        col.addView(Ui.title(c, "Your boundaries.", 28f).also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,16)) })

        if (!Perms.coreReady(c)) {
            val card = Ui.card(c)
            card.addView(Ui.title(c, "Setup unfinished", 16f))
            card.addView(Ui.body(c, "Rules won't enforce until permissions are granted.").also { it.setPadding(0, Ui.dp(c,6),0, Ui.dp(c,10)) })
            val b = Ui.ghost(c, "Finish setup"); b.setOnClickListener { startActivity(Intent(c, OnboardingActivity::class.java)) }
            card.addView(b); col.addView(card)
        }

        val add = Ui.primary(c, "New rule")
        add.setOnClickListener { startActivity(Intent(c, RuleEditorActivity::class.java)) }
        col.addView(add); col.addView(Ui.spacer(c,14))

        if (Store.rules.isEmpty()) {
            val card = Ui.card(c)
            card.addView(Ui.title(c, "No boundaries yet.", 16f))
            card.addView(Ui.body(c, "A rule is a set of apps, a schedule, and what happens when you reach for them. Start with the one that costs you the most time.")
                .also { it.setPadding(0, Ui.dp(c,6),0,0) })
            col.addView(card)
            return
        }

        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val dow = LocalDate.now().dayOfWeek.value
        Store.rules.forEach { r -> col.addView(ruleCard(c, r, nowMin, dow)) }
    }

    private fun ruleCard(c: android.content.Context, r: Rule, nowMin: Int, dow: Int): View {
        val card = Ui.card(c)
        val header = Ui.row(c)
        val tcol = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleRow = Ui.row(c)
        titleRow.addView(Ui.title(c, r.name, 17f))
        if (Store.isLocked(r)) {
            titleRow.addView(TextView(c).apply {
                text = "  🔒"; textSize = 13f
            })
        }
        tcol.addView(titleRow)
        val modeChip = TextView(c).apply {
            text = if (r.mode == Mode.BLOCK) "BLOCK" else "FRICTION"
            textSize = 10f; letterSpacing = 0.1f
            setTextColor(if (r.mode == Mode.BLOCK) Ui.CLAY else Ui.SAGE)
            setPadding(Ui.dp(c,8), Ui.dp(c,3), Ui.dp(c,8), Ui.dp(c,3))
            background = ContextCompat.getDrawable(c, R.drawable.pill)
        }
        val chipWrap = LinearLayout(c).apply { setPadding(0, Ui.dp(c,6),0,0) }
        chipWrap.addView(modeChip)
        tcol.addView(chipWrap)

        val sw = MaterialSwitch(c).apply {
            isChecked = r.enabled
            isEnabled = !Store.isLocked(r)
            setOnCheckedChangeListener { _, v ->
                r.enabled = v; Store.save()
                if (Perms.coreReady(c)) { if (Store.rules.any { it.enabled }) MonitorService.start(c) else MonitorService.stop(c) }
            }
        }
        header.addView(tcol); header.addView(sw)
        card.addView(header)

        val apps = if (r.packages.size == 1) "1 app" else "${r.packages.size} apps"
        card.addView(Ui.body(c, apps, Ui.MUTED, 13f).also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,2)) })
        r.windows.forEach { w -> card.addView(Ui.body(c, w.label(), Ui.MUTED, 13f)) }

        if (r.activeNow(nowMin, dow)) {
            card.addView(Ui.body(c, "● Active now — ${r.minutesLeft(nowMin, dow)} min left", Ui.SAGE, 12f)
                .also { it.setPadding(0, Ui.dp(c,8),0,0) })
        }

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
        col.addView(Ui.title(c, "What you do instead.", 28f).also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,6)) })
        col.addView(Ui.body(c, "Removing a habit leaves a gap. Fill it deliberately — that's what makes the change hold.")
            .also { it.setPadding(0,0,0, Ui.dp(c,16)) })

        // overview
        val total = Store.habits.size
        val done = Store.doneTodayCount()
        val overview = Ui.card(c)
        val orow = Ui.row(c)
        val ring = RingView(c).apply {
            setProgress(if (total == 0) 0f else done.toFloat() / total)
            setCenterText(if (total == 0) "—" else "${(done * 100 / total)}%")
            setSubText("today")
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c,96), Ui.dp(c,96))
        }
        val stats = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = Ui.dp(c,16) }
        }
        stats.addView(Ui.body(c, "$done of $total done today", Ui.TEXT, 15f))
        stats.addView(Ui.body(c, "Best streak · ${Store.habits.maxOfOrNull { Store.bestStreak(it) } ?: 0} days", Ui.MUTED, 13f))
        orow.addView(ring); orow.addView(stats)
        overview.addView(orow)
        col.addView(overview)

        // add habit
        val addCard = Ui.card(c)
        addCard.addView(Ui.eyebrow(c, "Add one"))
        val name = EditText(c).apply {
            hint = "A tiny habit — e.g. 10 push-ups"; setHintTextColor(Ui.FAINT); setTextColor(Ui.TEXT); textSize = 15f
            background = ContextCompat.getDrawable(c, R.drawable.input)
            setPadding(Ui.dp(c,14), Ui.dp(c,12), Ui.dp(c,14), Ui.dp(c,12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c,48)).also { it.topMargin = Ui.dp(c,8) }
        }
        val anchor = EditText(c).apply {
            hint = "After I… (an existing routine to attach it to)"; setHintTextColor(Ui.FAINT); setTextColor(Ui.TEXT); textSize = 14f
            background = ContextCompat.getDrawable(c, R.drawable.input)
            setPadding(Ui.dp(c,14), Ui.dp(c,12), Ui.dp(c,14), Ui.dp(c,12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c,46)).also { it.topMargin = Ui.dp(c,8) }
        }
        val addBtn = Ui.primary(c, "Add habit").also { it.setPadding(0, Ui.dp(c,10),0,0) }
        addBtn.setOnClickListener {
            val n = name.text.toString().trim()
            if (n.isEmpty()) { Toast.makeText(c, "Name it first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            Store.addHabit(n, anchor.text.toString()); refresh()
        }
        addCard.addView(name); addCard.addView(anchor); addCard.addView(Ui.spacer(c,10)); addCard.addView(addBtn)
        col.addView(addCard)

        // list
        Store.habits.forEach { h -> col.addView(habitCard(c, h)) }
    }

    private fun habitCard(c: android.content.Context, h: Habit): View {
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
            backgroundTintList = ColorStateList.valueOf(if (done) Ui.SAGE else 0xFF1C1F26.toInt())
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c,44), Ui.dp(c,44))
            setOnClickListener { Store.toggleToday(h); refresh() }
        }
        header.addView(tcol); header.addView(check)
        card.addView(header)
        card.addView(Ui.body(c, "🔥 ${Store.currentStreak(h)}-day streak · best ${Store.bestStreak(h)}", Ui.MUTED, 13f)
            .also { it.setPadding(0, Ui.dp(c,10),0, Ui.dp(c,10)) })
        val heat = HeatmapView(c).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setDays(Store.heatDays(h, 16 * 7), 16)
        }
        card.addView(heat)
        card.setOnLongClickListener {
            androidx.appcompat.app.AlertDialog.Builder(c)
                .setTitle("Delete \"${h.name}\"?")
                .setPositiveButton("Delete") { _, _ -> Store.deleteHabit(h); refresh() }
                .setNegativeButton("Cancel", null).show()
            true
        }
        return card
    }
}

/* ============================== INSIGHTS ============================== */

class InsightsFragment : BaseFragment() {
    override fun render() {
        val c = requireContext()
        col.addView(Ui.eyebrow(c, "Insights"))
        col.addView(Ui.title(c, "How it's going.", 28f).also { it.setPadding(0, Ui.dp(c,8),0, Ui.dp(c,16)) })

        // stat tiles 2x2
        fun tileRow(a: Pair<String,String>, b: Pair<String,String>): LinearLayout {
            val row = Ui.row(c).also { it.setPadding(0,0,0, Ui.dp(c,10)) }
            val (t1, _) = Ui.statTile(c, a.first, a.second)
            val (t2, _) = Ui.statTile(c, b.first, b.second)
            t1.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = Ui.dp(c,10) }
            t2.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(t1); row.addView(t2); return row
        }
        col.addView(tileRow("${Store.resistedToday()}" to "turned away today", "${Store.resistedTotal()}" to "turned away total"))
        col.addView(tileRow("${Store.interceptionsToday()}" to "intercepts today",
            "${Store.habits.maxOfOrNull { Store.bestStreak(it) } ?: 0}" to "best habit streak"))

        // 14-day chart
        val chartCard = Ui.card(c)
        chartCard.addView(Ui.eyebrow(c, "Last 14 days"))
        chartCard.addView(Ui.body(c, "Sage = urges you turned away · clay = opened anyway").also { it.setPadding(0, Ui.dp(c,6),0, Ui.dp(c,12)) })
        chartCard.addView(buildChart(c))
        col.addView(chartCard)

        // evidence note
        val note = Ui.card(c)
        note.addView(Ui.eyebrow(c, "What the research says"))
        note.addView(Ui.body(c,
            "Friction reliably stops the impulse in the moment, but on its own it doesn't durably cut how often you reach for an app. " +
            "Lasting change comes from pairing it with a commitment you set in advance and a better behaviour to put in its place. " +
            "That's why Anchor has all three: the pause, the lock, and your replacement habits.", Ui.MUTED, 13f)
            .also { it.setPadding(0, Ui.dp(c,8),0,0) })
        col.addView(note)
    }

    private fun buildChart(c: android.content.Context): View {
        val today = Store.today()
        val days = (0..13).map { today - 13 + it }
        val resisted = days.map { d -> Store.interceptions.count { it.day == d && !it.proceeded } }
        val proceeded = days.map { d -> Store.interceptions.count { it.day == d && it.proceeded } }
        val max = (0..13).maxOf { resisted[it] + proceeded[it] }.coerceAtLeast(1)

        val rowH = Ui.dp(c, 120)
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH)
        }
        for (i in 0..13) {
            val total = resisted[i] + proceeded[i]
            val colmn = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    .also { it.marginStart = Ui.dp(c,2); it.marginEnd = Ui.dp(c,2) }
            }
            val emptyW = (max - total).toFloat()
            colmn.addView(View(c).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, emptyW) })
            if (proceeded[i] > 0) colmn.addView(View(c).apply {
                setBackgroundColor(Ui.CLAY)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, proceeded[i].toFloat())
            })
            if (resisted[i] > 0) colmn.addView(View(c).apply {
                setBackgroundColor(Ui.SAGE)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, resisted[i].toFloat())
            })
            row.addView(colmn)
        }
        return row
    }
}
