package com.anchor.app

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime

/* ============================== BLOCKS ============================== */

class BlocksFragment : BaseFragment() {
    override fun render() {
        val c = requireContext()
        col.addView(Ui.eyebrow(c, "Margin"))
        col.addView(Ui.display(c, "Blocks").also { it.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 16)) })

        if (!Perms.coreReady(c)) {
            val card = Ui.card(c)
            card.addView(Ui.title(c, "Finish setup", 16f))
            card.addView(Ui.body(c, "Blocking won't work until permissions are granted.").also { it.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 12)) })
            val btn = Ui.ghost(c, "Finish setup")
            btn.setOnClickListener { startActivity(Intent(c, OnboardingActivity::class.java)) }
            card.addView(btn); col.addView(card)
        }

        val add = Ui.primary(c, "New block")
        add.setOnClickListener { Ui.haptic(it); startActivity(Intent(c, RuleEditorActivity::class.java)) }
        col.addView(add); col.addView(Ui.spacer(c, 18))

        if (Store.rules.isEmpty()) {
            col.addView(Ui.emptyState(c, "Blocks", "Nothing blocked yet.",
                "Add a block: choose the apps, when they're off-limits, and what happens when you reach for them."))
            return
        }

        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val dow = LocalDate.now().dayOfWeek.value
        Store.rules.forEach { col.addView(ruleCard(c, it, nowMin, dow)) }
    }

    private fun ruleCard(c: Context, r: Rule, nowMin: Int, dow: Int): View {
        val card = Ui.card(c)
        val header = Ui.row(c)
        val tcol = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tcol.addView(Ui.title(c, r.name, 17f))
        val isBlock = r.mode == Mode.BLOCK
        val chip = TextView(c).apply {
            text = (if (isBlock) "Block" else "Friction").uppercase()
            textSize = 10f; letterSpacing = 0.08f; typeface = Ui.monoMed(c)
            setTextColor(if (isBlock) Ui.ACC_TEXT else 0xFF8A4E0E.toInt())
            setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5))
            background = ContextCompat.getDrawable(c, R.drawable.pill)
            backgroundTintList = ColorStateList.valueOf(if (isBlock) 0xFFF6E2CE.toInt() else 0xFFF3E6C6.toInt())
        }
        val chipRow = LinearLayout(c).apply { setPadding(0, Ui.dp(c, 6), 0, 0); gravity = Gravity.CENTER_VERTICAL; addView(chip) }
        if (r.strict) {
            val locked = Store.isLocked(r)
            val tag = TextView(c).apply {
                text = if (locked) "LOCKED" else "COMMITTED"
                textSize = 10f; letterSpacing = 0.08f; typeface = Ui.monoMed(c)
                setTextColor(if (locked) Ui.CLAY else Ui.MUTED)
                setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5))
                background = ContextCompat.getDrawable(c, R.drawable.pill)
                backgroundTintList = ColorStateList.valueOf(Ui.SURFACE2)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.marginStart = Ui.dp(c, 6) }
            }
            chipRow.addView(tag)
        }
        tcol.addView(chipRow)

        val sw = Ui.switch(c).apply {
            isChecked = r.enabled
            isEnabled = !Store.isLocked(r)   // can't disable a committed rule while it's active
            setOnCheckedChangeListener { _, v ->
                r.enabled = v; Store.save()
                if (Perms.coreReady(c)) { if (Store.anyEnabled()) MonitorService.start(c) else MonitorService.stop(c) }
            }
        }
        header.addView(tcol); header.addView(sw)
        card.addView(header)

        val apps = if (r.packages.size == 1) "1 app" else "${r.packages.size} apps"
        card.addView(Ui.body(c, apps, Ui.MUTED, 13f).also { it.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 2)) })
        r.windows.forEach { card.addView(Ui.body(c, it.label(), Ui.MUTED, 13f)) }
        if (r.activeNow(nowMin, dow)) {
            card.addView(Ui.body(c, "● On now", Ui.ACC_TEXT, 12f).also { it.setPadding(0, Ui.dp(c, 8), 0, 0) })
        }

        card.setOnClickListener {
            startActivity(Intent(c, RuleEditorActivity::class.java).putExtra(RuleEditorActivity.EXTRA_RULE_ID, r.id))
        }
        return card
    }
}

