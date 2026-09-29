package com.keejii.elbowsup.core

import java.time.DayOfWeek

private const val DEFAULT_START = 9 * MINUTES_PER_HOUR
private const val DEFAULT_END = 17 * MINUTES_PER_HOUR

enum class ScheduleProblem { NAME_REQUIRED, NO_DAYS }

sealed interface ScheduleResult {
    data class Valid(val schedule: PauseSchedule) : ScheduleResult
    data class Invalid(val problem: ScheduleProblem) : ScheduleResult
}

/** The schedule editor's form, before it has been checked. */
data class ScheduleDraft(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val days: Set<DayOfWeek> = WEEKDAYS,
    val startMinute: Int = DEFAULT_START,
    val endMinute: Int = DEFAULT_END,
) {
    fun toSchedule(): ScheduleResult = when {
        name.isBlank() -> ScheduleResult.Invalid(ScheduleProblem.NAME_REQUIRED)
        days.isEmpty() -> ScheduleResult.Invalid(ScheduleProblem.NO_DAYS)
        else -> ScheduleResult.Valid(PauseSchedule(id, name.trim(), enabled, TimeWindow(days, startMinute, endMinute)))
    }
}

fun draftOf(schedule: PauseSchedule): ScheduleDraft = ScheduleDraft(
    id = schedule.id,
    name = schedule.name,
    enabled = schedule.enabled,
    days = schedule.window.days,
    startMinute = schedule.window.startMinute,
    endMinute = schedule.window.endMinute,
)

fun upsertSchedule(schedules: List<PauseSchedule>, schedule: PauseSchedule): List<PauseSchedule> =
    if (schedules.any { it.id == schedule.id }) {
        schedules.map { if (it.id == schedule.id) schedule else it }
    } else {
        schedules + schedule
    }

fun deleteSchedule(schedules: List<PauseSchedule>, id: Long): List<PauseSchedule> =
    schedules.filter { it.id != id }
