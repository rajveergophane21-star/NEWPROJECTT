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
 * Setup flow. Anchor is honest about what it needs and why — real blocking on
 * non-rooted Android requires a few system grants, and hiding that erodes trust.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private val steps = mutableListOf<StepView>()
    private lateinit var doneBtn: com.google.android.material.button.MaterialButton

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
        root.addView(Ui.eyebrow(this, "Setup"))
        root.addView(Ui.title(this, "A few keys to the gate.", 26f).also { it.setPadding(0, Ui.dp(this,8),0,0) })
        root.addView(Ui.body(this,
            "Blocking apps without rooting your phone means borrowing a few of Android's own controls. " +
            "Anchor only ever reads which app is in front — never your content, and nothing leaves your device.")
            .also { (it.layoutParams as? LinearLayout.LayoutParams) ; it.setPadding(0, Ui.dp(this,10),0, Ui.dp(this,20)) })

        steps.clear()
        steps += StepView("Accessibility · the engine",
            "Find Anchor in the list and switch it on. This is what lets the shield notice the moment a blocked app opens and step in. It only reads which app is in front — never your content.",
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
            getSharedPreferences("anchor_flags", MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
            if (Store.rules.any { it.enabled }) MonitorService.start(this)
            finish()
        }
        root.addView(Ui.spacer(this, 6))
        root.addView(doneBtn)
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
        if (::doneBtn.isInitialized) {
            val core = Perms.coreReady(this)
            doneBtn.isEnabled = core
            doneBtn.alpha = if (core) 1f else 0.4f
            doneBtn.text = if (core) "I'm set up" else "Grant the three above to continue"
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
        private lateinit var btn: com.google.android.material.button.MaterialButton

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
            chip.backgroundTintList = ColorStateList.valueOf(if (ok) 0xFF1C2A22.toInt() else 0xFF1C1F26.toInt())
            btn.visibility = if (ok) View.GONE else View.VISIBLE
        }
    }
}
