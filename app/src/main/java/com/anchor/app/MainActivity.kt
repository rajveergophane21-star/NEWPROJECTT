package com.anchor.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.anchor.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.bottomNav.setOnItemSelectedListener {
            Ui.haptic(b.bottomNav)
            when (it.itemId) {
                R.id.nav_today -> show(TodayFragment())
                R.id.nav_shield -> show(ShieldFragment())
                R.id.nav_habits -> show(HabitsFragment())
                R.id.nav_insights -> show(InsightsFragment())
                else -> false
            }
            true
        }
        if (savedInstanceState == null) {
            b.bottomNav.selectedItemId = R.id.nav_today
        }

        // First run with no permissions → walk the user through setup.
        if (!Perms.coreReady(this) && !onboardedFlag()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Keep the shield running whenever it's permitted and something is enabled.
        if (Perms.coreReady(this) && (Store.rules.any { it.enabled } || Store.focusActive())) {
            MonitorService.start(this)
        }
        // refresh whatever tab is visible
        (supportFragmentManager.findFragmentById(R.id.container) as? Refreshable)?.refresh()
    }

    private fun show(f: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.frag_enter, R.anim.frag_exit)
            .replace(R.id.container, f)
            .commit()
        return true
    }

    private fun onboardedFlag(): Boolean =
        getSharedPreferences("anchor_flags", MODE_PRIVATE).getBoolean("onboarded", false)
}

/** Fragments implement this so the host can refresh them on resume. */
interface Refreshable { fun refresh() }
