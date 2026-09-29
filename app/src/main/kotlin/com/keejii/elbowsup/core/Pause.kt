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
        val NONE = TimedPause(null, false)
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
