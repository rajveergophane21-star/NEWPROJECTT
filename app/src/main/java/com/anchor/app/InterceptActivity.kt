package com.anchor.app

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
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
    private var pauseTimer: CountDownTimer? = null
    private var breathAnimator: ValueAnimator? = null
    private var canProceed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        b = ActivityInterceptBinding.inflate(layoutInflater)
        setContentView(b.root)

        pkg = intent.getStringExtra(EXTRA_PKG) ?: run { finish(); return }
        mode = Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "BLOCK")
        minutesLeft = intent.getIntExtra(EXTRA_LEFT, -1)
        ruleName = intent.getStringExtra(EXTRA_RULE) ?: "Anchor"

        // Back = leave (the good outcome), never fall through to the app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave(logWin = true)
        })

        if (mode == Mode.BLOCK) renderBlock() else renderFriction()
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
        b.sub.text = "You set this boundary when you were thinking clearly. Trust that version of you."
        b.breathWrap.visibility = View.GONE
        b.btnPrimary.text = "Take me back"
        b.btnPrimary.setOnClickListener { leave(logWin = true) }
        b.btnSecondary.visibility = View.GONE
    }

    // ------------------------------------------------------------- friction
    private fun renderFriction() {
        b.eyebrow.text = "PAUSE"
        b.headline.text = "One breath first."
        b.appLine.text = "You reached for ${appName()}. Sit with that for a moment before you decide."
        b.sub.text = "Most urges crest and fall within a minute. Let this one pass."
        b.breathWrap.visibility = View.VISIBLE

        b.btnPrimary.text = "Not now — take me back"
        b.btnPrimary.setOnClickListener { leave(logWin = true) }

        b.btnSecondary.visibility = View.VISIBLE
        b.btnSecondary.text = "Wait ${PAUSE_SECONDS}s…"
        b.btnSecondary.isEnabled = false
        b.btnSecondary.setOnClickListener { if (canProceed) proceed() }

        startBreathing()
        pauseTimer = object : CountDownTimer(PAUSE_SECONDS * 1000L, 1000) {
            override fun onTick(ms: Long) {
                val s = (ms / 1000).toInt() + 1
                b.breathCount.text = s.toString()
                b.btnSecondary.text = "Wait ${s}s…"
            }
            override fun onFinish() {
                canProceed = true
                b.breathCount.text = ""
                b.btnSecondary.text = "Open ${appName()} anyway"
                b.btnSecondary.isEnabled = true
            }
        }.start()
    }

    /** Inhale/exhale orb that paces the breath during the pause. */
    private fun startBreathing() {
        breathAnimator = ValueAnimator.ofFloat(0.62f, 1f).apply {
            duration = 4000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val v = it.animatedValue as Float
                b.breathOrb.scaleX = v; b.breathOrb.scaleY = v
            }
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

    private fun untilTime(): String =
        LocalTime.now().plusMinutes(minutesLeft.toLong()).withSecond(0)
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

    override fun onDestroy() { cleanup(); super.onDestroy() }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_MODE = "mode"
        const val EXTRA_LEFT = "left"
        const val EXTRA_RULE = "rule"
        private const val PAUSE_SECONDS = 12
        private const val GRANT_MINUTES = 3
    }
}
