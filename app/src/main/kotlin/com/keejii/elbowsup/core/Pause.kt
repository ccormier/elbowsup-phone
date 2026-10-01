package com.keejii.elbowsup.core

import java.time.DayOfWeek

data class PauseSchedule(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val window: TimeWindow,
)

/** [untilNextCall] pauses blocking until the next incoming call arrives, which uses the pause up. */
data class TimedPause(val untilEpochMillis: Long?, val untilResume: Boolean, val untilNextCall: Boolean = false) {
    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L

        val NONE = TimedPause(null, false)
        val UNTIL_RESUME = TimedPause(null, true)
        val NEXT_CALL = TimedPause(null, false, untilNextCall = true)

        fun forMinutes(minutes: Int, nowEpochMillis: Long) =
            TimedPause(nowEpochMillis + minutes * MILLIS_PER_MINUTE, false)
    }
}

enum class PauseState {
    NONE,
    TIMED,
    UNTIL_RESUME,
    NEXT_CALL,
    SCHEDULE;

    val isPaused: Boolean get() = this != NONE
}

/** Why blocking is paused right now, in the order status copy should prefer. */
fun pauseState(
    pause: TimedPause,
    schedules: List<PauseSchedule>,
    nowEpochMillis: Long,
    day: DayOfWeek,
    minuteOfDay: Int,
): PauseState = when {
    pause.untilResume -> PauseState.UNTIL_RESUME
    pause.untilNextCall -> PauseState.NEXT_CALL
    pause.untilEpochMillis != null && nowEpochMillis < pause.untilEpochMillis -> PauseState.TIMED
    schedules.any { it.enabled && it.window.contains(day, minuteOfDay) } -> PauseState.SCHEDULE
    else -> PauseState.NONE
}

/** A call that used up a pause until the next call, so the rest of its handling must not block it. */
const val PASSED_CALL_WINDOW_MILLIS = 60_000L

data class PassedCall(val number: String?, val atMillis: Long) {
    /** The same number (hidden numbers match each other) within the window after it was let through. */
    fun covers(otherNumber: String?, nowMillis: Long): Boolean =
        number == otherNumber && nowMillis - atMillis in 0..PASSED_CALL_WINDOW_MILLIS
}

fun coveredByPassedCall(passed: PassedCall?, number: String?, nowMillis: Long): Boolean =
    passed != null && passed.covers(number, nowMillis)