/* ============================== HABITS ============================== */

class HabitsFragment : BaseFragment() {
    private var pendingAdd = ""

    override fun render() {
        val c = requireContext()
        col.addView(Ui.eyebrow(c, "Daily"))
        col.addView(Ui.display(c, "Habits").also { it.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 16)) })

        val addCard = Ui.card(c)
        addCard.addView(Ui.eyebrow(c, "Add a habit"))
        val field = EditText(c).apply {
            hint = "e.g. 10 push-ups, read a page"; isSingleLine = true
            setHintTextColor(Ui.FAINT); setTextColor(Ui.TEXT); textSize = 15f
            background = ContextCompat.getDrawable(c, R.drawable.input)
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = Ui.dp(c, 8) }
            // Survive a rebuild (e.g. app resumed) without losing what's being typed.
            setText(pendingAdd); setSelection(text.length)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { pendingAdd = s?.toString() ?: "" }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            })
        }
        val addBtn = Ui.primary(c, "Add habit").also { it.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12)) }
        addBtn.setOnClickListener {
            val n = field.text.toString().trim()
            if (n.isEmpty()) { Toast.makeText(c, "Name it first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            Ui.haptic(it); pendingAdd = ""; Store.addHabit(n); refresh()
        }
        addCard.addView(field); addCard.addView(Ui.spacer(c, 8)); addCard.addView(addBtn)
        col.addView(addCard); col.addView(Ui.spacer(c, 8))

        if (Store.habits.isEmpty()) {
            // An inviting box, not a plain empty area.
            val e = Ui.card(c).also { it.setPadding(Ui.dp(c, 20), Ui.dp(c, 24), Ui.dp(c, 20), Ui.dp(c, 24)); it.gravity = Gravity.CENTER }
            e.addView(android.widget.ImageView(c).apply {
                background = ContextCompat.getDrawable(c, R.drawable.check_on)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40))
            })
            e.addView(Ui.title(c, "Build your first habit", 17f).also { it.setPadding(0, Ui.dp(c, 14), 0, 0); it.gravity = Gravity.CENTER })
            e.addView(Ui.body(c, "Add one tiny habit above — small enough you can't talk yourself out of it. Check it off here each day.", Ui.MUTED, 14f)
                .also { it.setPadding(0, Ui.dp(c, 6), 0, 0); it.gravity = Gravity.CENTER })
            col.addView(e)
            return
        }

        // Today-progress box.
        val doneCount = Store.habits.count { Store.isDoneToday(it) }
        val total = Store.habits.size
        val pcard = Ui.card(c)
        val prow = Ui.row(c).also { it.gravity = Gravity.CENTER_VERTICAL }
        val ptcol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        ptcol.addView(Ui.eyebrow(c, "Today"))
        ptcol.addView(Ui.title(c, "$doneCount of $total done", 17f).also { it.setPadding(0, Ui.dp(c, 4), 0, 0) })
        prow.addView(ptcol)
        prow.addView(Ui.numeral(c, "${doneCount * 100 / total}%", 26f, if (doneCount == total) Ui.GREEN else Ui.MUTED))
        pcard.addView(prow)
        val bar = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 5)).also { it.topMargin = Ui.dp(c, 12) }
        }
        val frac = doneCount.toFloat() / total
        bar.addView(View(c).apply { setBackgroundColor(Ui.GREEN); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, frac.coerceAtLeast(0.0001f)) })
        bar.addView(View(c).apply { setBackgroundColor(Ui.SURFACE2); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (1f - frac).coerceAtLeast(0.0001f)) })
        pcard.addView(bar)
        col.addView(pcard); col.addView(Ui.spacer(c, 4))

        Store.habits.forEach { col.addView(habitRow(c, it)) }
    }

    private fun habitRow(c: Context, h: Habit): View {
        val card = Ui.card(c).also { it.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16)) }
        val row = Ui.row(c)
        val done = Store.isDoneToday(h)
        val streak = Store.currentStreak(h)

        val check = View(c).apply {
            background = ContextCompat.getDrawable(c, if (done) R.drawable.check_on else R.drawable.ring)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)).also { it.marginEnd = Ui.dp(c, 14) }
            isClickable = true
            // pendingAdd is preserved across refresh(), so a full rebuild here is safe and keeps
            // the streak chip / status line perfectly in sync.
            setOnClickListener { Ui.haptic(this); Store.toggleToday(h); refresh() }
        }

        val tcol = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tcol.addView(Ui.title(c, h.name, 16f))
        val status = if (done) "Done today" else "Not done today"
        tcol.addView(Ui.body(c, status, if (done) Ui.GREEN_TEXT else Ui.MUTED, 12.5f).also { it.setPadding(0, Ui.dp(c, 3), 0, 0) })

        // Reminder chip — tappable to set / change / remove.
        val rem = h.reminderMinutes
        val remChip = TextView(c).apply {
            text = if (rem != null) "⏰  ${fmtTime(rem)}" else "+ Add reminder"
            textSize = 12f; typeface = Ui.sans(c)
            setTextColor(if (rem != null) Ui.ACC_TEXT else Ui.MUTED)
            setPadding(0, Ui.dp(c, 6), 0, 0); isClickable = true
            setOnClickListener { Ui.haptic(this); reminderFlow(c, h) }
        }
        tcol.addView(remChip)

        row.addView(check); row.addView(tcol)

        // Streak chip — only once there's a streak to show (no discouraging "0").
        if (streak > 0) {
            val chip = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            chip.addView(Ui.numeral(c, "$streak", 24f, Ui.GREEN).also { it.gravity = Gravity.CENTER })
            chip.addView(Ui.eyebrow(c, "day streak").also { it.gravity = Gravity.CENTER })
            row.addView(chip)
        }

        card.addView(row)

        // The contribution grid — last 16 weeks of check-ins.
        card.addView(HeatmapView(c).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = Ui.dp(c, 14) }
            setDays(Store.heatDays(h, 16 * 7), 16)
        })
        val bestLine = "Best ${Store.bestStreak(h)} days"
        card.addView(Ui.body(c, bestLine, Ui.FAINT, 11f).also { it.setPadding(0, Ui.dp(c, 8), 0, 0) })

        card.setOnClickListener { editHabit(c, h) }
        return card
    }

    private fun fmtTime(min: Int): String =
        java.time.LocalTime.of(min / 60, min % 60).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))

    private fun reminderFlow(c: Context, h: Habit) {
        val rem = h.reminderMinutes
        if (rem == null) {
            pickReminderTime(c, h, 8 * 60)
        } else {
            AlertDialog.Builder(c).setTitle("Reminder · ${fmtTime(rem)}")
                .setItems(arrayOf("Change time", "Remove reminder")) { _, which ->
                    if (which == 0) pickReminderTime(c, h, rem)
                    else { Store.setReminder(h, null); ReminderScheduler.cancel(c, h); refresh() }
                }
                .setNegativeButton("Cancel", null).show()
        }
    }

    private fun pickReminderTime(c: Context, h: Habit, current: Int) {
        android.app.TimePickerDialog(c, { _, hh, mm ->
            Store.setReminder(h, hh * 60 + mm)
            ReminderScheduler.schedule(c, h)
            if (!Perms.hasNotifications(c)) Toast.makeText(c, "Turn on notifications to get reminders", Toast.LENGTH_LONG).show()
            refresh()
        }, current / 60, current % 60, false).show()
    }

    private fun editHabit(c: Context, h: Habit) {
        val input = EditText(c).apply {
            setText(h.name); setSelection(text.length); isSingleLine = true
            setTextColor(Ui.TEXT); textSize = 16f
            background = ContextCompat.getDrawable(c, R.drawable.input)
            setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12))
        }
        val body = LinearLayout(c).apply { setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), 0); addView(input) }
        AlertDialog.Builder(c)
            .setTitle("Edit habit")
            .setView(body)
            .setPositiveButton("Save") { _, _ -> val n = input.text.toString().trim(); if (n.isNotEmpty()) { Store.renameHabit(h, n); refresh() } }
            .setNeutralButton("Delete") { _, _ ->
                AlertDialog.Builder(c).setTitle("Delete \"${h.name}\"?")
                    .setMessage("This removes the habit and its history.")
                    .setPositiveButton("Delete") { _, _ -> ReminderScheduler.cancel(c, h); Store.deleteHabit(h); refresh() }
                    .setNegativeButton("Cancel", null).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
