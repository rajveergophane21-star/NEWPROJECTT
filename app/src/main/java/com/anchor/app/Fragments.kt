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
        col.addView(Ui.eyebrow(c, "Tame"))
        col.addView(Ui.display(c, "Rules").also { it.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 16)) })

        if (!Perms.coreReady(c)) {
            val card = Ui.card(c)
            card.addView(Ui.title(c, "Finish setup", 16f))
            card.addView(Ui.body(c, "Rules won't take effect until permissions are granted.").also { it.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 12)) })
            val btn = Ui.ghost(c, "Finish setup")
            btn.setOnClickListener { startActivity(Intent(c, OnboardingActivity::class.java)) }
            card.addView(btn); col.addView(card)
        }

        val add = Ui.primary(c, "New rule")
        add.setOnClickListener { Ui.haptic(it); startActivity(Intent(c, RuleEditorActivity::class.java)) }
        col.addView(add); col.addView(Ui.spacer(c, 18))

        if (Store.rules.isEmpty()) {
            col.addView(Ui.emptyState(c, "Rules", "Nothing tamed yet.",
                "Add a rule: pick an app or its short-form feed, when it's off-limits, and what happens when you reach for it."))
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
        val isFeed = r.kind == Kind.FEED
        val chipRow = LinearLayout(c).apply { setPadding(0, Ui.dp(c, 8), 0, 0); gravity = Gravity.CENTER_VERTICAL }
        chipRow.addView(Ui.chip(c, if (isFeed) "Feed" else "App", Ui.MUTED, Ui.SURFACE2)
            .also { (it.layoutParams as? LinearLayout.LayoutParams)?.marginEnd = Ui.dp(c, 6) })
        chipRow.addView(Ui.chip(c, if (isBlock) "Block" else "Friction",
            if (isBlock) Ui.CLAY else Ui.GREEN_TEXT, if (isBlock) Ui.CLAY_WASH else Ui.GREEN_WASH)
            .also { (it.layoutParams as? LinearLayout.LayoutParams)?.marginEnd = Ui.dp(c, 6) })
        if (r.strict) {
            val locked = Store.isLocked(r)
            chipRow.addView(Ui.chip(c, if (locked) "Locked" else "Committed",
                if (locked) 0xFFB8860B.toInt() else Ui.MUTED, if (locked) 0xFFFBF3DC.toInt() else Ui.SURFACE2))
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

        val targets = if (r.packages.size == 1) appLabel(c, r.packages.first()) else "${r.packages.size} apps"
        val sub = if (isFeed) "$targets · ${FeedDetector.feedLabel(r.packages.first())}" else targets
        card.addView(Ui.body(c, sub, Ui.MUTED, 13f).also { it.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 2)) })
        r.windows.forEach { card.addView(Ui.body(c, it.label(), Ui.MUTED, 13f)) }

        if (isFeed && r.reelLimit != null) {
            val used = Store.reelCountToday(r.packages.first())
            card.addView(Ui.body(c, "$used / ${r.reelLimit} reels today",
                if (used >= r.reelLimit!!) Ui.OVER else Ui.MUTED, 13f).also { it.setPadding(0, Ui.dp(c, 6), 0, 0) })
        }
        if (r.activeNow(nowMin, dow)) {
            card.addView(Ui.body(c, "● On now", Ui.ACC_TEXT, 12.5f).also { it.setPadding(0, Ui.dp(c, 8), 0, 0) })
        }

        card.setOnClickListener {
            startActivity(Intent(c, RuleEditorActivity::class.java).putExtra(RuleEditorActivity.EXTRA_RULE_ID, r.id))
        }
        return card
    }

    private fun appLabel(c: Context, pkg: String): String = try {
        c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }
}

/* ============================== HABITS ============================== */

