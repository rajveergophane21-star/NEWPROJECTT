package com.anchor.app

/** How a rule intervenes when a blocked app is opened. */
enum class Mode { BLOCK, FRICTION }

/** A recurring time slot, e.g. 09:00–17:00 weekdays, or 22:00–07:00 (overnight). */
class TimeWindow(
    var startMin: Int,            // minutes from midnight, 0..1439
    var endMin: Int,              // exclusive, 1..1440
    var days: MutableSet<Int>     // java.time DayOfWeek values: 1=Mon … 7=Sun (the start day)
) {
    val overnight: Boolean get() = endMin <= startMin

    fun activeAt(nowMin: Int, dow: Int): Boolean {
        if (!overnight) return days.contains(dow) && nowMin >= startMin && nowMin < endMin
        // Wraps midnight: the start day owns the evening; the morning belongs to the next day.
        val prev = if (dow == 1) 7 else dow - 1
        return (days.contains(dow) && nowMin >= startMin) || (days.contains(prev) && nowMin < endMin)
    }

    fun label(): String {
        return "${fmt(startMin)}–${fmt(endMin)}  ·  ${daysLabel()}"
    }

    private fun daysLabel(): String {
        if (days.size == 7) return "Every day"
        if (days == setOf(1, 2, 3, 4, 5)) return "Weekdays"
        if (days == setOf(6, 7)) return "Weekends"
        val names = arrayOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return (1..7).filter { days.contains(it) }.joinToString(" ") { names[it] }
    }

    companion object {
        fun fmt(min: Int): String {
            val m = min.coerceIn(0, 1440)
            return "%02d:%02d".format((m / 60) % 24, m % 60)
        }
    }
}

/** A user-defined blocking rule: a set of apps + schedule + intervention mode. */
class Rule(
    val id: Long,
    var name: String,
    val packages: MutableSet<String>,
    val windows: MutableList<TimeWindow>,
    var mode: Mode,
    var enabled: Boolean,
    var strict: Boolean,             // commitment device: can't be turned off while active
    var reason: String = ""          // optional personal "why", recalled at the intercept
) {
    fun activeNow(nowMin: Int, dow: Int): Boolean =
        enabled && windows.any { it.activeAt(nowMin, dow) }

    /** Minutes remaining — the longest across all currently-active windows, or -1 if none. */
    fun minutesLeft(nowMin: Int, dow: Int): Int {
        val active = windows.filter { it.activeAt(nowMin, dow) }
        if (active.isEmpty()) return -1
        return active.maxOf { w ->
            when {
                !w.overnight -> w.endMin - nowMin
                nowMin >= w.startMin -> (w.endMin + 1440) - nowMin   // evening portion, ends next day
                else -> w.endMin - nowMin                            // early-morning portion
            }
        }
    }
}

/** One end-of-day reflection: how close the day felt to who you're becoming. */
class DayNote(
    val day: Long,            // epoch-day, unique
    var alignment: Int,       // 0 = drifted, 1 = some of the day, 2 = that was me
    var note: String = ""
)

/** A weekly review: the mirror, a reflection, and the focus chosen for next week. */
class WeeklyReview(
    val weekStart: Long,             // epoch-day of the Monday of that week
    var noticed: String = "",        // what you noticed about yourself
    var focus: String = "",          // one thing to lean into next week
    var lastFocusOutcome: Int = -1   // -1 unset, else 0/1/2 against the prior week's focus
)

/** A logged moment where Margin stepped in. */
class Interception(
    val timeMillis: Long,
    val day: Long,                   // epoch-day
    val pkg: String,
    val proceeded: Boolean           // false = user backed off (a "win")
)
