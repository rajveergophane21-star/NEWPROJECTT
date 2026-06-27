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
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Guided, three-step rule creation: Apps -> Schedule -> Response.
 * One decision per screen, a live preview of the rule taking shape, and animated
 * transitions. Editing an existing rule walks the same flow, pre-filled.
 */
class RuleEditorActivity : AppCompatActivity() {

    private lateinit var dots: LinearLayout
    private lateinit var stepTitle: TextView
    private lateinit var stepSub: TextView
    private lateinit var preview: TextView
    private lateinit var container: FrameLayout
    private lateinit var backBtn: com.google.android.material.button.MaterialButton
    private lateinit var nextBtn: com.google.android.material.button.MaterialButton

    private var step = 0
    private var firstRun = false

    // working state (preserved across step rebuilds)
    private val pkgs = linkedSetOf<String>()
    private val windows = mutableListOf<TimeWindow>()
    private var modeBlock = true
    private var strict = false
    private var nameText = ""
    private var editing: Rule? = null

    private var windowsBox: LinearLayout? = null
    private var blockOpt: LinearLayout? = null
    private var frictionOpt: LinearLayout? = null

    private val appPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            pkgs.clear()
            res.data?.getStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED)?.let { pkgs.addAll(it) }
            if (step == 0) showStep(0, true)
            updateChrome()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        firstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)

        editing = intent.getLongExtra(EXTRA_RULE_ID, -1).takeIf { it >= 0 }?.let { Store.ruleById(it) }
        editing?.let { r ->
            pkgs.addAll(r.packages)
            r.windows.forEach { windows.add(TimeWindow(it.startMin, it.endMin, it.days.toMutableSet())) }
            modeBlock = r.mode == Mode.BLOCK
            strict = r.strict
            nameText = r.name
        }
        if (windows.isEmpty()) windows.add(TimeWindow(9 * 60, 17 * 60, mutableSetOf(1, 2, 3, 4, 5)))

        setContentView(buildChrome())
        editing?.let { if (Store.isLocked(it)) { lockUi(); return } }
        showStep(0, true)
    }

    // ------------------------------------------------------------- chrome
    private fun buildChrome(): View {
        val rootV = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            fitsSystemWindows = true
            setPadding(Ui.dp(this@RuleEditorActivity,20), Ui.dp(this@RuleEditorActivity,16), Ui.dp(this@RuleEditorActivity,20), Ui.dp(this@RuleEditorActivity,16))
        }

        // top: cancel + step dots
        val top = Ui.row(this)
        val cancel = TextView(this).apply {
            text = if (firstRun) "Skip" else "Cancel"; setTextColor(Ui.MUTED); textSize = 14f
            setOnClickListener { finish() }
        }
        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        repeat(3) { i ->
            dots.addView(View(this).apply {
                background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.circle)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@RuleEditorActivity,7), Ui.dp(this@RuleEditorActivity,7))
                    .also { it.marginStart = Ui.dp(this@RuleEditorActivity,5) }
            })
        }
        val spacerV = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        top.addView(cancel); top.addView(spacerV); top.addView(dots)
        rootV.addView(top)

        stepTitle = Ui.title(this, "", 26f).also { it.setPadding(0, Ui.dp(this,18),0,0) }
        stepSub = Ui.body(this, "").also { it.setPadding(0, Ui.dp(this,6),0,0) }
        rootV.addView(stepTitle); rootV.addView(stepSub)

        preview = TextView(this).apply {
            setTextColor(Ui.SAGE); textSize = 12f; letterSpacing = 0.02f
            setPadding(Ui.dp(this@RuleEditorActivity,12), Ui.dp(this@RuleEditorActivity,10), Ui.dp(this@RuleEditorActivity,12), Ui.dp(this@RuleEditorActivity,10))
            background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.card2)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = Ui.dp(this@RuleEditorActivity,16) }
        }
        rootV.addView(preview)

        container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                .also { it.topMargin = Ui.dp(this@RuleEditorActivity,12) }
        }
        rootV.addView(container)

        val footer = Ui.row(this).also { it.setPadding(0, Ui.dp(this,8),0,0) }
        backBtn = Ui.ghost(this, "Back").apply {
            layoutParams = LinearLayout.LayoutParams(0, Ui.dp(this@RuleEditorActivity,52), 1f).also { it.marginEnd = Ui.dp(this@RuleEditorActivity,10) }
            setOnClickListener { Ui.haptic(this); goBack() }
        }
        nextBtn = Ui.primary(this, "Next").apply {
            layoutParams = LinearLayout.LayoutParams(0, Ui.dp(this@RuleEditorActivity,52), 1.4f)
            setOnClickListener { Ui.haptic(this); goNext() }
        }
        footer.addView(backBtn); footer.addView(nextBtn)
        rootV.addView(footer)
        return rootV
    }

    private fun showStep(i: Int, forward: Boolean) {
        step = i
        val content = when (i) { 0 -> buildApps(); 1 -> buildSchedule(); else -> buildResponse() }
        val sv = ScrollView(this).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        container.removeAllViews(); container.addView(sv)
        sv.alpha = 0f; sv.translationX = (if (forward) 1 else -1) * Ui.dp(this, 36).toFloat()
        sv.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
        updateChrome()
    }

    private fun updateChrome() {
        when (step) {
            0 -> { stepTitle.text = "What's pulling you in?"; stepSub.text = "Pick the apps this rule should govern." }
            1 -> { stepTitle.text = "When is it off-limits?"; stepSub.text = "Add the hours and days the rule applies." }
            else -> { stepTitle.text = "And when you reach for it?"; stepSub.text = "Choose how Margin steps in — and lock it if you mean it." }
        }
        for (d in 0 until dots.childCount) {
            dots.getChildAt(d).backgroundTintList = ColorStateList.valueOf(if (d == step) Ui.SAGE else 0xFFE7E4DD.toInt())
        }
        backBtn.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        nextBtn.text = if (step == 2) (if (editing == null) "Arm this rule" else "Save rule") else "Next"
        preview.text = previewSummary()
    }

    private fun previewSummary(): String {
        val apps = when (pkgs.size) { 0 -> "No apps"; 1 -> "1 app"; else -> "${pkgs.size} apps" }
        val sched = windows.firstOrNull()?.let { w ->
            if (windows.size > 1) "${windows.size} time slots" else w.label()
        } ?: "No schedule"
        val mode = if (modeBlock) "Block" else "Friction"
        return "$apps  ·  $sched  ·  $mode"
    }

    // ------------------------------------------------------------- step 0
    private fun buildApps(): View {
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val choose = Ui.primary(this, if (pkgs.isEmpty()) "Choose apps" else "Edit selection")
        choose.setOnClickListener {
            Ui.haptic(it)
            appPicker.launch(Intent(this, AppPickerActivity::class.java)
                .putStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED, ArrayList(pkgs)))
        }
        v.addView(choose); v.addView(Ui.spacer(this, 14))

        if (pkgs.isEmpty()) {
            val card = Ui.card(this)
            card.addView(Ui.body(this, "Tip: start with the one app that costs you the most time. You can always add more.", Ui.MUTED, 14f))
            v.addView(card)
        } else {
            val pm = packageManager
            pkgs.forEach { p ->
                val card = Ui.card(this).also { (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = Ui.dp(this,8); it.setPadding(Ui.dp(this,14), Ui.dp(this,12), Ui.dp(this,14), Ui.dp(this,12)) }
                val r = Ui.row(this)
                val icon = android.widget.ImageView(this).apply {
                    try { setImageDrawable(pm.getApplicationIcon(p)) } catch (_: Exception) {}
                    layoutParams = LinearLayout.LayoutParams(Ui.dp(this@RuleEditorActivity,32), Ui.dp(this@RuleEditorActivity,32)).also { it.marginEnd = Ui.dp(this@RuleEditorActivity,12) }
                }
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (_: Exception) { p }
                r.addView(icon); r.addView(Ui.body(this, label, Ui.TEXT, 15f))
                card.addView(r)
                v.addView(card)
            }
        }
        return v
    }

    // ------------------------------------------------------------- step 1
    private fun buildSchedule(): View {
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        windowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        v.addView(windowsBox)
        renderWindows()
        val add = Ui.ghost(this, "Add another time slot")
        add.setOnClickListener {
            Ui.haptic(it)
            windows.add(TimeWindow(20 * 60, 23 * 60, mutableSetOf(1,2,3,4,5,6,7)))
            renderWindows(); updateChrome()
        }
        v.addView(add)
        return v
    }

    private fun renderWindows() {
        val box = windowsBox ?: return
        box.removeAllViews()
        windows.forEachIndexed { idx, w ->
            val card = Ui.card(this).also { (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = Ui.dp(this,10) }
            val timeRow = Ui.row(this)
            timeRow.addView(timeChip(TimeWindow.fmt(w.startMin)) { pickTime(w.startMin) { w.startMin = it; renderWindows(); updateChrome() } })
            timeRow.addView(TextView(this).apply { text = "  to  "; setTextColor(Ui.MUTED) })
            timeRow.addView(timeChip(TimeWindow.fmt(w.endMin)) { pickTime(w.endMin) { w.endMin = it; renderWindows(); updateChrome() } })
            timeRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            if (windows.size > 1) timeRow.addView(TextView(this).apply {
                text = "Remove"; setTextColor(Ui.CLAY); textSize = 13f
                setOnClickListener { windows.removeAt(idx); renderWindows(); updateChrome() }
            })
            card.addView(timeRow)

            val dayRow = Ui.row(this).also { it.setPadding(0, Ui.dp(this,12),0,0) }
            val names = arrayOf("M","T","W","T","F","S","S")
            for (d in 1..7) {
                val on = w.days.contains(d)
                dayRow.addView(TextView(this).apply {
                    text = names[d-1]; gravity = Gravity.CENTER; textSize = 13f
                    setTextColor(if (on) Ui.INK else Ui.MUTED)
                    background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.circle_stroke)
                    backgroundTintList = ColorStateList.valueOf(if (on) Ui.SAGE else 0xFFF4F2ED.toInt())
                    layoutParams = LinearLayout.LayoutParams(Ui.dp(this@RuleEditorActivity,34), Ui.dp(this@RuleEditorActivity,34)).also { it.marginEnd = Ui.dp(this@RuleEditorActivity,7) }
                    setOnClickListener {
                        if (w.days.contains(d)) w.days.remove(d) else w.days.add(d)
                        renderWindows()
                    }
                })
            }
            card.addView(dayRow)
            if (w.endMin <= w.startMin) card.addView(Ui.body(this, "End must be after start.", Ui.CLAY, 12f).also { it.setPadding(0, Ui.dp(this,8),0,0) })
            box.addView(card)
        }
    }

    // ------------------------------------------------------------- step 2
    private fun buildResponse(): View {
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val nameCard = Ui.card(this)
        nameCard.addView(Ui.eyebrow(this, "Name this rule"))
        val nameInput = EditText(this).apply {
            setText(nameText); setSelection(text.length)
            hint = "e.g. Work hours, Wind-down"; setHintTextColor(Ui.FAINT); setTextColor(Ui.TEXT); textSize = 16f
            background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.input)
            setPadding(Ui.dp(this@RuleEditorActivity,14), Ui.dp(this@RuleEditorActivity,12), Ui.dp(this@RuleEditorActivity,14), Ui.dp(this@RuleEditorActivity,12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this@RuleEditorActivity,50)).also { it.topMargin = Ui.dp(this@RuleEditorActivity,8) }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { nameText = s?.toString() ?: "" }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        nameCard.addView(nameInput)
        v.addView(nameCard)

        val modeCard = Ui.card(this)
        modeCard.addView(Ui.eyebrow(this, "Intervention"))
        modeCard.addView(Ui.spacer(this,8))
        blockOpt = modeOption("Block", "A calm wall. The only way through is to put the phone down.")
        frictionOpt = modeOption("Friction", "A mandatory breath, then an honest choice. Stops the impulse without locking you out.")
        modeCard.addView(blockOpt); modeCard.addView(Ui.spacer(this,8)); modeCard.addView(frictionOpt)
        v.addView(modeCard)
        applyModeSelection()

        val strictCard = Ui.card(this)
        val sr = Ui.row(this)
        val sc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        sc.addView(Ui.title(this, "Commitment lock", 16f))
        sc.addView(Ui.body(this, "While active, this rule can't be turned off or edited. A contract with your future self.").also { it.setPadding(0, Ui.dp(this,4),0,0) })
        val sw = MaterialSwitch(this).apply { isChecked = strict; setOnCheckedChangeListener { _, c -> strict = c; updateChrome() } }
        sr.addView(sc); sr.addView(sw)
        strictCard.addView(sr)
        v.addView(strictCard)

        if (editing != null) {
            val del = Ui.ghost(this, "Delete rule").apply { setTextColor(Ui.CLAY) }
            del.setOnClickListener { confirmDelete() }
            v.addView(Ui.spacer(this,4)); v.addView(del)
        }
        return v
    }

    private fun modeOption(title: String, desc: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.card2)
            setPadding(Ui.dp(this@RuleEditorActivity,14), Ui.dp(this@RuleEditorActivity,12), Ui.dp(this@RuleEditorActivity,14), Ui.dp(this@RuleEditorActivity,12))
        }
        box.addView(Ui.title(this, title, 15f))
        box.addView(Ui.body(this, desc).also { it.setPadding(0, Ui.dp(this,4),0,0) })
        box.setOnClickListener { Ui.haptic(it); modeBlock = (title == "Block"); applyModeSelection(); updateChrome() }
        return box
    }

    private fun applyModeSelection() {
        fun mark(box: LinearLayout?, on: Boolean) {
            box ?: return
            box.backgroundTintList = ColorStateList.valueOf(if (on) 0xFFF3E3DB.toInt() else 0xFFF4F2ED.toInt())
            (box.getChildAt(0) as TextView).setTextColor(if (on) Ui.SAGE else Ui.TEXT)
        }
        mark(blockOpt, modeBlock); mark(frictionOpt, !modeBlock)
    }

    // ------------------------------------------------------------- nav
    private fun goNext() {
        when (step) {
            0 -> { if (pkgs.isEmpty()) { toast("Choose at least one app"); return }; showStep(1, true) }
            1 -> {
                if (windows.none { it.endMin > it.startMin && it.days.isNotEmpty() }) { toast("Add a valid time slot with at least one day"); return }
                showStep(2, true)
            }
            else -> save()
        }
    }

    private fun goBack() { if (step > 0) showStep(step - 1, false) }

    override fun onBackPressed() { if (step > 0) goBack() else super.onBackPressed() }

    // ------------------------------------------------------------- save
    private fun save() {
        val name = nameText.trim().ifEmpty {
            // sensible default from the first app's label
            try { val pm = packageManager; pm.getApplicationLabel(pm.getApplicationInfo(pkgs.first(), 0)).toString() } catch (_: Exception) { "Blocked apps" }
        }
        val valid = windows.filter { it.endMin > it.startMin && it.days.isNotEmpty() }
        if (pkgs.isEmpty() || valid.isEmpty()) { toast("Add apps and a valid schedule"); return }
        val mode = if (modeBlock) Mode.BLOCK else Mode.FRICTION

        val r = editing
        if (r == null) {
            Store.addRule(Rule(Store.newId(), name, pkgs.toMutableSet(), valid.toMutableList(), mode, true, strict))
        } else {
            r.name = name; r.packages.clear(); r.packages.addAll(pkgs)
            r.windows.clear(); r.windows.addAll(valid); r.mode = mode; r.strict = strict
            Store.save()
        }
        if (Perms.coreReady(this)) MonitorService.start(this)
        if (firstRun) {
            val first = try { val pm = packageManager; pm.getApplicationLabel(pm.getApplicationInfo(pkgs.first(), 0)).toString() } catch (_: Exception) { "that app" }
            Toast.makeText(this, "Shield armed. Open $first to see Margin work.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun confirmDelete() {
        val r = editing ?: return
        if (Store.isLocked(r)) { toast("Locked while active — commitment lock is on"); return }
        AlertDialog.Builder(this).setTitle("Delete \"${r.name}\"?")
            .setPositiveButton("Delete") { _, _ -> Store.deleteRule(r); finish() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun lockUi() {
        AlertDialog.Builder(this).setTitle("This rule is locked")
            .setMessage("The commitment lock is active right now, so it can't be edited until the scheduled window ends. That's the point — you decided this in advance.")
            .setPositiveButton("OK") { _, _ -> finish() }.setCancelable(false).show()
    }

    // ------------------------------------------------------------- helpers
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

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_FIRST_RUN = "first_run"
    }
}