class HabitsFragment : BaseFragment() {
    private var pendingAdd = ""
    private val notifPerm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

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
            text = if (rem != null) "⏰  ${fmtTime(rem)} alarm" else "+ Add alarm reminder"
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
                    else {
                        Store.setReminder(h, null); ReminderScheduler.cancel(c, h)
                        AlarmService.stop(c); refresh()
                    }
                }
                .setNegativeButton("Cancel", null).show()
        }
    }

    private fun pickReminderTime(c: Context, h: Habit, current: Int) {
        android.app.TimePickerDialog(c, { _, hh, mm ->
            Store.setReminder(h, hh * 60 + mm)
            ReminderScheduler.schedule(c, h)
            // Make sure reminders can actually show.
            if (!Perms.hasNotifications(c)) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                    notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else
                    Toast.makeText(c, "Turn on notifications in Settings to get reminders", Toast.LENGTH_LONG).show()
            }
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
                    .setPositiveButton("Delete") { _, _ ->
                        ReminderScheduler.cancel(c, h); AlarmService.stop(c)
                        Store.deleteHabit(h); refresh()
                    }
                    .setNegativeButton("Cancel", null).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

/* =============================== HOME =============================== */

class HomeFragment : BaseFragment() {
    override fun render() {
        val c = requireContext()
        val hour = LocalTime.now().hour
        val greeting = when {
            hour < 5 -> "Still up?"
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            hour < 22 -> "Good evening"
            else -> "Winding down"
        }
        val date = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d"))

        col.addView(Ui.eyebrow(c, date))
        val titleRow = Ui.row(c).also { it.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 16)) }
        titleRow.addView(Ui.display(c, greeting).also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        val bestStreak = Store.habits.maxOfOrNull { Store.currentStreak(it) } ?: 0
        if (bestStreak > 0) titleRow.addView(Ui.chip(c, "⚡ $bestStreak", Ui.GREEN_TEXT, Ui.GREEN_WASH))
        col.addView(titleRow)

        if (!Perms.coreReady(c)) {
            val card = Ui.card(c)
            card.addView(Ui.title(c, "Finish setup", 16f))
            card.addView(Ui.body(c, "Tame can't step in until permissions are granted.").also { it.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 12)) })
            val btn = Ui.ghost(c, "Finish setup")
            btn.setOnClickListener { startActivity(Intent(c, OnboardingActivity::class.java)) }
            card.addView(btn); col.addView(card)
        }

        col.addView(heroCard(c))

        // Active now
        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val dow = LocalDate.now().dayOfWeek.value
        val activeRules = Store.rules.filter { it.activeNow(nowMin, dow) }
        col.addView(Ui.spacer(c, 4))
        col.addView(Ui.sectionHeader(c, "Active now").also { it.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 12)) })
        if (activeRules.isEmpty()) {
            val card = Ui.card(c)
            card.addView(Ui.body(c, if (Store.rules.isEmpty()) "No rules yet. Add one to give Frank a break." else "Nothing on right now — you're free.", Ui.MUTED, 14f))
            val btn = Ui.ghost(c, if (Store.rules.isEmpty()) "New rule" else "Manage rules").also { it.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12)) }
            btn.setOnClickListener {
                if (Store.rules.isEmpty()) startActivity(Intent(c, RuleEditorActivity::class.java))
                else (activity as? MainActivity)?.goTab(1)
            }
            card.addView(Ui.spacer(c, 10)); card.addView(btn)
            col.addView(card)
        } else {
            activeRules.forEach { r -> col.addView(activeRuleRow(c, r)) }
        }

        // Today's habits
        if (Store.habits.isNotEmpty()) {
            col.addView(Ui.spacer(c, 4))
            col.addView(Ui.sectionHeader(c, "Today's habits").also { it.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 12)) })
            val card = Ui.card(c)
            Store.habits.forEachIndexed { i, h ->
                if (i > 0) card.addView(Ui.hairline(c))
                card.addView(habitQuickRow(c, h))
            }
            col.addView(card)
        }
    }

    private fun heroCard(c: Context): View {
        // Aggregate today's reels across feed rules.
        val feedRules = Store.rules.filter { it.kind == Kind.FEED }
        val pkgs = feedRules.flatMap { it.packages }.toSet()
        val reels = pkgs.sumOf { Store.reelCountToday(it) }
        val limit = feedRules.mapNotNull { it.reelLimit }.minOrNull()
        val ratio = if (limit != null && limit > 0) reels.toFloat() / limit else 0f
        val over = ratio >= 1f
        val mood = when {
            over -> "crying"
            ratio >= 0.85f -> "worried"
            ratio >= 0.5f -> "neutral"
            reels > 0 -> "happy"
            else -> "calm"
        }

        val card = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(c, if (over) R.drawable.card_dark else R.drawable.card)
            setPadding(Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20), Ui.dp(c, 20))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = Ui.dp(c, 14) }
        }
        card.addView(Ui.frank(c, mood, 84).also { (it.layoutParams as LinearLayout.LayoutParams).marginEnd = Ui.dp(c, 16) })

        val textColor = if (over) Ui.DARK_TEXT else Ui.TEXT
        val subColor = if (over) 0x99FFFFFF.toInt() else Ui.MUTED
        val tcol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val kicker = when {
            feedRules.isEmpty() -> "Frank's calm"
            over -> "Limit reached"
            ratio >= 0.85f -> "Almost there"
            else -> "Reels today"
        }
        tcol.addView(Ui.eyebrow(c, kicker).also { it.setTextColor(subColor) })
        val headline = when {
            feedRules.isEmpty() -> "No feeds to watch"
            over -> "Feeds locked — rest now"
            limit != null -> "$reels / $limit"
            else -> "$reels reels"
        }
        tcol.addView(Ui.numeral(c, headline, if (feedRules.isEmpty() || over) 22f else 36f, if (over) Ui.OVER else if (ratio >= 0.85f) Ui.POP else Ui.SAGE)
            .also { it.setPadding(0, Ui.dp(c, 6), 0, 0) })
        if (feedRules.isNotEmpty() && limit != null && !over) {
            // progress bar
            val bar = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 6)).also { it.topMargin = Ui.dp(c, 12) }
            }
            val frac = ratio.coerceIn(0f, 1f)
            val fillColor = if (ratio >= 0.85f) Ui.POP else Ui.SAGE
            bar.addView(View(c).apply { setBackgroundColor(fillColor); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, frac.coerceAtLeast(0.0001f)) })
            bar.addView(View(c).apply { setBackgroundColor(if (over) 0x33FFFFFF else Ui.SURFACE2); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (1f - frac).coerceAtLeast(0.0001f)) })
            tcol.addView(bar)
            tcol.addView(Ui.body(c, "${(limit - reels).coerceAtLeast(0)} left today", subColor, 12.5f).also { it.setPadding(0, Ui.dp(c, 8), 0, 0) })
        } else {
            val sub = when {
                feedRules.isEmpty() -> "Add a feed rule to keep Frank in check."
                over -> "They open again tomorrow."
                else -> "Tame is keeping count."
            }
            tcol.addView(Ui.body(c, sub, subColor, 13f).also { it.setPadding(0, Ui.dp(c, 6), 0, 0) })
        }
        card.addView(tcol)
        return card
    }

    private fun activeRuleRow(c: Context, r: Rule): View {
        val card = Ui.card(c).also { it.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14)) }
        val row = Ui.row(c)
        val tcol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        tcol.addView(Ui.title(c, r.name, 16f))
        val kind = if (r.kind == Kind.FEED) "Feed" else "App"
        val mode = if (r.mode == Mode.BLOCK) "Block" else "Friction"
        tcol.addView(Ui.body(c, "$kind · $mode", Ui.MUTED, 12.5f).also { it.setPadding(0, Ui.dp(c, 3), 0, 0) })
        row.addView(tcol)
        row.addView(Ui.chip(c, if (r.mode == Mode.BLOCK) "On" else "On",
            if (r.mode == Mode.BLOCK) Ui.CLAY else Ui.GREEN_TEXT, if (r.mode == Mode.BLOCK) Ui.CLAY_WASH else Ui.GREEN_WASH))
        card.addView(row)
        card.setOnClickListener { (activity as? MainActivity)?.goTab(1) }
        return card
    }

    private fun habitQuickRow(c: Context, h: Habit): View {
        val row = Ui.row(c).also { it.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10)) }
        val done = Store.isDoneToday(h)
        row.addView(View(c).apply {
            background = ContextCompat.getDrawable(c, if (done) R.drawable.check_on else R.drawable.ring)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)).also { it.marginEnd = Ui.dp(c, 14) }
            isClickable = true
            setOnClickListener { Ui.haptic(this); Store.toggleToday(h); refresh() }
        })
        row.addView(Ui.title(c, h.name, 15.5f).also {
            it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (done) it.setTextColor(Ui.MUTED)
        })
        val streak = Store.currentStreak(h)
        if (streak > 0) row.addView(Ui.chip(c, "⚡ $streak", Ui.GREEN_TEXT, Ui.GREEN_WASH))
        return row
    }
}

