package com.keejii.elbowsup.core

import java.time.DayOfWeek

data class PauseSchedule(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val window: TimeWindow,
)

data class TimedPause(val untilEpochMillis: Long?, val untilResume: Boolean) {
    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L

        val NONE = TimedPause(null, false)
        val UNTIL_RESUME = TimedPause(null, true)

        fun forMinutes(minutes: Int, nowEpochMillis: Long) =
            TimedPause(nowEpochMillis + minutes * MILLIS_PER_MINUTE, false)
    }
}

enum class PauseState {
    NONE,
    TIMED,
    UNTIL_RESUME,
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
    pause.untilEpochMillis != null && nowEpochMillis < pause.untilEpochMillis -> PauseState.TIMED
    schedules.any { it.enabled && it.window.contains(day, minuteOfDay) } -> PauseState.SCHEDULE
    else -> PauseState.NONE
}
