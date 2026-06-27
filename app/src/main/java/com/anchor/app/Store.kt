package com.anchor.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** A positive replacement habit. */
class Habit(
    val id: Long,
    var name: String,
    var anchor: String,
    val createdDay: Long,
    val checkins: MutableSet<Long> = mutableSetOf()
)

/**
 * Single source of truth, fully offline. Persisted to SharedPreferences as JSON.
 * No network permission is requested anywhere in the app.
 */
object Store {
    private const val PREFS = "anchor_store"
    private const val KEY = "data_v1"

    val rules = mutableListOf<Rule>()
    val habits = mutableListOf<Habit>()
    val interceptions = mutableListOf<Interception>()

    // Ad-hoc "Focus now" session (not persisted as a Rule).
    var focusUntil: Long = 0L
    var focusTotalMs: Long = 0L
    val focusPackages = mutableSetOf<String>()

    // Transient, in-memory: after a user chooses "open anyway", grant a short pass
    // so we don't re-intercept immediately. Cleared on process death.
    val grants = mutableMapOf<String, Long>()

    private var nextId = 1L
    private lateinit var ctx: Context

    fun init(context: Context) {
        if (this::ctx.isInitialized) return
        ctx = context.applicationContext
        load()
    }

    fun newId() = nextId++

    // ---- Rules ------------------------------------------------------------

    fun addRule(r: Rule) { rules.add(r); save() }
    fun deleteRule(r: Rule) { rules.remove(r); save() }
    fun ruleById(id: Long) = rules.firstOrNull { it.id == id }

    /** Whether a strict rule currently locks editing (active right now). */
    fun isLocked(r: Rule): Boolean {
        if (!r.strict) return false
        val (m, d) = nowMinDow()
        return r.activeNow(m, d)
    }

    /**
     * Decide what to do for [pkg] at the current moment.
     * Returns the governing Mode, or null if the app is free right now.
     */
    fun decisionFor(pkg: String): Decision? {
        // active focus session always blocks
        if (System.currentTimeMillis() < focusUntil && focusPackages.contains(pkg)) {
            return Decision(Mode.BLOCK, focusMinutesLeft(), "Focus session", -1L)
        }
        val (nowMin, dow) = nowMinDow()
        var best: Decision? = null
        for (r in rules) {
            if (!r.enabled || !r.packages.contains(pkg)) continue
            if (!r.activeNow(nowMin, dow)) continue
            val left = r.minutesLeft(nowMin, dow)
            // BLOCK wins over FRICTION if multiple rules apply
            if (best == null || (r.mode == Mode.BLOCK && best.mode == Mode.FRICTION)) {
                best = Decision(r.mode, left, r.name, r.id)
            }
        }
        return best
    }

    fun focusMinutesLeft(): Int =
        ((focusUntil - System.currentTimeMillis()) / 60000L).toInt().coerceAtLeast(0)

    fun startFocus(pkgs: Set<String>, minutes: Int) {
        focusPackages.clear(); focusPackages.addAll(pkgs)
        focusTotalMs = minutes * 60_000L
        focusUntil = System.currentTimeMillis() + focusTotalMs
    }
    fun focusActive() = System.currentTimeMillis() < focusUntil && focusPackages.isNotEmpty()
    fun focusRemainingMs() = (focusUntil - System.currentTimeMillis()).coerceAtLeast(0)
    fun stopFocus() { focusUntil = 0L; focusPackages.clear() }

    // ---- Interceptions ----------------------------------------------------

    fun logInterception(pkg: String, proceeded: Boolean) {
        interceptions.add(Interception(System.currentTimeMillis(), today(), pkg, proceeded))
        if (interceptions.size > 1000) interceptions.removeAt(0)
        save()
    }

    fun interceptionsToday() = interceptions.count { it.day == today() }
    fun resistedToday() = interceptions.count { it.day == today() && !it.proceeded }
    fun resistedTotal() = interceptions.count { !it.proceeded }
    fun interceptionsForDay(day: Long) = interceptions.count { it.day == day }

    /** Grant a short pass after "open anyway" so the user isn't trapped in a loop. */
    fun grantPass(pkg: String, minutes: Int) {
        grants[pkg] = System.currentTimeMillis() + minutes * 60_000L
    }
    fun hasPass(pkg: String): Boolean {
        val until = grants[pkg] ?: return false
        if (System.currentTimeMillis() > until) { grants.remove(pkg); return false }
        return true
    }

    // ---- Habits -----------------------------------------------------------

