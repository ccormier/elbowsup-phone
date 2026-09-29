package com.keejii.elbowsup.core

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeWindowTest {
    private val weekdays = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)
    private val night = TimeWindow(weekdays, 22 * 60, 7 * 60)
    private val work = TimeWindow(weekdays, 9 * 60, 17 * 60)

    @Test
    fun aWindowCrossingMidnightSpillsIntoTheNextMorning() {
        assertTrue(night.contains(FRIDAY, 23 * 60))
        assertTrue(night.contains(SATURDAY, 3 * 60))
    }

    @Test
    fun theSpillBelongsToTheStartDayOnly() {
        assertFalse(night.contains(MONDAY, 3 * 60))
        assertFalse(night.contains(SUNDAY, 23 * 60))
        assertFalse(night.contains(SUNDAY, 3 * 60))
    }

    @Test
    fun endIsExclusiveAndStartIsInclusive() {
        assertTrue(night.contains(TUESDAY, 6 * 60 + 59))
        assertFalse(night.contains(TUESDAY, 7 * 60))
        assertTrue(night.contains(TUESDAY, 22 * 60))
        assertFalse(night.contains(TUESDAY, 21 * 60 + 59))
    }

    @Test
    fun aSameDayWindowIsSimple() {
        assertTrue(work.contains(MONDAY, 9 * 60))
        assertFalse(work.contains(MONDAY, 17 * 60))
        assertFalse(work.contains(SATURDAY, 10 * 60))
    }

    @Test
    fun equalStartAndEndIsTwentyFourHoursFromTheStartDay() {
        val day = TimeWindow(setOf(MONDAY), 9 * 60, 9 * 60)
        assertTrue(day.contains(MONDAY, 9 * 60))
        assertTrue(day.contains(MONDAY, 23 * 60))
        assertTrue(day.contains(TUESDAY, 8 * 60 + 59))
        assertFalse(day.contains(TUESDAY, 9 * 60))
        assertFalse(day.contains(MONDAY, 8 * 60))
    }

    @Test
    fun noDaysNeverMatches() {
        assertFalse(TimeWindow(emptySet(), 0, 24 * 60).contains(MONDAY, 12 * 60))
    }
}
