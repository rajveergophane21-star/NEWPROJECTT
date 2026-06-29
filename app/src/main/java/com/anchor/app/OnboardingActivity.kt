package com.anchor.app

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Setup. Honest about what blocking needs and why. The accessibility disclosure is
 * deliberately prominent: Margin only reads WHICH app is in front, never its content,
 * and nothing leaves the device.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private val steps = mutableListOf<StepView>()
    private lateinit var doneBtn: TextView
    private var progressText: TextView? = null

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
        root.addView(Ui.frank(this, "calm", 96).also {
            (it.layoutParams as LinearLayout.LayoutParams).also { lp -> lp.gravity = android.view.Gravity.CENTER_HORIZONTAL }
            it.setPadding(0, Ui.dp(this, 8), 0, 0)
        })
        root.addView(Ui.eyebrow(this, "Set up Tame"))
        root.addView(Ui.display(this, "Three quick grants.").also { it.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10)) })
        root.addView(Ui.body(this,
            "Taming apps and feeds without rooting your phone means borrowing a few of Android's own controls. Tame only reads which app and screen are in front — never your content — and nothing ever leaves your device.")
            .also { it.setPadding(0, 0, 0, Ui.dp(this, 16)) })

        progressText = Ui.eyebrow(this, "").also { it.setPadding(0, 0, 0, Ui.dp(this, 12)) }
        root.addView(progressText)

        steps.clear()
        steps += StepView("Accessibility",
            "The engine. Switch Tame on in the list. It lets Tame notice the moment a blocked app or feed opens and step in. It reads only which app and screen are in front — never your content.",
            { Perms.hasAccessibility(this) }, { startActivity(Perms.accessibilityIntent()) })
        steps += StepView("Display over other apps",
            "So the block screen and the live reel counter can appear on top of an app the instant it opens.",
            { Perms.canDrawOverlays(this) }, { startActivity(Perms.overlayIntent(this)) })
        steps += StepView("Notifications",
            "A quiet, permanent notification keeps the shield alive in the background.",
            { Perms.hasNotifications(this) }, { requestNotifications() })
        steps += StepView("Ignore battery optimisation",
            "Optional, but recommended — stops the system from killing the shield to save power.",
            { Perms.ignoringBattery(this) }, { startActivity(Perms.batteryIntent(this)) }, optional = true)

        steps.forEach { root.addView(it.build()) }

        doneBtn = Ui.primary(this, "Done")
        doneBtn.setOnClickListener {
            Ui.haptic(it)
            getSharedPreferences("margin_flags", MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
            if (Perms.coreReady(this)) MonitorService.start(this)
            finish()
        }
        root.addView(Ui.spacer(this, 6)); root.addView(doneBtn)
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
        val ready = listOf(Perms.hasAccessibility(this), Perms.canDrawOverlays(this), Perms.hasNotifications(this)).count { it }
        progressText?.text = if (ready == 3) "All set — Tame is ready" else "$ready of 3 essentials ready"
        progressText?.setTextColor(if (ready == 3) Ui.GREEN_TEXT else Ui.MUTED)
        if (::doneBtn.isInitialized) {
            val core = ready == 3
            doneBtn.isEnabled = core
            doneBtn.alpha = if (core) 1f else 0.4f
            doneBtn.text = if (core) "Done" else "Grant the three above"
        }
    }

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
                textSize = 11f
                setPadding(Ui.dp(this@OnboardingActivity, 10), Ui.dp(this@OnboardingActivity, 4), Ui.dp(this@OnboardingActivity, 10), Ui.dp(this@OnboardingActivity, 4))
                background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.pill)
            }
            header.addView(tcol); header.addView(chip)
            c.addView(header)
            c.addView(Ui.body(this@OnboardingActivity, why).also { it.setPadding(0, Ui.dp(this@OnboardingActivity, 8), 0, Ui.dp(this@OnboardingActivity, 12)) })
            btn = Ui.ghost(this@OnboardingActivity, "Open settings")
            btn.setOnClickListener { action() }
            c.addView(btn)
            return c
        }

        fun update() {
            val ok = granted()
            chip.text = if (ok) "Granted" else if (optional) "Optional" else "Needed"
            chip.setTextColor(if (ok) Ui.GREEN_TEXT else Ui.MUTED)
            chip.backgroundTintList = ColorStateList.valueOf(if (ok) Ui.GREEN_WASH else Ui.SURFACE2)
            btn.visibility = if (ok) View.GONE else View.VISIBLE
        }
    }
}
