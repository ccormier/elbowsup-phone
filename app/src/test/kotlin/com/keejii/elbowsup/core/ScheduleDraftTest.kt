package com.keejii.elbowsup.core

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleDraftTest {
    private val monday = setOf(DayOfWeek.MONDAY)

    @Test
    fun aNamedWindowBecomesASchedule() {
        val result = ScheduleDraft(name = "  Work ", days = monday, startMinute = 540, endMinute = 1020).toSchedule()
        val schedule = (result as ScheduleResult.Valid).schedule
        assertEquals("Work", schedule.name)
        assertEquals(TimeWindow(monday, 540, 1020), schedule.window)
        assertEquals(true, schedule.enabled)
    }

    @Test
    fun aBlankNameOrNoDaysIsRefused() {
        val noName = ScheduleDraft(name = " ", days = monday).toSchedule() as ScheduleResult.Invalid
        val noDays = ScheduleDraft(name = "Work", days = emptySet()).toSchedule() as ScheduleResult.Invalid
        assertEquals(ScheduleProblem.NAME_REQUIRED, noName.problem)
        assertEquals(ScheduleProblem.NO_DAYS, noDays.problem)
    }

    @Test
    fun aScheduleRoundTripsThroughItsDraft() {
        val schedule = PauseSchedule(4, "Sleep", false, TimeWindow(monday, 22 * 60, 7 * 60))
        assertEquals(schedule, (draftOf(schedule).toSchedule() as ScheduleResult.Valid).schedule)
    }

    @Test
    fun upsertReplacesByIdOrAppends() {
        val a = PauseSchedule(1, "A", true, TimeWindow(monday, 0, 60))
        val b = PauseSchedule(2, "B", true, TimeWindow(monday, 0, 60))
        assertEquals(listOf(a, b), upsertSchedule(listOf(a), b))
        assertEquals(listOf(a.copy(name = "A2"), b), upsertSchedule(listOf(a, b), a.copy(name = "A2")))
        assertEquals(listOf(b), deleteSchedule(listOf(a, b), 1))
    }
}
