package com.anchor.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.anchor.app.databinding.ActivityInterceptBinding
import java.time.LocalTime

/**
 * The wall, or the pause. Shown over a blocked app.
 *
 * BLOCK mode: a calm, final stop — the only way out is to leave.
 * FRICTION mode: a mandatory breath, then an honest choice. Friction reliably
 * stops the impulse in the moment; pairing it with a real decision is what the
 * evidence supports (per-instance abandonment, not magic willpower).
 */
class InterceptActivity : AppCompatActivity() {

    private lateinit var b: ActivityInterceptBinding
    private lateinit var pkg: String
    private var mode = Mode.BLOCK
    private var minutesLeft = -1
    private var ruleName = ""
    private var reason = ""
    private var pauseTimer: CountDownTimer? = null
    private var breathAnimator: ValueAnimator? = null
    private var frictionRing: RingView? = null
    private var canProceed = false
    private var inhale = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        b = ActivityInterceptBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Arrive deliberately: one quiet fade-up and a single grounding haptic.
        b.root.alpha = 0f
        b.root.animate().alpha(1f).setDuration(200).start()
        b.root.post { b.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
        styleDark()

        pkg = intent.getStringExtra(EXTRA_PKG) ?: run { finish(); return }
        mode = Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "BLOCK")
        minutesLeft = intent.getIntExtra(EXTRA_LEFT, -1)
        ruleName = intent.getStringExtra(EXTRA_RULE) ?: "Margin"
        reason = intent.getStringExtra(EXTRA_REASON) ?: ""

