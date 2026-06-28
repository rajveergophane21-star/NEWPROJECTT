package com.anchor.app

import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime

/** One screen: pick apps, set a schedule, choose Block or Friction. Minimal and direct. */
class RuleEditorActivity : AppCompatActivity() {

    private val pkgs = linkedSetOf<String>()
    private var allDay = true
    private var startMin = 9 * 60
    private var endMin = 17 * 60
    private val days = mutableSetOf(1, 2, 3, 4, 5, 6, 7)
    private var modeBlock = true
    private var strict = false
    private var nameText = ""
    private var editing: Rule? = null

    private lateinit var appsBox: LinearLayout
    private lateinit var scheduleBox: LinearLayout
    private var blockOpt: LinearLayout? = null
    private var frictionOpt: LinearLayout? = null

    private val appPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            pkgs.clear()
            res.data?.getStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED)?.let { pkgs.addAll(it) }
            renderApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)

        editing = intent.getLongExtra(EXTRA_RULE_ID, -1).takeIf { it >= 0 }?.let { Store.ruleById(it) }
        editing?.let { r ->
            pkgs.addAll(r.packages)
            modeBlock = r.mode == Mode.BLOCK
            strict = r.strict
            nameText = r.name
            val w = r.windows.firstOrNull()
            if (w != null && w.allDay) {
                allDay = true
            } else if (w != null) {
                allDay = false; startMin = w.startMin; endMin = w.endMin
                days.clear(); days.addAll(w.days)
            }
        } ?: run {
            intent.getStringExtra(EXTRA_PREFILL_PKG)?.let { p ->
                pkgs.add(p)
                val s = intent.getIntExtra(EXTRA_PREFILL_START, -1)
                val e = intent.getIntExtra(EXTRA_PREFILL_END, -1)
                if (s >= 0 && e > s) { allDay = false; startMin = s; endMin = e }
            }
        }

        setContentView(build())
        editing?.let { if (Store.isLocked(it)) lockUi() }
    }

    private fun lockUi() {
        AlertDialog.Builder(this).setTitle("This block is locked")
            .setMessage("Its commitment lock is active right now, so it can't be edited or turned off until the scheduled window ends. That's the point — you committed to this in advance.")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ -> finish() }
            .show()
    }

    private fun build(): View {
        val (sv, col) = Ui.scroll(this)

        val top = Ui.row(this)
        top.addView(Ui.eyebrow(this, "Cancel").apply { setOnClickListener { finish() } })
        col.addView(top)
        col.addView(Ui.display(this, if (editing == null) "New block" else "Edit block")
            .also { it.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 16)) })

        // Apps
        val appsCard = Ui.card(this)
        appsCard.addView(Ui.eyebrow(this, "Apps"))
        val choose = Ui.ghost(this, "Choose apps").also { it.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10)) }
        choose.setOnClickListener {
            Ui.haptic(it)
            appPicker.launch(Intent(this, AppPickerActivity::class.java)
                .putStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED, ArrayList(pkgs)))
        }
        appsCard.addView(choose)
        appsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appsCard.addView(appsBox)
        col.addView(appsCard)
        renderApps()

        // Schedule
        val schedCard = Ui.card(this)
        val sr = Ui.row(this)
        val sc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        sc.addView(Ui.title(this, "All day, every day", 16f))
        sc.addView(Ui.body(this, "Or set specific hours and days.").also { it.setPadding(0, Ui.dp(this, 4), 0, 0) })
        val sw = Ui.switch(this).apply { isChecked = allDay; setOnCheckedChangeListener { _, v -> allDay = v; renderSchedule() } }
        sr.addView(sc); sr.addView(sw)
        schedCard.addView(sr)
        scheduleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        schedCard.addView(scheduleBox)
        col.addView(schedCard)
        renderSchedule()

        // Mode
        val modeCard = Ui.card(this)
        modeCard.addView(Ui.eyebrow(this, "When you open it"))
        modeCard.addView(Ui.spacer(this, 8))
        blockOpt = modeOption("Block", "A wall. The only way through is to turn back.")
        frictionOpt = modeOption("Friction", "A short breath, then you may choose to continue.")
        modeCard.addView(blockOpt); modeCard.addView(Ui.spacer(this, 8)); modeCard.addView(frictionOpt)
        col.addView(modeCard)
        applyModeSelection()

        // Commitment lock
        val lockCard = Ui.card(this)
        val lr = Ui.row(this)
        val lc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        lc.addView(Ui.title(this, "Commitment lock", 16f))
        lc.addView(Ui.body(this, "While active, this block can't be turned off or edited. A contract with your future self.").also { it.setPadding(0, Ui.dp(this, 4), 0, 0) })
        val lsw = Ui.switch(this)
        lsw.isChecked = strict
        lsw.setOnCheckedChangeListener { _, v ->
            if (v && !strict) {
                lsw.isChecked = false   // hold until confirmed (programmatic set won't re-fire the listener)
                AlertDialog.Builder(this).setTitle("Lock this block?")
                    .setMessage("While it's active you won't be able to turn it off or edit it — by design. You decide now, not in a weak moment.")
                    .setPositiveButton("Lock it") { _, _ -> strict = true; lsw.isChecked = true }
                    .setNegativeButton("Cancel") { _, _ -> lsw.isChecked = false }
                    .show()
            } else {
                strict = v
            }
        }
        lr.addView(lc); lr.addView(lsw)
        lockCard.addView(lr)
        col.addView(lockCard)

        // Name
        val nameCard = Ui.card(this)
        nameCard.addView(Ui.eyebrow(this, "Name (optional)"))
        val nameInput = EditText(this).apply {
            setText(nameText); isSingleLine = true
            hint = "e.g. Work hours"; setHintTextColor(Ui.FAINT); setTextColor(Ui.TEXT); textSize = 16f
            background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.input)
            setPadding(Ui.dp(this@RuleEditorActivity, 14), Ui.dp(this@RuleEditorActivity, 12), Ui.dp(this@RuleEditorActivity, 14), Ui.dp(this@RuleEditorActivity, 12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = Ui.dp(this@RuleEditorActivity, 8) }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { nameText = s?.toString() ?: "" }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        nameCard.addView(nameInput); col.addView(nameCard)

        val save = Ui.primary(this, if (editing == null) "Add block" else "Save")
        save.setOnClickListener { Ui.haptic(it); save() }
        col.addView(save)

        if (editing != null) {
            val del = Ui.ghost(this, "Delete block").apply { setTextColor(Ui.CLAY) }
            del.setOnClickListener { confirmDelete() }
            col.addView(Ui.spacer(this, 8)); col.addView(del)
        }
        return sv
    }

    private fun renderApps() {
        appsBox.removeAllViews()
        if (pkgs.isEmpty()) {
            appsBox.addView(Ui.body(this, "No apps chosen yet.", Ui.MUTED, 13f).also { it.setPadding(0, Ui.dp(this, 10), 0, 0) })
            return
        }
        val pm = packageManager
        pkgs.forEach { p ->
            val row = Ui.row(this).also { it.setPadding(0, Ui.dp(this, 12), 0, 0) }
            row.addView(ImageView(this).apply {
                try { setImageDrawable(pm.getApplicationIcon(p)) } catch (_: Exception) {}
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@RuleEditorActivity, 30), Ui.dp(this@RuleEditorActivity, 30)).also { it.marginEnd = Ui.dp(this@RuleEditorActivity, 12) }
            })
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (_: Exception) { p }
            row.addView(Ui.body(this, label, Ui.TEXT, 15f))
            appsBox.addView(row)
        }
    }

    private fun renderSchedule() {
        scheduleBox.removeAllViews()
        if (allDay) return
        scheduleBox.addView(Ui.spacer(this, 12))
        val timeRow = Ui.row(this)
        timeRow.addView(timeChip(TimeWindow.fmt(startMin)) { pickTime(startMin) { startMin = it; renderSchedule() } })
        timeRow.addView(Ui.body(this, "  to  ", Ui.MUTED, 14f))
        timeRow.addView(timeChip(TimeWindow.fmt(endMin)) { pickTime(endMin) { endMin = it; renderSchedule() } })
        scheduleBox.addView(timeRow)
        if (startMin == endMin) scheduleBox.addView(Ui.body(this, "Start and end can't be the same.", Ui.CLAY, 12f).also { it.setPadding(0, Ui.dp(this, 8), 0, 0) })
        else if (endMin < startMin) scheduleBox.addView(Ui.body(this, "Overnight — ends the next morning.", Ui.MUTED, 12f).also { it.setPadding(0, Ui.dp(this, 8), 0, 0) })

        val dayRow = Ui.row(this).also { it.setPadding(0, Ui.dp(this, 14), 0, 0) }
        val names = arrayOf("M", "T", "W", "Th", "F", "Sa", "Su")
        for (d in 1..7) {
            val on = days.contains(d)
            dayRow.addView(TextView(this).apply {
                text = names[d - 1]; gravity = Gravity.CENTER; textSize = 13f; typeface = Ui.monoMed(this@RuleEditorActivity)
                setTextColor(if (on) Ui.INK else Ui.MUTED)
                background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.circle_stroke)
                backgroundTintList = ColorStateList.valueOf(if (on) Ui.SAGE else Ui.SURFACE2)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@RuleEditorActivity, 34), Ui.dp(this@RuleEditorActivity, 34)).also { it.marginEnd = Ui.dp(this@RuleEditorActivity, 6) }
                setOnClickListener { if (days.contains(d)) days.remove(d) else days.add(d); renderSchedule() }
            })
        }
        scheduleBox.addView(dayRow)
    }

    private fun timeChip(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; setTextColor(Ui.TEXT); textSize = 22f; typeface = Ui.vt(this@RuleEditorActivity)
        background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.pill)
        setPadding(Ui.dp(this@RuleEditorActivity, 16), Ui.dp(this@RuleEditorActivity, 6), Ui.dp(this@RuleEditorActivity, 16), Ui.dp(this@RuleEditorActivity, 6))
        setOnClickListener { onClick() }
    }

    private fun pickTime(currentMin: Int, onSet: (Int) -> Unit) {
        val h = (currentMin / 60).coerceIn(0, 23); val m = currentMin % 60
        TimePickerDialog(this, { _, hh, mm -> onSet(hh * 60 + mm) }, h, m, true).show()
    }

    private fun modeOption(title: String, desc: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.card2)
            setPadding(Ui.dp(this@RuleEditorActivity, 16), Ui.dp(this@RuleEditorActivity, 14), Ui.dp(this@RuleEditorActivity, 16), Ui.dp(this@RuleEditorActivity, 14))
        }
        val head = Ui.row(this)
        head.addView(Ui.title(this, title, 16f).also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        val radio = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.ic_check_px))
            background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.circle)
            val pad = Ui.dp(this@RuleEditorActivity, 3); setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@RuleEditorActivity, 18), Ui.dp(this@RuleEditorActivity, 18))
        }
        head.addView(radio)
        box.addView(head)
        box.addView(Ui.body(this, desc).also { it.setPadding(0, Ui.dp(this, 5), 0, 0) })
        box.setOnClickListener { Ui.haptic(it); modeBlock = (title == "Block"); applyModeSelection() }
        return box
    }

    private fun applyModeSelection() {
        fun mark(box: LinearLayout?, on: Boolean, block: Boolean) {
            box ?: return
            box.backgroundTintList = ColorStateList.valueOf(when {
                !on -> Ui.SURFACE2
                block -> 0xFFF6E2CE.toInt()
                else -> 0xFFF3E6C6.toInt()
            })
            val head = box.getChildAt(0) as LinearLayout
            (head.getChildAt(0) as TextView).setTextColor(if (!on) Ui.TEXT else if (block) Ui.ACC_TEXT else 0xFF8A4E0E.toInt())
            val radio = head.getChildAt(1)
            radio.backgroundTintList = ColorStateList.valueOf(if (on) (if (block) Ui.SAGE else Ui.TERRA) else 0x00000000)
            radio.visibility = if (on) View.VISIBLE else View.INVISIBLE
        }
        mark(blockOpt, modeBlock, true); mark(frictionOpt, !modeBlock, false)
    }

    private fun save() {
        if (pkgs.isEmpty()) { toast("Choose at least one app"); return }
        val windows = if (allDay) {
            mutableListOf(TimeWindow(0, 1440, mutableSetOf(1, 2, 3, 4, 5, 6, 7)))
        } else {
            if (startMin == endMin || days.isEmpty()) { toast("Set a valid time range and days"); return }
            mutableListOf(TimeWindow(startMin, endMin, days.toMutableSet()))
        }
        val name = nameText.trim().ifEmpty {
            try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkgs.first(), 0)).toString() } catch (_: Exception) { "Blocked apps" }
        }
        val mode = if (modeBlock) Mode.BLOCK else Mode.FRICTION

        val saved: Rule
        val r = editing
        if (r == null) {
            saved = Rule(Store.newId(), name, pkgs.toMutableSet(), windows, mode, true, strict)
            Store.addRule(saved)
        } else {
            r.name = name; r.packages.clear(); r.packages.addAll(pkgs)
            r.windows.clear(); r.windows.addAll(windows); r.mode = mode; r.strict = strict
            saved = r; Store.save()
        }
        Store.claimPackages(saved)
        if (Perms.coreReady(this)) MonitorService.start(this)

        val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
        val dow = LocalDate.now().dayOfWeek.value
        val msg = when {
            !Perms.coreReady(this) -> "Saved. Finish setup to start blocking."
            saved.activeNow(nowMin, dow) -> "Saved and on now — open the app to see it."
            else -> "Saved. Active ${windows.first().label()}."
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun confirmDelete() {
        val r = editing ?: return
        if (Store.isLocked(r)) { toast("Locked while active — can't delete"); return }
        AlertDialog.Builder(this).setTitle("Delete \"${r.name}\"?")
            .setPositiveButton("Delete") { _, _ -> Store.deleteRule(r); finish() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_PREFILL_PKG = "prefill_pkg"
        const val EXTRA_PREFILL_START = "prefill_start"
        const val EXTRA_PREFILL_END = "prefill_end"
    }
}
