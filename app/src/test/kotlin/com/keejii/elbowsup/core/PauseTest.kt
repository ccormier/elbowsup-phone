package com.keejii.elbowsup.core

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseTest {
    private val weekdays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )
    private val work = TimeWindow(weekdays, 9 * 60, 17 * 60)
    private val night = TimeWindow(weekdays, 22 * 60, 7 * 60)

    private fun schedule(window: TimeWindow = work, enabled: Boolean = true) = PauseSchedule(1, "Work", enabled, window)

    private fun paused(
        pause: TimedPause = TimedPause.NONE,
        schedules: List<PauseSchedule> = emptyList(),
        now: Long = 1_000L,
        day: DayOfWeek = DayOfWeek.MONDAY,
        minute: Int = 10 * 60,
    ) = pauseState(pause, schedules, now, day, minute)

    @Test
    fun untilTheNextCallIsItsOwnKindOfPause() {
        assertEquals(PauseState.NEXT_CALL, paused(TimedPause.NEXT_CALL))
        assertEquals(PauseState.NEXT_CALL, paused(TimedPause.NEXT_CALL, now = 5_000_000L))
    }

    @Test
    fun untilTheNextCallDoesNotDependOnTheClockOrASchedule() {
        val inWork = listOf(schedule())
        assertEquals(PauseState.NEXT_CALL, paused(TimedPause.NEXT_CALL, schedules = inWork))
    }

    @Test
    fun nothingIsPausedByDefault() {
        assertEquals(PauseState.NONE, paused())
    }

    @Test
    fun aScheduleOnlyPausesInsideItsWindow() {
        val s = listOf(schedule())
        assertEquals(PauseState.SCHEDULE, paused(schedules = s))
        assertEquals(PauseState.NONE, paused(schedules = s, minute = 18 * 60))
        assertEquals(PauseState.NONE, paused(schedules = s, day = DayOfWeek.SATURDAY))
    }

    @Test
    fun aDisabledScheduleDoesNotPause() {
        assertEquals(PauseState.NONE, paused(schedules = listOf(schedule(enabled = false))))
    }

    @Test
    fun aScheduleCrossingMidnightPausesTheNextMorning() {
        val s = listOf(schedule(night))
        assertEquals(PauseState.SCHEDULE, paused(schedules = s, day = DayOfWeek.FRIDAY, minute = 23 * 60))
        assertEquals(PauseState.SCHEDULE, paused(schedules = s, day = DayOfWeek.SATURDAY, minute = 3 * 60))
        assertEquals(PauseState.NONE, paused(schedules = s, day = DayOfWeek.MONDAY, minute = 3 * 60))
    }

    @Test
    fun aTimedPauseEndsAtItsTimestamp() {
        val p = TimedPause(untilEpochMillis = 2_000L, untilResume = false)
        assertEquals(PauseState.TIMED, paused(p, now = 1_999L))
        assertEquals(PauseState.NONE, paused(p, now = 2_000L))
    }

    @Test
    fun untilResumeIgnoresTheTimestamp() {
        val p = TimedPause(untilEpochMillis = 500L, untilResume = true)
        assertEquals(PauseState.UNTIL_RESUME, paused(p, now = 9_999L))
    }

    @Test
    fun aTimedPauseWinsTheStatusOverASchedule() {
        val p = TimedPause(untilEpochMillis = 2_000L, untilResume = false)
        assertEquals(PauseState.TIMED, paused(p, listOf(schedule())))
        assertTrue(paused(p, listOf(schedule())).isPaused)
        assertFalse(PauseState.NONE.isPaused)
    }

    @Test
    fun aTimedPauseThatEndsInsideAScheduleStaysPaused() {
        val p = TimedPause(untilEpochMillis = 500L, untilResume = false)
        assertEquals(PauseState.SCHEDULE, paused(p, listOf(schedule()), now = 1_000L))
    }
}