        // Back = leave (the good outcome), never fall through to the app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave(logWin = true)
        })

        if (mode == Mode.BLOCK) renderBlock() else renderFriction()
    }

    /** Editorial dark styling: serif + mono on warm-black paper. */
    private fun styleDark() {
        val cream = 0xFFFFCD75.toInt(); val ink = 0xFF1A1C2C.toInt(); val soft = 0xFF94B0C2.toInt()
        b.mark.visibility = View.GONE
        b.eyebrow.typeface = Ui.monoMed(this); b.eyebrow.setTextColor(Ui.ACC_GLOW)
        b.headline.typeface = Ui.serif(this); b.headline.setTextColor(Ui.DARK_TEXT); b.headline.textSize = 32f
        b.appLine.typeface = Ui.sans(this); b.appLine.setTextColor(soft)
        b.sub.typeface = Ui.serifItalic(this); b.sub.setTextColor(cream); b.sub.textSize = 19f
        b.breathCount.typeface = Ui.serif(this); b.breathCount.setTextColor(Ui.DARK_TEXT)
        b.breathOrb.backgroundTintList = ColorStateList.valueOf(Ui.SAGE)
        b.btnPrimary.backgroundTintList = ColorStateList.valueOf(cream)
        b.btnPrimary.setTextColor(ink); b.btnPrimary.typeface = Ui.sans(this)
        b.btnSecondary.setTextColor(Ui.ACC_GLOW); b.btnSecondary.typeface = Ui.sans(this)
        b.btnReplace.setTextColor(Ui.DARK_TEXT); b.btnReplace.typeface = Ui.sans(this)
        b.btnReplace.strokeColor = ColorStateList.valueOf(0x33FFFFFF)
    }

    private fun appName(): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { "this app" }

    // ---------------------------------------------------------------- block
    private fun renderBlock() {
        b.eyebrow.text = ruleName.uppercase()
        b.headline.text = "Not now."
        b.appLine.text = buildString {
            append(appName())
            if (minutesLeft > 0) append(" is closed until ${untilTime()}.")
            else append(" is closed right now.")
        }
        b.sub.text = whyLine("You set this boundary when you were thinking clearly. Trust that version of you.")
        b.breathWrap.visibility = View.GONE
        b.btnPrimary.text = "Take me back"
        b.btnPrimary.setOnClickListener { leave(logWin = true) }
        b.btnSecondary.visibility = View.GONE
        setupReplacement()
    }

    /** Recall the user's own words at the moment of choice (values affirmation). */
    private fun whyLine(default: String): String = when {
        reason.isNotEmpty() -> "You told yourself: “$reason”"
        Store.identity.isNotEmpty() -> "Remember — you're becoming ${Store.identity}."
        else -> default
    }

    /** Offer the replacement behaviour: doing it instead is the real win. */
    private fun setupReplacement() {
        val h = Store.firstUndoneToday()
        if (h == null) { b.btnReplace.visibility = View.GONE; return }
        b.btnReplace.visibility = View.VISIBLE
        b.btnReplace.text = "Instead, ${h.name} →"
        b.btnReplace.setOnClickListener {
            Ui.haptic(b.btnReplace)
            Store.toggleToday(h)        // mark the replacement done
            leave(logWin = true)        // a win, and back to home
        }
    }

    // ------------------------------------------------------------- friction
    private fun renderFriction() {
        b.eyebrow.text = "PAUSE"
        b.headline.text = "One breath first."
        b.appLine.text = "You reached for ${appName()}. Sit with that for a moment before you decide."
        b.sub.text = whyLine("Most urges crest and fall within a minute. Let this one pass.")
        b.breathWrap.visibility = View.VISIBLE
        setupReplacement()

        // A ring fills behind the breathing orb as the pause elapses.
        frictionRing = RingView(this).apply {
            setActiveColor(Ui.SAGE)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        b.breathWrap.addView(frictionRing, 0)

        b.btnPrimary.text = "Not now — take me back"
        b.btnPrimary.setOnClickListener { leave(logWin = true) }

        b.btnSecondary.visibility = View.VISIBLE
        b.btnSecondary.text = "Wait ${PAUSE_SECONDS}s…"
        b.btnSecondary.isEnabled = false
        b.btnSecondary.alpha = 0.5f
        b.btnSecondary.setOnClickListener { if (canProceed) proceed() }

        startBreathing()
        val totalMs = PAUSE_SECONDS * 1000L
        pauseTimer = object : CountDownTimer(totalMs, 250) {
            override fun onTick(ms: Long) {
                frictionRing?.setProgress((totalMs - ms).toFloat() / totalMs)
                val s = (ms / 1000).toInt() + 1
                b.btnSecondary.text = "Wait ${s}s…"
            }
            override fun onFinish() {
                canProceed = true
                frictionRing?.setProgress(1f)
                b.btnSecondary.text = "Open ${appName()} anyway"
                b.btnSecondary.isEnabled = true
                b.btnSecondary.animate().alpha(1f).setDuration(220).start()
            }
        }.start()
    }

    /** Inhale/exhale orb that paces the breath during the pause; centre shows the phase. */
    private fun startBreathing() {
        b.breathCount.text = "In"
        breathAnimator = ValueAnimator.ofFloat(0.62f, 1f).apply {
            duration = 4000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val v = it.animatedValue as Float
                b.breathOrb.scaleX = v; b.breathOrb.scaleY = v
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: Animator) {
                    inhale = !inhale
                    b.breathCount.text = if (inhale) "In" else "Out"
                }
            })
            start()
        }
    }

    // ----------------------------------------------------------------- exits
    private fun leave(logWin: Boolean) {
        if (logWin) Store.logInterception(pkg, proceeded = false)
        Enforcer.reset()
        cleanup()
        // Send the user to the home screen rather than back into the blocked app.
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }

    private fun proceed() {
        Store.logInterception(pkg, proceeded = true)
        Store.grantPass(pkg, GRANT_MINUTES)   // short pass so we don't loop
        Enforcer.reset()
        cleanup()
        // We pressed HOME to get here, so re-open the app the user chose to enter.
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
        finish()
    }

    private fun cleanup() {
        pauseTimer?.cancel(); pauseTimer = null
        breathAnimator?.cancel(); breathAnimator = null
    }

    private fun untilTime(): String {
        val now = LocalTime.now()
        val hhmm = now.plusMinutes(minutesLeft.toLong()).withSecond(0)
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        val crossesMidnight = now.toSecondOfDay() + minutesLeft * 60 >= 86400
        return if (crossesMidnight) "tomorrow $hhmm" else hhmm
    }

    override fun onDestroy() { cleanup(); super.onDestroy() }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_MODE = "mode"
        const val EXTRA_LEFT = "left"
        const val EXTRA_RULE = "rule"
        const val EXTRA_REASON = "reason"
        private const val PAUSE_SECONDS = 12
        private const val GRANT_MINUTES = 3
    }
}
