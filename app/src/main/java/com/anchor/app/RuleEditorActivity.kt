package com.anchor.app

import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

/** Create or edit a blocking rule: apps + schedule + mode + commitment lock. */
class RuleEditorActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var appsSummary: TextView
    private lateinit var windowsBox: LinearLayout
    private var modeBlock = true
    private lateinit var blockOpt: LinearLayout
    private lateinit var frictionOpt: LinearLayout

    private val pkgs = linkedSetOf<String>()
    private val windows = mutableListOf<TimeWindow>()
    private var editing: Rule? = null
    private var strict = false

    private val appPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            pkgs.clear()
            res.data?.getStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED)?.let { pkgs.addAll(it) }
            updateAppsSummary()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val (sv, col) = Ui.scroll(this); root = col; setContentView(sv)

        editing = intent.getLongExtra(EXTRA_RULE_ID, -1).takeIf { it >= 0 }?.let { Store.ruleById(it) }
        editing?.let { r ->
            pkgs.addAll(r.packages)
            r.windows.forEach { windows.add(TimeWindow(it.startMin, it.endMin, it.days.toMutableSet())) }
            modeBlock = r.mode == Mode.BLOCK
            strict = r.strict
        }
        if (windows.isEmpty()) windows.add(TimeWindow(9 * 60, 17 * 60, mutableSetOf(1, 2, 3, 4, 5)))

        build()

        editing?.let {
            if (Store.isLocked(it)) lockUi()
        }
    }

    private fun build() {
        root.addView(Ui.eyebrow(this, if (editing == null) "New rule" else "Edit rule"))
        root.addView(Ui.title(this, if (editing == null) "Draw a boundary." else "Adjust the boundary.", 26f)
            .also { it.setPadding(0, Ui.dp(this,8),0, Ui.dp(this,18)) })

        // Name
        val nameCard = Ui.card(this)
        nameCard.addView(Ui.eyebrow(this, "Name"))
        nameInput = EditText(this).apply {
            setText(editing?.name ?: "")
            hint = "e.g. Work hours, Wind-down, No socials"
            setHintTextColor(Ui.FAINT); setTextColor(Ui.TEXT); textSize = 16f
            background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.input)
            setPadding(Ui.dp(this@RuleEditorActivity,14), Ui.dp(this@RuleEditorActivity,12), Ui.dp(this@RuleEditorActivity,14), Ui.dp(this@RuleEditorActivity,12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this@RuleEditorActivity,50)).also { it.topMargin = Ui.dp(this@RuleEditorActivity,8) }
        }
        nameCard.addView(nameInput)
        root.addView(nameCard)

        // Apps
        val appsCard = Ui.card(this)
        appsCard.addView(Ui.eyebrow(this, "Apps"))
        appsSummary = Ui.body(this, "", Ui.TEXT, 15f).also { it.setPadding(0, Ui.dp(this,8),0, Ui.dp(this,12)) }
        appsCard.addView(appsSummary)
        val chooseBtn = Ui.ghost(this, "Choose apps")
        chooseBtn.setOnClickListener {
            appPicker.launch(Intent(this, AppPickerActivity::class.java)
                .putStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED, ArrayList(pkgs)))
        }
        appsCard.addView(chooseBtn)
        root.addView(appsCard)
        updateAppsSummary()

        // Schedule
        val schedCard = Ui.card(this)
        schedCard.addView(Ui.eyebrow(this, "Schedule"))
        schedCard.addView(Ui.body(this, "When should these apps be off-limits?").also { it.setPadding(0, Ui.dp(this,6),0, Ui.dp(this,10)) })
        windowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        schedCard.addView(windowsBox)
        val addSlot = Ui.ghost(this, "Add time slot")
        addSlot.setOnClickListener {
            windows.add(TimeWindow(20 * 60, 23 * 60, mutableSetOf(1,2,3,4,5,6,7)))
            renderWindows()
        }
        schedCard.addView(addSlot)
        root.addView(schedCard)
        renderWindows()

        // Mode
        val modeCard = Ui.card(this)
        modeCard.addView(Ui.eyebrow(this, "When you open one"))
        blockOpt = modeOption("Block", "A calm wall. The only way through is to put the phone down.")
        frictionOpt = modeOption("Friction", "A mandatory breath, then an honest choice. Stops the impulse without locking you out.")
        modeCard.addView(blockOpt); modeCard.addView(Ui.spacer(this,8)); modeCard.addView(frictionOpt)
        root.addView(modeCard)
        applyModeSelection()

        // Strict
        val strictCard = Ui.card(this)
        val sr = Ui.row(this)
        val sc = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        sc.addView(Ui.title(this, "Commitment lock", 16f))
        sc.addView(Ui.body(this, "While this rule is active, you can't switch it off or edit it. A Ulysses contract with your future self.")
            .also { it.setPadding(0, Ui.dp(this,4),0,0) })
        val sw = MaterialSwitch(this).apply { isChecked = strict; thumbTintList = ColorStateList.valueOf(Ui.TEXT) }
        sw.setOnCheckedChangeListener { _, v -> strict = v }
        sr.addView(sc); sr.addView(sw)
        strictCard.addView(sr)
        root.addView(strictCard)

        // Save / delete
        val save = Ui.primary(this, "Save rule")
        save.setOnClickListener { save() }
        root.addView(Ui.spacer(this,4)); root.addView(save)

        if (editing != null) {
            val del = Ui.ghost(this, "Delete rule").apply { setTextColor(Ui.CLAY) }
            del.setOnClickListener { confirmDelete() }
            root.addView(Ui.spacer(this,8)); root.addView(del)
        }
    }

    private fun modeOption(title: String, desc: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.card2)
            setPadding(Ui.dp(this@RuleEditorActivity,14), Ui.dp(this@RuleEditorActivity,12), Ui.dp(this@RuleEditorActivity,14), Ui.dp(this@RuleEditorActivity,12))
        }
        box.addView(Ui.title(this, title, 15f))
        box.addView(Ui.body(this, desc).also { it.setPadding(0, Ui.dp(this,4),0,0) })
        box.setOnClickListener {
            modeBlock = (title == "Block"); applyModeSelection()
        }
        return box
    }

    private fun applyModeSelection() {
        fun mark(box: LinearLayout, on: Boolean) {
            box.background = ContextCompat.getDrawable(this, R.drawable.card2)
            box.backgroundTintList = ColorStateList.valueOf(if (on) 0xFF22303A.toInt() else 0xFF1C1F26.toInt())
            (box.getChildAt(0) as TextView).setTextColor(if (on) Ui.SAGE else Ui.TEXT)
        }
        mark(blockOpt, modeBlock); mark(frictionOpt, !modeBlock)
    }

    private fun updateAppsSummary() {
        appsSummary.text = when (pkgs.size) {
            0 -> "No apps chosen yet."
            1 -> "1 app selected."
            else -> "${pkgs.size} apps selected."
        }
    }

    private fun renderWindows() {
        windowsBox.removeAllViews()
        windows.forEachIndexed { idx, w ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.card2)
                setPadding(Ui.dp(this@RuleEditorActivity,12), Ui.dp(this@RuleEditorActivity,12), Ui.dp(this@RuleEditorActivity,12), Ui.dp(this@RuleEditorActivity,12))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.bottomMargin = Ui.dp(this@RuleEditorActivity,8) }
            }
            val timeRow = Ui.row(this)
            val startBtn = timeChip(TimeWindow.fmt(w.startMin)) {
                pickTime(w.startMin) { w.startMin = it; renderWindows() }
            }
            val dash = TextView(this).apply { text = "  to  "; setTextColor(Ui.MUTED) }
            val endBtn = timeChip(TimeWindow.fmt(w.endMin)) {
                pickTime(w.endMin) { w.endMin = it; renderWindows() }
            }
            val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
            val remove = TextView(this).apply {
                text = "Remove"; setTextColor(Ui.CLAY); textSize = 13f
                visibility = if (windows.size > 1) View.VISIBLE else View.GONE
                setOnClickListener { windows.removeAt(idx); renderWindows() }
            }
            timeRow.addView(startBtn); timeRow.addView(dash); timeRow.addView(endBtn)
            timeRow.addView(spacer); timeRow.addView(remove)
            box.addView(timeRow)

            // day toggles
            val dayRow = Ui.row(this).also { it.setPadding(0, Ui.dp(this,10),0,0) }
            val names = arrayOf("M","T","W","T","F","S","S")
            for (d in 1..7) {
                val on = w.days.contains(d)
                val chip = TextView(this).apply {
                    text = names[d-1]; gravity = Gravity.CENTER; textSize = 13f
                    setTextColor(if (on) Ui.INK else Ui.MUTED)
                    background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.circle_stroke)
                    backgroundTintList = ColorStateList.valueOf(if (on) Ui.SAGE else 0xFF1C1F26.toInt())
                    layoutParams = LinearLayout.LayoutParams(Ui.dp(this@RuleEditorActivity,32), Ui.dp(this@RuleEditorActivity,32))
                        .also { it.marginEnd = Ui.dp(this@RuleEditorActivity,6) }
                    setOnClickListener {
                        if (w.days.contains(d)) w.days.remove(d) else w.days.add(d)
                        renderWindows()
                    }
                }
                dayRow.addView(chip)
            }
            box.addView(dayRow)
            if (w.endMin <= w.startMin) {
                box.addView(Ui.body(this, "End must be after start.", Ui.CLAY, 12f).also { it.setPadding(0, Ui.dp(this,8),0,0) })
            }
            windowsBox.addView(box)
        }
    }

    private fun timeChip(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; setTextColor(Ui.TEXT); textSize = 16f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.pill)
        setPadding(Ui.dp(this@RuleEditorActivity,16), Ui.dp(this@RuleEditorActivity,8), Ui.dp(this@RuleEditorActivity,16), Ui.dp(this@RuleEditorActivity,8))
        setOnClickListener { onClick() }
    }

    private fun pickTime(currentMin: Int, onSet: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> onSet(h * 60 + m) }, currentMin / 60, currentMin % 60, true).show()
    }

    private fun save() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) { toast("Give the rule a name"); return }
        if (pkgs.isEmpty()) { toast("Choose at least one app"); return }
        val valid = windows.filter { it.endMin > it.startMin && it.days.isNotEmpty() }
        if (valid.isEmpty()) { toast("Add a valid time slot with at least one day"); return }
        val mode = if (modeBlock) Mode.BLOCK else Mode.FRICTION

        val r = editing
        if (r == null) {
            Store.addRule(Rule(Store.newId(), name, pkgs.toMutableSet(), valid.toMutableList(), mode, true, strict))
        } else {
            r.name = name
            r.packages.clear(); r.packages.addAll(pkgs)
            r.windows.clear(); r.windows.addAll(valid)
            r.mode = mode; r.strict = strict
            Store.save()
        }
        if (Perms.coreReady(this)) MonitorService.start(this)
        finish()
    }

    private fun confirmDelete() {
        val r = editing ?: return
        if (Store.isLocked(r)) { toast("Locked while active — commitment lock is on"); return }
        AlertDialog.Builder(this)
            .setTitle("Delete \"${r.name}\"?")
            .setPositiveButton("Delete") { _, _ -> Store.deleteRule(r); finish() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun lockUi() {
        AlertDialog.Builder(this)
            .setTitle("This rule is locked")
            .setMessage("The commitment lock is active right now, so it can't be edited until the scheduled window ends. That's the point — you decided this in advance.")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object { const val EXTRA_RULE_ID = "rule_id" }
}
