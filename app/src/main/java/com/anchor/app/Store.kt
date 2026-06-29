package com.anchor.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** The governing decision for an app at a moment: which mode, how long left, which rule. */
class Decision(val mode: Mode, val minutesLeft: Int, val ruleName: String)

/** Single source of truth, fully offline. Persisted to SharedPreferences as JSON. */
object Store {
    private const val PREFS = "margin_store"
    private const val KEY = "data_v2"

    val rules = mutableListOf<Rule>()
    val habits = mutableListOf<Habit>()

    // After "open anyway", a short in-memory pass so we don't re-intercept immediately.
    private val grants = mutableMapOf<String, Long>()

    // Reels watched today, per package; reset when the day rolls over.
    private val reelCounts = mutableMapOf<String, Int>()
    private var reelDay = 0L

    private var nextId = 1L
    private lateinit var ctx: Context

    fun init(c: Context) {
        if (this::ctx.isInitialized) return
        ctx = c.applicationContext
        load()
    }

    fun newId() = nextId++

    // ---- Rules ------------------------------------------------------------
    fun addRule(r: Rule) { rules.add(r); save() }
    fun deleteRule(r: Rule) { rules.remove(r); save() }
    fun ruleById(id: Long) = rules.firstOrNull { it.id == id }

    /** A committed rule can't be disabled or edited while its schedule is active. */
    fun isLocked(r: Rule): Boolean {
        if (!r.strict) return false
        val now = LocalTime.now(); val nowMin = now.hour * 60 + now.minute
        return r.activeNow(nowMin, LocalDate.now().dayOfWeek.value)
    }

    /**
     * Make [rule] the sole owner of its apps among rules of the SAME kind, so an older rule can't
     * override its mode — EXCEPT a currently-locked (committed & active) rule, which keeps its apps
     * so a new permissive rule can't quietly strip a commitment lock's coverage. An APP rule and a
     * FEED rule may both target the same package (block the whole app vs limit only its feed).
     */
    fun claimPackages(rule: Rule) {
        rules.forEach { if (it !== rule && it.kind == rule.kind && !isLocked(it)) it.packages.removeAll(rule.packages) }
        rules.removeAll { it !== rule && it.packages.isEmpty() && !isLocked(it) }
        save()
    }

    /** Decide what to do for the whole app [pkg] right now. BLOCK beats FRICTION; longer window wins. */
    fun decisionFor(pkg: String): Decision? {
        val now = LocalTime.now(); val nowMin = now.hour * 60 + now.minute
        val dow = LocalDate.now().dayOfWeek.value
        var best: Decision? = null
        for (r in rules) {
            if (r.kind != Kind.APP) continue
            if (!r.enabled || !r.packages.contains(pkg)) continue
            if (!r.activeNow(nowMin, dow)) continue
            val left = r.minutesLeft(nowMin, dow)
            if (best == null ||
                (r.mode == Mode.BLOCK && best.mode == Mode.FRICTION) ||
                (r.mode == best.mode && left > best.minutesLeft)) {
                best = Decision(r.mode, left, r.name)
            }
        }
        return best
    }

    // ---- Short-form feeds (reels) ----------------------------------------
    /** The active FEED rule covering [pkg] right now, or null. */
    fun feedRuleActive(pkg: String): Rule? {
        val now = LocalTime.now(); val nowMin = now.hour * 60 + now.minute
        val dow = LocalDate.now().dayOfWeek.value
        return rules.firstOrNull { it.kind == Kind.FEED && it.enabled && it.packages.contains(pkg) && it.activeNow(nowMin, dow) }
    }

    /** Reels watched today on [pkg]. */
    fun reelCountToday(pkg: String): Int { rollReelDay(); return reelCounts[pkg] ?: 0 }

    /** Count one more reel on [pkg]; returns the new total. */
    fun incReel(pkg: String): Int {
        rollReelDay()
        val n = (reelCounts[pkg] ?: 0) + 1
        reelCounts[pkg] = n; save(); return n
    }

