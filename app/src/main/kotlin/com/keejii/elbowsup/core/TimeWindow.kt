package com.keejii.elbowsup.core

import java.time.DayOfWeek

/**
 * Days plus a start and end minute of the day. Start is inclusive, end is exclusive. When the end
 * is not after the start the window crosses midnight, and the start day owns the spill into the
 * next morning.
 */
data class TimeWindow(
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
) {
    fun contains(day: DayOfWeek, minuteOfDay: Int): Boolean {
        if (endMinute > startMinute) {
            return day in days && minuteOfDay >= startMinute && minuteOfDay < endMinute
        }
        val evening = day in days && minuteOfDay >= startMinute
        val morningSpill = day.minus(1) in days && minuteOfDay < endMinute
        return evening || morningSpill
    }
}
