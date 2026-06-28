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

    // Self-improvement layer (all additive / backward-compatible).
    var identity: String = ""                        // "I'm someone who…"
    var identitySetDay: Long = 0L
    var reviewDow: Int = 7                            // weekly review day (7 = Sunday)
    val dayNotes = mutableMapOf<Long, DayNote>()      // epoch-day -> reflection
    val reviews = mutableListOf<WeeklyReview>()

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
            // BLOCK wins over FRICTION; among same-mode rules, the longer window wins
            // so the intercept reports the correct "closed until" time.
            if (best == null ||
                (r.mode == Mode.BLOCK && best.mode == Mode.FRICTION) ||
                (r.mode == best.mode && left > best.minutesLeft)) {
                best = Decision(r.mode, left, r.name, r.id, r.reason)
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
    fun firstUndoneToday(): Habit? = habits.firstOrNull { !isDoneToday(it) }

    // ---- Identity ---------------------------------------------------------

    fun updateIdentity(text: String) {
        identity = text.trim()
        if (identity.isNotEmpty() && identitySetDay == 0L) identitySetDay = today()
        save()
    }

    // ---- Daily reflection -------------------------------------------------

    fun dayNoteFor(day: Long): DayNote? = dayNotes[day]
    fun setDayNote(day: Long, alignment: Int, note: String) {
        dayNotes[day] = DayNote(day, alignment, note.trim()); save()
    }
    fun alignedDaysTotal() = dayNotes.values.count { it.alignment >= 1 }

    // ---- Weekly review ----------------------------------------------------

    /** Epoch-day of the Monday on or before [day]. */
    fun weekStartOf(day: Long): Long {
        val dow = LocalDate.ofEpochDay(day).dayOfWeek.value   // 1=Mon..7=Sun
        return day - (dow - 1)
    }
    fun reviewFor(weekStart: Long): WeeklyReview? = reviews.firstOrNull { it.weekStart == weekStart }
    fun lastReview(): WeeklyReview? =
        reviewFor(weekStartOf(today()) - 7) ?: reviews.maxByOrNull { it.weekStart }
    fun saveReview(weekStart: Long, noticed: String, focus: String, lastOutcome: Int) {
        val ex = reviewFor(weekStart)
        if (ex != null) { ex.noticed = noticed.trim(); ex.focus = focus.trim(); ex.lastFocusOutcome = lastOutcome }
        else reviews.add(WeeklyReview(weekStart, noticed.trim(), focus.trim(), lastOutcome))
        save()
    }
    /** Review is due once the chosen day has arrived, it isn't done, and there's something to review. */
    fun reviewDue(): Boolean {
        val ws = weekStartOf(today())
        if (reviewFor(ws) != null) return false
        if (interceptions.isEmpty() && dayNotes.isEmpty() && identity.isEmpty()) return false
        return LocalDate.now().dayOfWeek.value >= reviewDow
    }
    fun reviewsCount() = reviews.size

    // ---- Analytics over the intercept log --------------------------------

    fun interceptionsByHour(days: Int): IntArray {
        val cutoff = today() - (days - 1)
        val out = IntArray(24)
        for (it in interceptions) {
            if (it.day < cutoff) continue
            val hour = LocalTime.ofInstant(
                java.time.Instant.ofEpochMilli(it.timeMillis), java.time.ZoneId.systemDefault()
            ).hour
            out[hour]++
        }
        return out
    }
    fun peakInterceptionHour(days: Int): Int? {
        val byHour = interceptionsByHour(days)
        val max = byHour.maxOrNull() ?: return null
        return if (max == 0) null else byHour.indexOfFirst { it == max }
    }
    fun interceptionsByPackage(days: Int): Map<String, Int> {
        val cutoff = today() - (days - 1)
        val out = mutableMapOf<String, Int>()
        for (it in interceptions) { if (it.day >= cutoff) out[it.pkg] = (out[it.pkg] ?: 0) + 1 }
        return out
    }
    fun topInterceptedPackage(days: Int): String? =
        interceptionsByPackage(days).maxByOrNull { it.value }?.key
    fun resistedByDay(days: Int): IntArray {
        val start = today() - (days - 1)
        val out = IntArray(days)
        for (it in interceptions) {
            if (it.proceeded || it.day < start || it.day > today()) continue
            out[(it.day - start).toInt()]++
        }
        return out
    }
    fun resistRatio(days: Int): Float {
        val cutoff = today() - (days - 1)
        val window = interceptions.filter { it.day >= cutoff }
        if (window.isEmpty()) return -1f
        return window.count { !it.proceeded }.toFloat() / window.size
    }
    /** Consecutive days (ending today or yesterday) with at least one resisted urge. */
    fun resistedMomentum(): Int {
        val days = interceptions.filter { !it.proceeded }.map { it.day }.toSet()
        val t = today()
        var day = if (days.contains(t)) t else t - 1
        var c = 0
        while (days.contains(day)) { c++; day-- }
        return c
    }

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
                        o.optBoolean("strict", false),
                        o.optString("reason", "")
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

            identity = root.optString("identity", "")
            identitySetDay = root.optLong("identitySet", 0L)
            reviewDow = root.optInt("reviewDow", 7)

            dayNotes.clear()
            val dna = root.optJSONArray("dayNotes") ?: JSONArray()
            for (i in 0 until dna.length()) {
                val o = dna.getJSONObject(i)
                val d = o.getLong("day")
                dayNotes[d] = DayNote(d, o.optInt("a", 0), o.optString("n", ""))
            }

            reviews.clear()
            val rva = root.optJSONArray("reviews") ?: JSONArray()
            for (i in 0 until rva.length()) {
                val o = rva.getJSONObject(i)
                reviews.add(WeeklyReview(o.getLong("week"), o.optString("noticed", ""), o.optString("focus", ""), o.optInt("outcome", -1)))
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
            o.put("enabled", r.enabled); o.put("strict", r.strict); o.put("reason", r.reason)
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

        root.put("identity", identity)
        root.put("identitySet", identitySetDay)
        root.put("reviewDow", reviewDow)

        val dna = JSONArray()
        for (n in dayNotes.values) {
            val o = JSONObject(); o.put("day", n.day); o.put("a", n.alignment); o.put("n", n.note); dna.put(o)
        }
        root.put("dayNotes", dna)

        val rva = JSONArray()
        for (w in reviews) {
            val o = JSONObject()
            o.put("week", w.weekStart); o.put("noticed", w.noticed); o.put("focus", w.focus); o.put("outcome", w.lastFocusOutcome)
            rva.put(o)
        }
        root.put("reviews", rva)

        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, root.toString()).apply()
    }
}

/** Result of evaluating the current foreground app against all rules. */
class Decision(val mode: Mode, val minutesLeft: Int, val ruleName: String, val ruleId: Long, val reason: String = "")
