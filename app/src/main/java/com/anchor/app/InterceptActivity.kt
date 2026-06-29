package com.anchor.app

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The takeover shown after Tame closes a blocked app or feed. The app is already gone (we pressed
 * Home); this screen sits over Home so the only way forward is an intentional one.
 *
 * BLOCK   — a calm wall with Frank; the only way out is to turn back.
 * FRICTION — Frank breathes with you, then an honest choice to continue or stay out.
 */
class InterceptActivity : AppCompatActivity() {

    private lateinit var pkg: String
    private var mode = Mode.BLOCK
    private var kind = Kind.APP
    private var minutesLeft = -1
    private var ruleName = ""

    private var timer: CountDownTimer? = null
    private var breath: ValueAnimator? = null
    private var canProceed = false

    private lateinit var root: LinearLayout
    private lateinit var headline: TextView
    private lateinit var appLine: TextView
    private lateinit var frank: ImageView
    private lateinit var primaryBtn: TextView
    private var secondaryBtn: TextView? = null

    // Tame takeover palette
    private val bg = 0xFF10160F.toInt()
    private val ink = 0xFFFFFFFF.toInt()
    private val soft = 0xB3FFFFFF.toInt()
    private val accent = Ui.SAGE
    private val pop = Ui.POP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)

        pkg = intent.getStringExtra(EXTRA_PKG) ?: run { finish(); return }
        mode = runCatching { Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "BLOCK") }.getOrDefault(Mode.BLOCK)
        kind = runCatching { Kind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "APP") }.getOrDefault(Kind.APP)
        minutesLeft = intent.getIntExtra(EXTRA_LEFT, -1)
        ruleName = intent.getStringExtra(EXTRA_RULE) ?: "Tame"

        setContentView(buildUi())
        window.statusBarColor = bg
        window.navigationBarColor = bg

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })

        root.post { root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
        if (mode == Mode.BLOCK) renderBlock() else renderFriction()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun buildUi(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            fitsSystemWindows = true
            setPadding(dp(34), dp(24), dp(34), dp(36))
        }

        frank = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(124), dp(124))
        }
        val eyebrow = TextView(this).apply {
            text = (if (kind == Kind.FEED) FeedDetector.feedLabel(pkg) else ruleName).uppercase()
            setTextColor(pop); textSize = 12f
            letterSpacing = 0.18f; typeface = Ui.sansMed(this@InterceptActivity); gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }
        headline = TextView(this).apply {
            setTextColor(ink); textSize = 32f; gravity = Gravity.CENTER
            typeface = Ui.serif(this@InterceptActivity); setPadding(0, dp(8), 0, 0)
        }
        appLine = TextView(this).apply {
            setTextColor(soft); textSize = 15.5f; gravity = Gravity.CENTER
            typeface = Ui.sans(this@InterceptActivity); setPadding(0, dp(12), 0, 0); setLineSpacing(dp(3).toFloat(), 1f)
        }
        root.addView(frank); root.addView(eyebrow); root.addView(headline); root.addView(appLine)
        return root
    }

    private fun targetLabel(): String =
        if (kind == Kind.FEED) "${FeedDetector.feedLabel(pkg)} on ${appName()}" else appName()

    // ---------------------------------------------------------------- block
    private fun renderBlock() {
        frank.setImageResource(Ui.frankRes("angry"))
        headline.text = "Not now."
        appLine.text = "${targetLabel()} is blocked${untilSuffix()}."
        primaryBtn = filledButton("Turn back", pop, 0xFF10160F.toInt()) { leave() }
        root.addView(primaryBtn, btnParams(topDp = 30))
    }

    private fun untilSuffix(): String {
        if (kind == Kind.FEED && Store.reelLimitFor(pkg) != null) return " for today"
        if (minutesLeft in 1 until 12 * 60) {
            val now = LocalTime.now()
            if (now.toSecondOfDay() + minutesLeft * 60 < 86400) {
                return " until ${now.plusMinutes(minutesLeft.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))}"
            }
        }
        return " right now"
    }

    // ------------------------------------------------------------- friction
    private fun renderFriction() {
        frank.setImageResource(Ui.frankRes("calm"))
        headline.text = "Take a breath."
        appLine.text = "You reached for ${targetLabel()}. Sit with it for a moment."

        primaryBtn = filledButton("No — I'm good", pop, 0xFF10160F.toInt()) { leave() }
        root.addView(primaryBtn, btnParams(topDp = 28))

        secondaryBtn = textButton("Wait ${PAUSE_SECONDS}s…") { if (canProceed) proceed() }.also {
            it.isEnabled = false; it.alpha = 0.5f
            root.addView(it, btnParams(topDp = 10))
        }

        startBreathing()
        val totalMs = PAUSE_SECONDS * 1000L
        timer = object : CountDownTimer(totalMs, 250) {
            override fun onTick(ms: Long) { secondaryBtn?.text = "Wait ${(ms / 1000).toInt() + 1}s…" }
            override fun onFinish() {
                canProceed = true
                secondaryBtn?.apply { text = "Open ${appName()} anyway"; isEnabled = true; animate().alpha(1f).setDuration(220).start() }
            }
        }.start()
    }

    private fun startBreathing() {
        breath = ValueAnimator.ofFloat(0.86f, 1.06f).apply {
            duration = 4000; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { val v = it.animatedValue as Float; frank.scaleX = v; frank.scaleY = v }
            start()
        }
    }

    // ----------------------------------------------------------------- exits
    private fun leave() {
        Enforcer.reset()
        cleanup()
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME); flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    private fun proceed() {
        Store.grantPass(pkg, GRANT_MINUTES)
        Enforcer.reset()
        cleanup()
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        } ?: run {
            startActivity(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        }
        finish()
    }

    private fun cleanup() { timer?.cancel(); timer = null; breath?.cancel(); breath = null }
    override fun onDestroy() { cleanup(); super.onDestroy() }

    // ------------------------------------------------------------- helpers
    private fun appName(): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { "this app" }

    private fun btnParams(topDp: Int) = LinearLayout.LayoutParams(dp(264), ViewGroup.LayoutParams.WRAP_CONTENT)
        .also { it.topMargin = dp(topDp) }

    private fun filledButton(label: String, fill: Int, textColor: Int, onTap: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; setTextColor(textColor); textSize = 16f
        typeface = Ui.sansBold(this@InterceptActivity)
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(fill) }
        setPadding(0, dp(16), 0, dp(16)); isClickable = true; isFocusable = true
        setOnClickListener { Ui.haptic(this); onTap() }
    }

    private fun textButton(label: String, onTap: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; setTextColor(soft); textSize = 14.5f
        typeface = Ui.sans(this@InterceptActivity); setPadding(0, dp(12), 0, dp(12)); isClickable = true
        setOnClickListener { Ui.haptic(this); onTap() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_MODE = "mode"
        const val EXTRA_KIND = "kind"
        const val EXTRA_LEFT = "left"
        const val EXTRA_RULE = "rule"
        private const val PAUSE_SECONDS = 8
        private const val GRANT_MINUTES = 5
    }
}
