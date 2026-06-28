package com.anchor.app

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Setup flow. Margin is honest about what it needs and why — real blocking on
 * non-rooted Android requires a few system grants, and hiding that erodes trust.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private val steps = mutableListOf<StepView>()
    private lateinit var doneBtn: TextView
    private var progressText: TextView? = null
    private var identityInput: android.widget.EditText? = null
    private var prevReady = false

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val (sv, col) = Ui.scroll(this)
        root = col
        setContentView(sv)
        build()
    }

    override fun onResume() { super.onResume(); render() }

    private fun build() {
        root.addView(Ui.eyebrow(this, "Welcome to Margin - 1 of 2"))
        root.addView(Ui.display(this, "Who are you becoming?", 34f).also { it.setPadding(0, Ui.dp(this,12),0, Ui.dp(this,10)) })
        root.addView(Ui.body(this,
            "Margin isn't about using your phone less — it's about becoming someone in particular. Say it in your own words; every boundary points back to it.")
            .also { it.setPadding(0,0,0, Ui.dp(this,16)) })

        val idCard = Ui.card(this)
        idCard.addView(Ui.eyebrow(this, "I'm someone who…"))
        val idField = android.widget.EditText(this).apply {
            setText(Store.identity)
            hint = "reads a few pages before bed…"; setHintTextColor(Ui.FAINT); setTextColor(Ui.TEXT); textSize = 21f
            typeface = Ui.serifItalic(this@OnboardingActivity)
            background = null; setPadding(0, Ui.dp(this@OnboardingActivity,10), 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        identityInput = idField
        idCard.addView(idField)
        // example chips
        val chips = Ui.row(this).also { it.setPadding(0, Ui.dp(this,14),0,0) }
        listOf("is present with my kids", "makes things", "sleeps before midnight").forEach { ex ->
            chips.addView(TextView(this).apply {
                text = ex; textSize = 12f; typeface = Ui.sans(this@OnboardingActivity); setTextColor(Ui.MUTED)
                background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.pill)
                setPadding(Ui.dp(this@OnboardingActivity,12), Ui.dp(this@OnboardingActivity,7), Ui.dp(this@OnboardingActivity,12), Ui.dp(this@OnboardingActivity,7))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginEnd = Ui.dp(this@OnboardingActivity,8) }
                setOnClickListener { idField.setText("I $ex"); idField.setSelection(idField.text.length) }
            })
        }
        idCard.addView(chips)
        root.addView(idCard)

        root.addView(Ui.eyebrow(this, "Permissions that make blocking work - 2 of 2").also { it.setPadding(0, Ui.dp(this,12),0, Ui.dp(this,6)) })
        root.addView(Ui.body(this,
            "Blocking without rooting means borrowing a few of Android's own controls. Margin only reads which app is in front — never your content.")
            .also { it.setPadding(0, 0,0, Ui.dp(this,16)) })

        progressText = Ui.eyebrow(this, "0 of 3 essentials ready").also { it.setPadding(0,0,0, Ui.dp(this,16)) }
        root.addView(progressText)

        steps.clear()
        steps += StepView("Accessibility · the engine",
            "Find Margin in the list and switch it on. This is what lets the shield notice the moment a blocked app opens and step in. It only reads which app is in front — never your content.",
            { Perms.hasAccessibility(this) }, { startActivity(Perms.accessibilityIntent()) })

        steps += StepView("Display over other apps",
            "So the intercept screen can appear on top of a blocked app the instant it opens.",
            { Perms.canDrawOverlays(this) }, { startActivity(Perms.overlayIntent(this)) })

        steps += StepView("Notifications",
            "A quiet, permanent notification keeps the shield alive in the background. Android requires it.",
            { Perms.hasNotifications(this) }, { requestNotifications() })

        steps += StepView("Ignore battery optimisation",
            "Optional, but recommended — stops the system from quietly killing the shield to save power.",
            { Perms.ignoringBattery(this) }, { startActivity(Perms.batteryIntent(this)) }, optional = true)

        steps.forEach { root.addView(it.build()) }

        doneBtn = Ui.primary(this, "I'm set up")
        doneBtn.setOnClickListener {
            Ui.haptic(it)
            identityInput?.text?.toString()?.let { s -> if (s.isNotBlank()) Store.updateIdentity(s) }
            getSharedPreferences("anchor_flags", MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
            // Start the shield's foreground service as soon as permissions are in place, so the
            // user gets immediate confirmation it's alive even before the first rule exists.
            if (Perms.coreReady(this)) MonitorService.start(this)
            // Land the user straight in their first block — the activation moment.
            if (Store.rules.isEmpty()) {
                startActivity(Intent(this, RuleEditorActivity::class.java)
                    .putExtra(RuleEditorActivity.EXTRA_FIRST_RUN, true))
            }
            finish()
        }
        root.addView(Ui.spacer(this, 6))
        root.addView(doneBtn)

        Ui.stagger(root)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun render() {
        steps.forEach { it.update() }
        val readyCount = listOf(Perms.hasAccessibility(this), Perms.canDrawOverlays(this), Perms.hasNotifications(this)).count { it }
        progressText?.text = if (readyCount == 3) "All set — your shield is ready" else "Grant the three essentials below"
        progressText?.setTextColor(if (readyCount == 3) Ui.SAGE else Ui.MUTED)
        if (::doneBtn.isInitialized) {
            val core = readyCount == 3
            doneBtn.isEnabled = core
            doneBtn.alpha = if (core) 1f else 0.4f
            doneBtn.text = if (core) "Arm the shield" else "Grant the three above to continue"
            if (core && !prevReady) Ui.pop(doneBtn)
            prevReady = core
        }
    }

    /** One permission step rendered as a card with a live status chip. */
    inner class StepView(
        val titleText: String,
        val why: String,
        val granted: () -> Boolean,
        val action: () -> Unit,
        val optional: Boolean = false
    ) {
        private lateinit var chip: TextView
        private lateinit var btn: TextView

        fun build(): View {
            val c = Ui.card(this@OnboardingActivity)
            val header = Ui.row(this@OnboardingActivity)
            val tcol = LinearLayout(this@OnboardingActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            tcol.addView(Ui.title(this@OnboardingActivity, titleText, 16f))
            chip = TextView(this@OnboardingActivity).apply {
                textSize = 11f; setPadding(Ui.dp(this@OnboardingActivity,10), Ui.dp(this@OnboardingActivity,4), Ui.dp(this@OnboardingActivity,10), Ui.dp(this@OnboardingActivity,4))
                background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.pill)
            }
            header.addView(tcol); header.addView(chip)
            c.addView(header)
            c.addView(Ui.body(this@OnboardingActivity, why).also { it.setPadding(0, Ui.dp(this@OnboardingActivity,8),0,Ui.dp(this@OnboardingActivity,12)) })
            btn = Ui.ghost(this@OnboardingActivity, "Open settings")
            btn.setOnClickListener { action() }
            c.addView(btn)
            return c
        }

        fun update() {
            val ok = granted()
            chip.text = if (ok) "Granted" else if (optional) "Optional" else "Needed"
            chip.setTextColor(if (ok) Ui.SAGE else Ui.MUTED)
            chip.backgroundTintList = ColorStateList.valueOf(if (ok) 0xFFE4D6B8.toInt() else Ui.SURFACE2)
            btn.visibility = if (ok) View.GONE else View.VISIBLE
        }
    }
}