    /** The daily reel allowance for [pkg]'s active feed rule, or null if none/unlimited. */
    fun reelLimitFor(pkg: String): Int? = feedRuleActive(pkg)?.reelLimit

    /**
     * What to do on [pkg]'s feed right now. Returns a Decision when the feed should be intervened on:
     * either an always-on scheduled feed block (no limit set) or the daily reel limit is reached.
     * Returns null when the feed is merely being counted (under limit).
     */
    fun feedDecision(pkg: String): Decision? {
        val r = feedRuleActive(pkg) ?: return null
        val now = LocalTime.now(); val nowMin = now.hour * 60 + now.minute
        val dow = LocalDate.now().dayOfWeek.value
        val left = r.minutesLeft(nowMin, dow)
        val limit = r.reelLimit
        if (limit == null || reelCountToday(pkg) >= limit) return Decision(r.mode, left, r.name)
        return null
    }

    private fun rollReelDay() {
        val t = today()
        if (reelDay != t) { reelDay = t; reelCounts.clear(); save() }
    }

    fun grantPass(pkg: String, minutes: Int) { grants[pkg] = System.currentTimeMillis() + minutes * 60_000L; save() }
    fun hasPass(pkg: String): Boolean {
        val until = grants[pkg] ?: return false
        if (System.currentTimeMillis() > until) { grants.remove(pkg); return false }
        return true
    }

    fun anyEnabled() = rules.any { it.enabled }

    // ---- Habits -----------------------------------------------------------
    fun addHabit(name: String): Habit { val h = Habit(newId(), name.trim()); habits.add(h); save(); return h }
    fun deleteHabit(h: Habit) { habits.remove(h); save() }
    fun renameHabit(h: Habit, name: String) { h.name = name.trim(); save() }
    fun isDoneToday(h: Habit) = h.checkins.contains(today())
    fun toggleToday(h: Habit) { val t = today(); if (!h.checkins.add(t)) h.checkins.remove(t); save() }
    fun currentStreak(h: Habit): Int {
        val t = today(); var d = if (h.checkins.contains(t)) t else t - 1; var c = 0
        while (h.checkins.contains(d)) { c++; d-- }
        return c
    }
    fun bestStreak(h: Habit): Int {
        if (h.checkins.isEmpty()) return 0
        val s = h.checkins.sorted(); var best = 1; var run = 1
        for (i in 1 until s.size) { run = if (s[i] == s[i - 1] + 1) run + 1 else 1; if (run > best) best = run }
        return best
    }
    /** Last [days] days as a done/not-done array (index 0 = oldest, last = today). */
    fun heatDays(h: Habit, days: Int): BooleanArray {
        val t = today()
        return BooleanArray(days) { i -> h.checkins.contains(t - (days - 1 - i)) }
    }
    fun setReminder(h: Habit, minutes: Int?) { h.reminderMinutes = minutes; save() }

    fun today(): Long = LocalDate.now().toEpochDay()

