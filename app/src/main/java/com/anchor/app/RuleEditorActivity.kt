package com.anchor.app

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
    private lateinit var backBtn: TextView
    private lateinit var nextBtn: TextView

    private var step = 0
    private var firstRun = false

    // working state (preserved across step rebuilds)
    private val pkgs = linkedSetOf<String>()
    private val windows = mutableListOf<TimeWindow>()
    private var modeBlock = true
    private var strict = false
    private var nameText = ""
    private var reasonText = ""
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
            reasonText = r.reason
        }
        // Default to all-day, every-day so a freshly made rule actually fires the moment you
        // test it. Users narrow the window from here; a surprising default reads as "broken".
        if (windows.isEmpty()) windows.add(TimeWindow(0, 1440, mutableSetOf(1, 2, 3, 4, 5, 6, 7)))

        // Prefill from an Insights suggestion ("Block X around the evening").
        if (editing == null) {
            intent.getStringExtra(EXTRA_PREFILL_PKG)?.let { p ->
                pkgs.add(p)
                val s = intent.getIntExtra(EXTRA_PREFILL_START, -1)
                val e = intent.getIntExtra(EXTRA_PREFILL_END, -1)
                if (s >= 0 && e > s) { windows.clear(); windows.add(TimeWindow(s, e, mutableSetOf(1,2,3,4,5,6,7))) }
                nameText = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString() } catch (_: Exception) { "" }
            }
        }

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
        val cancel = (if (firstRun) Ui.eyebrow(this, "Skip") else Ui.eyebrow(this, "Cancel")).apply {
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

        stepTitle = Ui.serifHead(this, "", 30f).also { it.setPadding(0, Ui.dp(this,18),0,0) }
        stepSub = Ui.body(this, "").also { it.setPadding(0, Ui.dp(this,6),0,0) }
        rootV.addView(stepTitle); rootV.addView(stepSub)

        preview = Ui.mono(this, "", Ui.ACC_TEXT, 11f).apply {
            letterSpacing = 0.08f
            setPadding(0, Ui.dp(this@RuleEditorActivity,14), 0, Ui.dp(this@RuleEditorActivity,2))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        rootV.addView(preview)
        rootV.addView(View(this).apply {
            setBackgroundColor(Ui.LINE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@RuleEditorActivity,1).coerceAtLeast(1))
        })

        container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                .also { it.topMargin = Ui.dp(this@RuleEditorActivity,12) }
        }
        rootV.addView(container)

        val footer = Ui.row(this).also { it.setPadding(0, Ui.dp(this,8),0,0) }
        backBtn = Ui.ghost(this, "Back").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = Ui.dp(this@RuleEditorActivity,10) }
            setOnClickListener { Ui.haptic(this); goBack() }
        }
        nextBtn = Ui.primary(this, "Next").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f)
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
        // Motion restraint: a quiet 160ms alpha cross — no slide.
        sv.alpha = 0f
        sv.animate().alpha(1f).setDuration(160).setInterpolator(DecelerateInterpolator()).start()
        updateChrome()
    }

    private fun updateChrome() {
        when (step) {
            0 -> { stepTitle.text = "What's pulling you in?"; stepSub.text = "Pick the apps this rule should govern." }
            1 -> { stepTitle.text = "When is it off-limits?"; stepSub.text = "Add the hours and days the rule applies." }
            else -> { stepTitle.text = "And when you reach for it?"; stepSub.text = "Choose how Margin steps in — and lock it if you mean it." }
        }
        for (d in 0 until dots.childCount) {
            dots.getChildAt(d).backgroundTintList = ColorStateList.valueOf(if (d == step) Ui.SAGE else Ui.LINE)
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
        return "$apps  /  $sched  /  $mode"
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
            timeRow.addView(Ui.mono(this, "  until  ", Ui.FAINT, 10f))
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
                    text = names[d-1]; gravity = Gravity.CENTER; textSize = 12f; typeface = Ui.monoMed(this@RuleEditorActivity)
                    setTextColor(if (on) Ui.INK else Ui.MUTED)
                    background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.circle_stroke)
                    backgroundTintList = ColorStateList.valueOf(if (on) Ui.SAGE else Ui.SURFACE2)
                    layoutParams = LinearLayout.LayoutParams(Ui.dp(this@RuleEditorActivity,34), Ui.dp(this@RuleEditorActivity,34)).also { it.marginEnd = Ui.dp(this@RuleEditorActivity,7) }
                    setOnClickListener {
                        if (w.days.contains(d)) w.days.remove(d) else w.days.add(d)
                        renderWindows()
                    }
                })
            }
            card.addView(dayRow)
            if (w.endMin == w.startMin) card.addView(Ui.body(this, "Start and end can't be the same time.", Ui.CLAY, 12f).also { it.setPadding(0, Ui.dp(this,8),0,0) })
            else if (w.overnight) card.addView(Ui.mono(this, "Overnight - ends next morning", Ui.FAINT, 10f).also { it.setPadding(0, Ui.dp(this,8),0,0) })
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
        val sw = Ui.switch(this).apply { isChecked = strict; setOnCheckedChangeListener { _, c -> strict = c; updateChrome() } }
        sr.addView(sc); sr.addView(sw)
        strictCard.addView(sr)
        v.addView(strictCard)

        // Optional "why" — recalled to you at the intercept moment.
        val whyCard = Ui.card(this)
        whyCard.addView(Ui.eyebrow(this, "Your why (optional)"))
        whyCard.addView(Ui.body(this, "Margin will show this back to you the moment you reach for these apps.").also { it.setPadding(0, Ui.dp(this,6),0,0) })
        val whyInput = EditText(this).apply {
            setText(reasonText)
            hint = "e.g. I want to be present at dinner"; setHintTextColor(Ui.FAINT); setTextColor(Ui.TEXT); textSize = 15f
            background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.input)
            setPadding(Ui.dp(this@RuleEditorActivity,14), Ui.dp(this@RuleEditorActivity,12), Ui.dp(this@RuleEditorActivity,14), Ui.dp(this@RuleEditorActivity,12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = Ui.dp(this@RuleEditorActivity,8) }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { reasonText = s?.toString() ?: "" }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        whyCard.addView(whyInput)
        v.addView(whyCard)

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
            setPadding(Ui.dp(this@RuleEditorActivity,16), Ui.dp(this@RuleEditorActivity,14), Ui.dp(this@RuleEditorActivity,16), Ui.dp(this@RuleEditorActivity,14))
        }
        val head = Ui.row(this)
        head.addView(Ui.title(this, title, 15.5f).also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        val radio = android.widget.ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.ic_check_px))
            background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.circle)
            val pad = Ui.dp(this@RuleEditorActivity,3); setPadding(pad,pad,pad,pad)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@RuleEditorActivity,18), Ui.dp(this@RuleEditorActivity,18))
        }
        head.addView(radio)
        box.addView(head)
        box.addView(Ui.body(this, desc).also { it.setPadding(0, Ui.dp(this,5),0,0) })
        box.setOnClickListener { Ui.haptic(it); modeBlock = (title == "Block"); applyModeSelection(); updateChrome() }
        return box
    }

    private fun applyModeSelection() {
        fun mark(box: LinearLayout?, on: Boolean, block: Boolean) {
            box ?: return
            box.backgroundTintList = ColorStateList.valueOf(when {
                !on -> Ui.SURFACE2
                block -> 0xFFF6E2CE.toInt()    // terracotta wash — Block
                else -> 0xFFF3E6C6.toInt()     // amber wash — Friction
            })
            val head = box.getChildAt(0) as LinearLayout
            (head.getChildAt(0) as TextView).setTextColor(if (!on) Ui.TEXT else if (block) Ui.ACC_TEXT else 0xFF8A4E0E.toInt())
            val radio = head.getChildAt(1)
            radio.backgroundTintList = ColorStateList.valueOf(if (on) (if (block) Ui.SAGE else Ui.TERRA) else 0x00000000)
            radio.visibility = if (on) View.VISIBLE else View.INVISIBLE
        }
        mark(blockOpt, modeBlock, true); mark(frictionOpt, !modeBlock, false)
    }

    // ------------------------------------------------------------- nav
    private fun goNext() {
        when (step) {
            0 -> { if (pkgs.isEmpty()) { toast("Choose at least one app"); return }; showStep(1, true) }
            1 -> {
                if (windows.none { it.endMin != it.startMin && it.days.isNotEmpty() }) { toast("Add a valid time slot with at least one day"); return }
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
        val valid = windows.filter { it.endMin != it.startMin && it.days.isNotEmpty() }
        if (pkgs.isEmpty() || valid.isEmpty()) { toast("Add apps and a valid schedule"); return }
        val mode = if (modeBlock) Mode.BLOCK else Mode.FRICTION

        val r = editing
        if (r == null) {
            Store.addRule(Rule(Store.newId(), name, pkgs.toMutableSet(), valid.toMutableList(), mode, true, strict, reasonText.trim()))
        } else {
            r.name = name; r.packages.clear(); r.packages.addAll(pkgs)
            r.windows.clear(); r.windows.addAll(valid); r.mode = mode; r.strict = strict; r.reason = reasonText.trim()
            Store.save()
        }
        if (Perms.coreReady(this)) MonitorService.start(this)
        // Tell the user when the rule actually applies — a rule tested outside its window
        // otherwise reads as "not working".
        val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        val dow = java.time.LocalDate.now().dayOfWeek.value
        val activeNow = valid.any { it.activeAt(nowMin, dow) }
        if (firstRun) {
            val first = try { val pm = packageManager; pm.getApplicationLabel(pm.getApplicationInfo(pkgs.first(), 0)).toString() } catch (_: Exception) { "that app" }
            Toast.makeText(this, "Shield armed. Open $first to see Margin work.", Toast.LENGTH_LONG).show()
        } else if (!Perms.coreReady(this)) {
            Toast.makeText(this, "Saved. Finish setup in Today to start enforcing.", Toast.LENGTH_LONG).show()
        } else if (activeNow) {
            Toast.makeText(this, "Saved and active now — open the app to see it work.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Saved. Active ${valid.first().label()}.", Toast.LENGTH_LONG).show()
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
        this.text = text; setTextColor(Ui.TEXT); textSize = 22f; typeface = Ui.serif(this@RuleEditorActivity)
        background = ContextCompat.getDrawable(this@RuleEditorActivity, R.drawable.pill)
        setPadding(Ui.dp(this@RuleEditorActivity,16), Ui.dp(this@RuleEditorActivity,6), Ui.dp(this@RuleEditorActivity,16), Ui.dp(this@RuleEditorActivity,6))
        setOnClickListener { onClick() }
    }

    /** A pixel "set the clock" stepper, JRPG style — chunky -/+ keys, big VT323 readout. */
    private fun pickTime(currentMin: Int, onSet: (Int) -> Unit) {
        val c = this
        var h = (currentMin / 60).coerceIn(0, 23)
        // Round to the nearest 5 (not floor) so opening the picker doesn't silently shift the window.
        var m = (((currentMin % 60) + 2) / 5 * 5).coerceIn(0, 55)
        val hh = Ui.numeral(c, "", 46f).apply { gravity = Gravity.CENTER }
        val mm = Ui.numeral(c, "", 46f).apply { gravity = Gravity.CENTER }
        fun upd() { hh.text = "%02d".format(h); mm.text = "%02d".format(m) }
        upd()

        fun key(label: String, onTap: () -> Unit) = TextView(c).apply {
            text = label; gravity = Gravity.CENTER; textSize = 20f; typeface = Ui.serif(c); setTextColor(Ui.TEXT)
            background = ContextCompat.getDrawable(c, R.drawable.btn_ghost)
            setPadding(Ui.dp(c,14), Ui.dp(c,8), Ui.dp(c,16), Ui.dp(c,12))
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c,46), Ui.dp(c,46))
            setOnClickListener { Ui.haptic(this); onTap(); upd() }
        }
        fun unit(num: TextView, dec: () -> Unit, inc: () -> Unit): LinearLayout {
            val r = Ui.row(c).apply { gravity = Gravity.CENTER }
            r.addView(key("-", dec))
            r.addView(LinearLayout(c).apply {
                gravity = Gravity.CENTER
                background = ContextCompat.getDrawable(c, R.drawable.card2)
                setPadding(Ui.dp(c,14), Ui.dp(c,6), Ui.dp(c,14), Ui.dp(c,8))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.marginStart = Ui.dp(c,8); it.marginEnd = Ui.dp(c,8) }
                addView(num)
            })
            r.addView(key("+", inc))
            return r
        }

        val container = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c,22), Ui.dp(c,22), Ui.dp(c,22), Ui.dp(c,18))
        }
        container.addView(Ui.eyebrow(c, "Set time").also { it.gravity = Gravity.CENTER_HORIZONTAL })
        val grid = Ui.row(c).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c,16), 0, Ui.dp(c,18)) }
        val hcol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        hcol.addView(unit(hh, { h = (h + 23) % 24 }, { h = (h + 1) % 24 }))
        hcol.addView(Ui.eyebrow(c, "Hour").also { it.gravity = Gravity.CENTER_HORIZONTAL; it.setPadding(0, Ui.dp(c,6),0,0) })
        val mcol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        mcol.addView(unit(mm, { m = (m + 55) % 60 }, { m = (m + 5) % 60 }))
        mcol.addView(Ui.eyebrow(c, "Min").also { it.gravity = Gravity.CENTER_HORIZONTAL; it.setPadding(0, Ui.dp(c,6),0,0) })
        grid.addView(hcol)
        grid.addView(TextView(c).apply { text = ":"; typeface = Ui.vt(c); textSize = 38f; setTextColor(Ui.MUTED); setPadding(Ui.dp(c,8),0,Ui.dp(c,8),Ui.dp(c,16)) })
        grid.addView(mcol)
        container.addView(grid)
        val ok = Ui.primary(c, "Set")
        container.addView(ok)

        val dialog = AlertDialog.Builder(c).setView(container).create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.card)
        ok.setOnClickListener { onSet(h * 60 + m); dialog.dismiss() }
        dialog.show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_FIRST_RUN = "first_run"
        const val EXTRA_PREFILL_PKG = "prefill_pkg"
        const val EXTRA_PREFILL_START = "prefill_start"
        const val EXTRA_PREFILL_END = "prefill_end"
    }
}
