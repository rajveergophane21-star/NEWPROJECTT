package com.anchor.app

/** How a rule intervenes when a blocked app is opened. */
enum class Mode { BLOCK, FRICTION }

/** A recurring time slot, e.g. 09:00–17:00 on weekdays. Same-day only (start < end). */
class TimeWindow(
    var startMin: Int,            // minutes from midnight, 0..1439
    var endMin: Int,              // exclusive, 1..1440
    var days: MutableSet<Int>     // java.time DayOfWeek values: 1=Mon … 7=Sun
) {
    fun activeAt(nowMin: Int, dow: Int): Boolean =
        days.contains(dow) && nowMin >= startMin && nowMin < endMin

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
    var strict: Boolean              // commitment device: can't be turned off while active
) {
    fun activeNow(nowMin: Int, dow: Int): Boolean =
        enabled && windows.any { it.activeAt(nowMin, dow) }

    /** Minutes remaining in the currently-active window, or -1 if none active. */
    fun minutesLeft(nowMin: Int, dow: Int): Int {
        val w = windows.firstOrNull { it.activeAt(nowMin, dow) } ?: return -1
        return w.endMin - nowMin
    }
}

/** A logged moment where Margin stepped in. */
class Interception(
    val timeMillis: Long,
    val day: Long,                   // epoch-day
    val pkg: String,
    val proceeded: Boolean           // false = user backed off (a "win")
)