    // ---- Persistence ------------------------------------------------------
    private fun load() {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        try {
            val root = JSONObject(raw)
            nextId = root.optLong("nextId", 1L)
            rules.clear()
            val ra = root.optJSONArray("rules") ?: JSONArray()
            for (i in 0 until ra.length()) {
                try {
                    val o = ra.getJSONObject(i)
                    val pkgs = mutableSetOf<String>()
                    o.optJSONArray("pkgs")?.let { for (j in 0 until it.length()) pkgs.add(it.getString(j)) }
                    val ws = mutableListOf<TimeWindow>()
                    o.optJSONArray("windows")?.let {
                        for (j in 0 until it.length()) {
                            val w = it.getJSONObject(j)
                            val days = mutableSetOf<Int>()
                            w.optJSONArray("days")?.let { da -> for (k in 0 until da.length()) days.add(da.getInt(k)) }
                            ws.add(TimeWindow(w.getInt("s"), w.getInt("e"), days))
                        }
                    }
                    val mode = runCatching { Mode.valueOf(o.optString("mode", "BLOCK")) }.getOrDefault(Mode.BLOCK)
                    val kind = runCatching { Kind.valueOf(o.optString("kind", "APP")) }.getOrDefault(Kind.APP)
                    val limit = if (o.has("reelLimit") && !o.isNull("reelLimit")) o.getInt("reelLimit") else null
                    rules.add(Rule(o.getLong("id"), o.getString("name"), pkgs, ws, mode, o.optBoolean("enabled", true), o.optBoolean("strict", false), kind, limit))
                } catch (_: Exception) {}   // skip only the bad rule
            }
            habits.clear()
            val ha = root.optJSONArray("habits") ?: JSONArray()
            for (i in 0 until ha.length()) {
                try {
                    val o = ha.getJSONObject(i)
                    val ck = mutableSetOf<Long>()
                    o.optJSONArray("checkins")?.let { for (j in 0 until it.length()) ck.add(it.getLong(j)) }
                    val rem = if (o.has("reminder")) o.getInt("reminder") else null
                    habits.add(Habit(o.getLong("id"), o.getString("name"), ck, rem))
                } catch (_: Exception) {}   // skip only the bad habit
            }
            grants.clear()
            root.optJSONObject("grants")?.let {
                val nowMs = System.currentTimeMillis(); val keys = it.keys()
                while (keys.hasNext()) { val k = keys.next(); val u = it.optLong(k); if (u > nowMs) grants[k] = u }
            }
            reelCounts.clear()
            reelDay = root.optLong("reelDay", 0L)
            if (reelDay == today()) {
                root.optJSONObject("reelCounts")?.let {
                    val keys = it.keys()
                    while (keys.hasNext()) { val k = keys.next(); reelCounts[k] = it.optInt(k) }
                }
            } else reelDay = 0L
        } catch (_: Exception) {}
        nextId = maxOf(nextId, ((rules.map { it.id } + habits.map { it.id }).maxOrNull() ?: 0L) + 1L)
    }

    fun save() {
        val root = JSONObject(); root.put("nextId", nextId)
        val ra = JSONArray()
        for (r in rules) {
            val o = JSONObject()
            o.put("id", r.id); o.put("name", r.name); o.put("mode", r.mode.name); o.put("enabled", r.enabled); o.put("strict", r.strict)
            o.put("kind", r.kind.name); r.reelLimit?.let { o.put("reelLimit", it) }
            val pa = JSONArray(); r.packages.forEach { pa.put(it) }; o.put("pkgs", pa)
            val wa = JSONArray()
            for (w in r.windows) {
                val wo = JSONObject(); wo.put("s", w.startMin); wo.put("e", w.endMin)
                val da = JSONArray(); w.days.sorted().forEach { da.put(it) }; wo.put("days", da); wa.put(wo)
            }
            o.put("windows", wa); ra.put(o)
        }
        root.put("rules", ra)
        val ha = JSONArray()
        for (h in habits) {
            val o = JSONObject(); o.put("id", h.id); o.put("name", h.name)
            val ca = JSONArray(); h.checkins.sorted().forEach { ca.put(it) }; o.put("checkins", ca)
            h.reminderMinutes?.let { o.put("reminder", it) }
            ha.put(o)
        }
        root.put("habits", ha)
        val ga = JSONObject(); val nowMs = System.currentTimeMillis()
        for ((pkg, until) in grants) if (until > nowMs) ga.put(pkg, until)
        root.put("grants", ga)
        root.put("reelDay", reelDay)
        val rc = JSONObject(); for ((pkg, n) in reelCounts) rc.put(pkg, n)
        root.put("reelCounts", rc)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, root.toString()).apply()
    }
}
