package com.keejii.elbowsup.storage

import com.google.gson.Gson
import com.keejii.elbowsup.core.BlockAction
import com.keejii.elbowsup.core.MatcherType
import com.keejii.elbowsup.core.PauseSchedule
import com.keejii.elbowsup.core.Rule
import com.keejii.elbowsup.core.RuleKind
import com.keejii.elbowsup.core.TimeWindow
import java.time.DayOfWeek

private data class RuleDto(
    val id: Long = 0,
    val enabled: Boolean = true,
    val kind: String? = null,
    val matcher: String? = null,
    val pattern: String? = null,
    /** Removed setting: a rule that had it on cannot be read back as a broader rule, so it is dropped. */
    val emptyNameOnly: Boolean = false,
    val days: String? = null,
    val start: Int? = null,
    val end: Int? = null,
    val action: String? = null,
)

private data class ScheduleDto(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val days: String? = null,
    val start: Int? = null,
    val end: Int? = null,
)

/**
 * Rules and schedules as JSON. Reading never throws: unreadable input is an empty list, and a single
 * unreadable entry (for example one written by a newer version) is dropped, so a bad entry never
 * turns into a rule that matches more than it should.
 */
object BlockerJson {
    private val gson = Gson()

    fun rulesToJson(rules: List<Rule>): String = gson.toJson(rules.map { it.toDto() })

    fun rulesFromJson(json: String?): List<Rule> =
        parse(json, Array<RuleDto>::class.java)
            .filterNot { it.emptyNameOnly }
            .mapNotNull { runCatching { it.toRule() }.getOrNull() }

    fun schedulesToJson(schedules: List<PauseSchedule>): String = gson.toJson(schedules.map { it.toDto() })

    fun schedulesFromJson(json: String?): List<PauseSchedule> =
        parse(json, Array<ScheduleDto>::class.java).mapNotNull { runCatching { it.toSchedule() }.getOrNull() }

    private fun <T> parse(json: String?, type: Class<Array<T>>): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        val parsed = runCatching { gson.fromJson(json, type) }.getOrNull() ?: return emptyList()
        return parsed.filterNotNull()
    }
}

private fun Rule.toDto() = RuleDto(
    id = id,
    enabled = enabled,
    kind = kind.name,
    matcher = matcher.name,
    pattern = pattern,
    days = window?.days?.encode(),
    start = window?.startMinute,
    end = window?.endMinute,
    action = action?.name,
)

private fun RuleDto.toRule() = Rule(
    id = id,
    enabled = enabled,
    kind = RuleKind.valueOf(requireNotNull(kind)),
    matcher = MatcherType.valueOf(requireNotNull(matcher)),
    pattern = pattern,
    window = windowOf(days, start, end),
    action = action?.let { BlockAction.valueOf(it) },
)

private fun PauseSchedule.toDto() = ScheduleDto(
    id = id,
    name = name,
    enabled = enabled,
    days = window.days.encode(),
    start = window.startMinute,
    end = window.endMinute,
)

private fun ScheduleDto.toSchedule() = PauseSchedule(
    id = id,
    name = name,
    enabled = enabled,
    window = requireNotNull(windowOf(days, start, end)),
)

private fun Set<DayOfWeek>.encode() = joinToString(",") { it.name }

/** All three parts absent is "always" (null); a partly missing window is corrupt and throws. */
private fun windowOf(days: String?, start: Int?, end: Int?): TimeWindow? {
    if (days == null && start == null && end == null) return null
    return TimeWindow(
        days = requireNotNull(days).split(",").filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it) }.toSet(),
        startMinute = requireNotNull(start),
        endMinute = requireNotNull(end),
    )
}
