package com.momentum.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A positive habit the user is building. */
class Habit(
    val id: Long,
    var name: String,
    var anchor: String,        // "After I ___" implementation-intention cue
    var color: Int,
    val createdDay: Long,      // LocalDate.toEpochDay()
    val checkins: MutableSet<Long> = mutableSetOf()  // set of epoch-days completed
)

/** A completed (or abandoned) detox focus session. */
class Session(
    val day: Long,             // epoch-day it happened
    val minutes: Int,          // minutes actually focused
    val completed: Boolean
)

/**
 * Offline data layer. Everything is persisted to SharedPreferences as JSON,
 * so the app works with no network and nothing ever leaves the device.
 */
object Store {
    private const val PREFS = "momentum_store"
    private const val KEY = "data_v1"

    val habits = mutableListOf<Habit>()
    val sessions = mutableListOf<Session>()
    var urgesSurfed = 0
    private var nextId = 1L

    private lateinit var ctx: Context

    fun init(context: Context) {
        ctx = context.applicationContext
        load()
    }

    // ---- Habit operations -------------------------------------------------

    fun addHabit(name: String, anchor: String, color: Int): Habit {
        val h = Habit(nextId++, name.trim(), anchor.trim(), color, today())
        habits.add(h)
        save()
        return h
    }

    fun deleteHabit(h: Habit) { habits.remove(h); save() }

    fun isDoneToday(h: Habit) = h.checkins.contains(today())

    fun toggleToday(h: Habit) {
        val t = today()
        if (!h.checkins.add(t)) h.checkins.remove(t)
        save()
    }

    /** Consecutive completed days ending today (or yesterday if today is still open). */
    fun currentStreak(h: Habit): Int {
        val t = today()
        var day = if (h.checkins.contains(t)) t else t - 1
        var count = 0
        while (h.checkins.contains(day)) { count++; day-- }
        return count
    }

    fun bestStreak(h: Habit): Int {
        if (h.checkins.isEmpty()) return 0
        val sorted = h.checkins.sorted()
        var best = 1; var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    fun bestStreakAll(): Int = habits.maxOfOrNull { bestStreak(it) } ?: 0
    fun totalCheckins(): Int = habits.sumOf { it.checkins.size }
    fun doneTodayCount(): Int = habits.count { isDoneToday(it) }

    /** Booleans oldest->newest for the last [days] days, last entry = today. */
    fun heatDays(h: Habit, days: Int): BooleanArray {
        val t = today()
        return BooleanArray(days) { i ->
            val day = t - (days - 1 - i)
            h.checkins.contains(day)
        }
    }

    // ---- Detox operations -------------------------------------------------

    fun addSession(minutes: Int, completed: Boolean) {
        sessions.add(Session(today(), minutes, completed))
        save()
    }

    fun addUrgeSurfed() { urgesSurfed++; save() }

    fun totalFocusMinutes() = sessions.sumOf { it.minutes }
    fun sessionsToday() = sessions.count { it.day == today() }
    fun longestSession() = sessions.maxOfOrNull { it.minutes } ?: 0

    // ---- Persistence ------------------------------------------------------

    private fun today() = LocalDate.now().toEpochDay()

    private fun load() {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        try {
            val root = JSONObject(raw)
            nextId = root.optLong("nextId", 1L)
            urgesSurfed = root.optInt("urges", 0)
            habits.clear()
            val ha = root.optJSONArray("habits") ?: JSONArray()
            for (i in 0 until ha.length()) {
                val o = ha.getJSONObject(i)
                val checks = mutableSetOf<Long>()
                val ca = o.optJSONArray("checkins") ?: JSONArray()
                for (j in 0 until ca.length()) checks.add(ca.getLong(j))
                habits.add(
                    Habit(
                        o.getLong("id"),
                        o.getString("name"),
                        o.optString("anchor", ""),
                        o.getInt("color"),
                        o.optLong("created", today()),
                        checks
                    )
                )
            }
            sessions.clear()
            val sa = root.optJSONArray("sessions") ?: JSONArray()
            for (i in 0 until sa.length()) {
                val o = sa.getJSONObject(i)
                sessions.add(Session(o.getLong("day"), o.getInt("min"), o.getBoolean("done")))
            }
        } catch (_: Exception) { /* corrupt data — start fresh */ }
    }

    private fun save() {
        val root = JSONObject()
        root.put("nextId", nextId)
        root.put("urges", urgesSurfed)
        val ha = JSONArray()
        for (h in habits) {
            val o = JSONObject()
            o.put("id", h.id)
            o.put("name", h.name)
            o.put("anchor", h.anchor)
            o.put("color", h.color)
            o.put("created", h.createdDay)
            val ca = JSONArray()
            h.checkins.sorted().forEach { ca.put(it) }
            o.put("checkins", ca)
            ha.put(o)
        }
        root.put("habits", ha)
        val sa = JSONArray()
        for (s in sessions) {
            val o = JSONObject()
            o.put("day", s.day); o.put("min", s.minutes); o.put("done", s.completed)
            sa.put(o)
        }
        root.put("sessions", sa)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, root.toString()).apply()
    }

    val palette = intArrayOf(
        0xFF4F8CFF.toInt(), 0xFF2EA043.toInt(), 0xFFFF7A1A.toInt(),
        0xFFE5534B.toInt(), 0xFFA371F7.toInt(), 0xFF1FB8CD.toInt(), 0xFFF2C744.toInt()
    )
}