    fun addHabit(name: String, anchor: String): Habit {
        val h = Habit(newId(), name.trim(), anchor.trim(), today())
        habits.add(h); save(); return h
    }
    fun deleteHabit(h: Habit) { habits.remove(h); save() }
    fun isDoneToday(h: Habit) = h.checkins.contains(today())
    fun toggleToday(h: Habit) {
        val t = today()
        if (!h.checkins.add(t)) h.checkins.remove(t)
        save()
    }
    fun currentStreak(h: Habit): Int {
        val t = today()
        var day = if (h.checkins.contains(t)) t else t - 1
        var c = 0
        while (h.checkins.contains(day)) { c++; day-- }
        return c
    }
    fun bestStreak(h: Habit): Int {
        if (h.checkins.isEmpty()) return 0
        val s = h.checkins.sorted(); var best = 1; var run = 1
        for (i in 1 until s.size) { run = if (s[i] == s[i-1]+1) run+1 else 1; if (run>best) best=run }
        return best
    }
    fun heatDays(h: Habit, days: Int): BooleanArray {
        val t = today()
        return BooleanArray(days) { i -> h.checkins.contains(t - (days - 1 - i)) }
    }
    fun doneTodayCount() = habits.count { isDoneToday(it) }

    // ---- Time helpers -----------------------------------------------------

    fun today() = LocalDate.now().toEpochDay()
    private fun nowMinDow(): Pair<Int, Int> {
        val now = LocalTime.now()
        val dow = LocalDate.now().dayOfWeek.value   // 1=Mon..7=Sun
        return (now.hour * 60 + now.minute) to dow
    }

    // ---- Persistence ------------------------------------------------------

    private fun load() {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        try {
            val root = JSONObject(raw)
            nextId = root.optLong("nextId", 1L)

            rules.clear()
            val ra = root.optJSONArray("rules") ?: JSONArray()
            for (i in 0 until ra.length()) {
                val o = ra.getJSONObject(i)
                val pkgs = mutableSetOf<String>()
                o.optJSONArray("pkgs")?.let { for (j in 0 until it.length()) pkgs.add(it.getString(j)) }
                val windows = mutableListOf<TimeWindow>()
                o.optJSONArray("windows")?.let {
                    for (j in 0 until it.length()) {
                        val w = it.getJSONObject(j)
                        val days = mutableSetOf<Int>()
                        w.optJSONArray("days")?.let { da -> for (k in 0 until da.length()) days.add(da.getInt(k)) }
                        windows.add(TimeWindow(w.getInt("s"), w.getInt("e"), days))
                    }
                }
                rules.add(
                    Rule(
                        o.getLong("id"), o.getString("name"), pkgs, windows,
                        Mode.valueOf(o.optString("mode", "BLOCK")),
                        o.optBoolean("enabled", true),
                        o.optBoolean("strict", false)
                    )
                )
            }

            habits.clear()
            val ha = root.optJSONArray("habits") ?: JSONArray()
            for (i in 0 until ha.length()) {
                val o = ha.getJSONObject(i)
                val checks = mutableSetOf<Long>()
                o.optJSONArray("checkins")?.let { for (j in 0 until it.length()) checks.add(it.getLong(j)) }
                habits.add(Habit(o.getLong("id"), o.getString("name"), o.optString("anchor",""), o.optLong("created", today()), checks))
            }

            interceptions.clear()
            val ia = root.optJSONArray("intercepts") ?: JSONArray()
            for (i in 0 until ia.length()) {
                val o = ia.getJSONObject(i)
                interceptions.add(Interception(o.getLong("t"), o.getLong("day"), o.getString("pkg"), o.getBoolean("proc")))
            }
        } catch (_: Exception) { }
    }

    fun save() {
        val root = JSONObject()
        root.put("nextId", nextId)

        val ra = JSONArray()
        for (r in rules) {
            val o = JSONObject()
            o.put("id", r.id); o.put("name", r.name); o.put("mode", r.mode.name)
            o.put("enabled", r.enabled); o.put("strict", r.strict)
            val pa = JSONArray(); r.packages.forEach { pa.put(it) }; o.put("pkgs", pa)
            val wa = JSONArray()
            for (w in r.windows) {
                val wo = JSONObject(); wo.put("s", w.startMin); wo.put("e", w.endMin)
                val da = JSONArray(); w.days.sorted().forEach { da.put(it) }; wo.put("days", da)
                wa.put(wo)
            }
            o.put("windows", wa)
            ra.put(o)
        }
        root.put("rules", ra)

        val ha = JSONArray()
        for (h in habits) {
            val o = JSONObject()
            o.put("id", h.id); o.put("name", h.name); o.put("anchor", h.anchor); o.put("created", h.createdDay)
            val ca = JSONArray(); h.checkins.sorted().forEach { ca.put(it) }; o.put("checkins", ca)
            ha.put(o)
        }
        root.put("habits", ha)

        val ia = JSONArray()
        for (it in interceptions) {
            val o = JSONObject(); o.put("t", it.timeMillis); o.put("day", it.day); o.put("pkg", it.pkg); o.put("proc", it.proceeded)
            ia.put(o)
        }
        root.put("intercepts", ia)

        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, root.toString()).apply()
    }
}

/** Result of evaluating the current foreground app against all rules. */
class Decision(val mode: Mode, val minutesLeft: Int, val ruleName: String, val ruleId: Long)