/* =============================== YOU =============================== */

class YouFragment : BaseFragment() {
    override fun render() {
        val c = requireContext()
        col.addView(Ui.eyebrow(c, "Tame"))
        col.addView(Ui.display(c, "You").also { it.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 16)) })

        // Frank stats hero
        val hero = Ui.card(c).also { it.gravity = Gravity.CENTER; it.setPadding(Ui.dp(c, 20), Ui.dp(c, 22), Ui.dp(c, 20), Ui.dp(c, 22)) }
        val bestStreak = Store.habits.maxOfOrNull { Store.bestStreak(it) } ?: 0
        val mood = if (bestStreak >= 3) "calm" else "happy"
        hero.addView(Ui.frank(c, mood, 96).also { it.layoutParams = (it.layoutParams as LinearLayout.LayoutParams).also { lp -> lp.gravity = Gravity.CENTER } })
        hero.addView(Ui.serifHead(c, "Frank's got your back", 18f).also { it.setPadding(0, Ui.dp(c, 12), 0, 0); it.gravity = Gravity.CENTER })
        hero.addView(Ui.body(c, "A calmer relationship with your phone — built one rule at a time.", Ui.MUTED, 13.5f)
            .also { it.setPadding(0, Ui.dp(c, 6), 0, 0); it.gravity = Gravity.CENTER; (it as TextView).gravity = Gravity.CENTER })
        col.addView(hero)

        // Stats row
        val statsRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = Ui.dp(c, 14) }
        }
        val reelsToday = Store.rules.filter { it.kind == Kind.FEED }.flatMap { it.packages }.toSet().sumOf { Store.reelCountToday(it) }
        val committed = Store.rules.count { it.strict }
        statsRow.addView(Ui.statTile(c, "${Store.rules.size}", "Rules", Ui.SAGE).first.also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { lp -> lp.marginEnd = Ui.dp(c, 8) } })
        statsRow.addView(Ui.statTile(c, "$reelsToday", "Reels today", if (reelsToday > 0) Ui.POP else Ui.SAGE).first.also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { lp -> lp.marginEnd = Ui.dp(c, 8) } })
        statsRow.addView(Ui.statTile(c, "$bestStreak", "Best streak", Ui.GREEN).first.also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        col.addView(statsRow)

        // Commitment
        if (committed > 0) {
            val card = Ui.card(c)
            card.addView(Ui.title(c, "$committed ${if (committed == 1) "rule" else "rules"} committed", 16f))
            card.addView(Ui.body(c, "Locked in — can't be turned off while active. That's the point.", Ui.MUTED, 13.5f).also { it.setPadding(0, Ui.dp(c, 5), 0, 0) })
            col.addView(card)
        }

        // Permissions
        col.addView(Ui.sectionHeader(c, "Permissions").also { it.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 12)) })
        val pcard = Ui.card(c)
        pcard.addView(permRow(c, "Accessibility", Perms.hasAccessibility(c)))
        pcard.addView(Ui.hairline(c))
        pcard.addView(permRow(c, "Display over apps", Perms.canDrawOverlays(c)))
        pcard.addView(Ui.hairline(c))
        pcard.addView(permRow(c, "Notifications", Perms.hasNotifications(c)))
        if (!Perms.coreReady(c)) {
            val fix = Ui.primary(c, "Fix permissions").also { it.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12)) }
            fix.setOnClickListener { startActivity(Intent(c, OnboardingActivity::class.java)) }
            pcard.addView(Ui.spacer(c, 12)); pcard.addView(fix)
        }
        col.addView(pcard)

        // Privacy + replay
        val card = Ui.card(c)
        card.addView(Ui.title(c, "Private by design", 16f))
        card.addView(Ui.body(c, "Tame is fully offline. No account, no servers, no analytics — everything stays on this device.", Ui.MUTED, 13.5f).also { it.setPadding(0, Ui.dp(c, 5), 0, 0) })
        val replay = Ui.ghost(c, "Replay intro").also { it.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12)) }
        replay.setOnClickListener { startActivity(Intent(c, OnboardingActivity::class.java)) }
        card.addView(Ui.spacer(c, 12)); card.addView(replay)
        col.addView(card)

        col.addView(Ui.body(c, "Tame v1.0", Ui.FAINT, 12f).also { it.gravity = Gravity.CENTER; it.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8)); (it as TextView).gravity = Gravity.CENTER })
    }

    private fun permRow(c: Context, label: String, on: Boolean): View {
        val row = Ui.row(c).also { it.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10)) }
        row.addView(Ui.title(c, label, 15f).also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        row.addView(Ui.chip(c, if (on) "On" else "Off", if (on) Ui.GREEN_TEXT else Ui.CLAY, if (on) Ui.GREEN_WASH else Ui.CLAY_WASH))
        return row
    }
}
