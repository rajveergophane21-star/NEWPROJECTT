package com.anchor.app

/** How a rule steps in when a blocked app is opened. */
enum class Mode { BLOCK, FRICTION }

/**
 * A recurring schedule slot. [days] are java.time DayOfWeek values (1=Mon … 7=Sun).
 * Times are minutes from midnight; a window that ends at/<= its start wraps past midnight.
 */
class TimeWindow(
    var startMin: Int,
    var endMin: Int,
    var days: MutableSet<Int>
) {
    val overnight: Boolean get() = endMin <= startMin
    val allDay: Boolean get() = startMin == 0 && endMin >= 1440 && days.size == 7

    fun activeAt(nowMin: Int, dow: Int): Boolean {
        if (days.isEmpty() || startMin == endMin) return false
        if (!overnight) return days.contains(dow) && nowMin >= startMin && nowMin < endMin
        val prev = if (dow == 1) 7 else dow - 1
        return (days.contains(dow) && nowMin >= startMin) || (days.contains(prev) && nowMin < endMin)
    }

    fun minutesLeft(nowMin: Int): Int = when {
        !overnight -> endMin - nowMin
        nowMin >= startMin -> (endMin + 1440) - nowMin
        else -> endMin - nowMin
    }

    fun label(): String {
        if (allDay) return "All day, every day"
        return "${fmt(startMin)}–${fmt(endMin)} · ${daysLabel()}"
    }

    private fun daysLabel(): String {
        if (days.size == 7) return "Every day"
        if (days == setOf(1, 2, 3, 4, 5)) return "Weekdays"
        if (days == setOf(6, 7)) return "Weekends"
        val n = arrayOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return (1..7).filter { days.contains(it) }.joinToString(" ") { n[it] }
    }

    companion object {
        fun fmt(min: Int): String {
            val m = min.coerceIn(0, 1440)
            return "%02d:%02d".format((m / 60) % 24, m % 60)
        }
    }
}

/** A blocking rule: a set of apps + a schedule + an intervention mode. */
class Rule(
    val id: Long,
    var name: String,
    val packages: MutableSet<String>,
    val windows: MutableList<TimeWindow>,
    var mode: Mode,
    var enabled: Boolean
) {
    fun activeNow(nowMin: Int, dow: Int) = enabled && windows.any { it.activeAt(nowMin, dow) }

    /** Longest minutes remaining across the active windows, or -1 if none active. */
    fun minutesLeft(nowMin: Int, dow: Int): Int {
        val active = windows.filter { it.activeAt(nowMin, dow) }
        return if (active.isEmpty()) -1 else active.maxOf { it.minutesLeft(nowMin) }
    }
}

/** A simple daily habit. [checkins] are epoch-days the user marked it done. */
class Habit(val id: Long, var name: String, val checkins: MutableSet<Long> = mutableSetOf())
