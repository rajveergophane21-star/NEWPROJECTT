package com.anchor.app

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The screen shown OVER a blocked app (the app keeps running underneath — we never close it).
 *
 * BLOCK   — a calm wall; the only way out is to turn back.
 * FRICTION — a short mandatory pause, then an honest choice to continue or turn back.
 */
class InterceptActivity : AppCompatActivity() {

    private lateinit var pkg: String
    private var mode = Mode.BLOCK
    private var minutesLeft = -1
    private var ruleName = ""

    private var timer: CountDownTimer? = null
    private var breath: ValueAnimator? = null
    private var canProceed = false

    private lateinit var headline: TextView
    private lateinit var appLine: TextView
    private lateinit var primaryBtn: TextView
    private var secondaryBtn: TextView? = null
    private var orb: View? = null

    // dark, calm palette
    private val bg = 0xFF241C16.toInt()
    private val ink = 0xFFF4ECD8.toInt()
    private val soft = 0xFFC2A87E.toInt()
    private val accent = 0xFFE07A3E.toInt()
    private val glow = 0xFFFFCD75.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)

        pkg = intent.getStringExtra(EXTRA_PKG) ?: run { finish(); return }
        mode = runCatching { Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "BLOCK") }.getOrDefault(Mode.BLOCK)
        minutesLeft = intent.getIntExtra(EXTRA_LEFT, -1)
        ruleName = intent.getStringExtra(EXTRA_RULE) ?: "Margin"

        setContentView(buildUi())
        window.statusBarColor = bg
        window.navigationBarColor = bg

        // Back must never fall through to the blocked app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })

        root.post { root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
        if (mode == Mode.BLOCK) renderBlock() else renderFriction()
    }

    /** singleTask: a new blocked app while this screen is alive must replace its contents,
     *  never show the previous app/mode (which could grant a pass to the wrong package). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private lateinit var root: LinearLayout

    private fun buildUi(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bg)
            fitsSystemWindows = true
            setPadding(dp(34), dp(24), dp(34), dp(34))
        }

        val eyebrow = TextView(this).apply {
            text = ruleName.uppercase(); setTextColor(accent); textSize = 12f
            letterSpacing = 0.18f; typeface = Ui.monoMed(this@InterceptActivity); gravity = Gravity.CENTER
        }
        headline = TextView(this).apply {
            setTextColor(ink); textSize = 30f; gravity = Gravity.CENTER
            typeface = Ui.sansMed(this@InterceptActivity); setPadding(0, dp(14), 0, 0)
        }
        appLine = TextView(this).apply {
            setTextColor(soft); textSize = 15f; gravity = Gravity.CENTER
            typeface = Ui.sans(this@InterceptActivity); setPadding(0, dp(12), 0, 0); setLineSpacing(dp(3).toFloat(), 1f)
        }
        root.addView(eyebrow); root.addView(headline); root.addView(appLine)
        return root
    }

    // ---------------------------------------------------------------- block
    private fun renderBlock() {
        headline.text = "Not now."
        appLine.text = "${appName()} is blocked${untilSuffix()}."
        primaryBtn = filledButton("Turn back") { leave() }
        root.addView(primaryBtn, btnParams(topDp = 32))
    }

    /** " until HH:mm" for a same-day scheduled window under 12h; otherwise " right now". */
    private fun untilSuffix(): String {
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
        headline.text = "Take a breath."
        appLine.text = "You reached for ${appName()}. Sit with it for a moment."

        val wrap = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(150), dp(150)).also { it.topMargin = dp(28) }
        }
        val o = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }
            layoutParams = LinearLayout.LayoutParams(dp(110), dp(110))
        }
        orb = o; wrap.addView(o); root.addView(wrap)

        primaryBtn = filledButton("Turn back") { leave() }
        root.addView(primaryBtn, btnParams(topDp = 28))

        secondaryBtn = textButton("Wait ${PAUSE_SECONDS}s…") { if (canProceed) proceed() }.also {
            it.isEnabled = false; it.alpha = 0.5f
            root.addView(it, btnParams(topDp = 12))
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
        breath = ValueAnimator.ofFloat(0.7f, 1f).apply {
            duration = 4000; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { val v = it.animatedValue as Float; orb?.scaleX = v; orb?.scaleY = v }
            start()
        }
    }

    // ----------------------------------------------------------------- exits
    /** Turn back: go to the home screen so the blocked app is never revealed. */
    private fun leave() {
        // Clear the debounce so an immediate re-tap of the same app re-intercepts (no bypass window).
        Enforcer.reset()
        cleanup()
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME); flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    /** Friction "open anyway": grant a short pass and reveal the app the user chose to enter. */
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

    private fun btnParams(topDp: Int) = LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT)
        .also { it.topMargin = dp(topDp) }

    private fun filledButton(label: String, onTap: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; setTextColor(0xFF241C16.toInt()); textSize = 16f
        typeface = Ui.sansMed(this@InterceptActivity)
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(glow) }
        setPadding(0, dp(15), 0, dp(15)); isClickable = true; isFocusable = true
        setOnClickListener { Ui.haptic(this); onTap() }
    }

    private fun textButton(label: String, onTap: () -> Unit) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; setTextColor(glow); textSize = 14f
        typeface = Ui.sans(this@InterceptActivity); setPadding(0, dp(12), 0, dp(12)); isClickable = true
        setOnClickListener { Ui.haptic(this); onTap() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_MODE = "mode"
        const val EXTRA_LEFT = "left"
        const val EXTRA_RULE = "rule"
        private const val PAUSE_SECONDS = 8
        private const val GRANT_MINUTES = 5
    }
}
