package com.keejii.elbowsup.core

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.time.DayOfWeek

private const val DEFAULT_START = 22 * MINUTES_PER_HOUR
private const val DEFAULT_END = 7 * MINUTES_PER_HOUR
private val NUMBER_TEXT = Regex("""\+?\d+""")

val WEEKDAYS: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

enum class DraftProblem { PATTERN_REQUIRED, NOT_A_NUMBER, NO_DAYS }

sealed interface DraftResult {
    data class Valid(val rule: Rule) : DraftResult
    data class Invalid(val problem: DraftProblem) : DraftResult
}

/** The rule editor's form, before it has been checked. */
data class RuleDraft(
    val id: Long = 0,
    val enabled: Boolean = true,
    val kind: RuleKind = RuleKind.BLOCK,
    val matcher: MatcherType = MatcherType.PREFIX,
    val patternText: String = "",
    val customWindow: Boolean = false,
    val days: Set<DayOfWeek> = WEEKDAYS,
    val startMinute: Int = DEFAULT_START,
    val endMinute: Int = DEFAULT_END,
    val action: BlockAction = BlockAction.REJECT_QUIET,
) {
    fun toRule(region: String?): DraftResult {
        val pattern = when (val parsed = patternFor(region)) {
            is PatternResult.Failed -> return DraftResult.Invalid(parsed.problem)
            is PatternResult.Ok -> parsed.pattern
        }
        if (customWindow && days.isEmpty()) return DraftResult.Invalid(DraftProblem.NO_DAYS)
        return DraftResult.Valid(
            Rule(
                id = id,
                enabled = enabled,
                kind = kind,
                matcher = matcher,
                pattern = pattern,
                window = if (customWindow) TimeWindow(days, startMinute, endMinute) else null,
                action = if (kind == RuleKind.BLOCK) action else null,
            ),
        )
    }

    private fun patternFor(region: String?): PatternResult {
        val text = patternText.trim()
        return when (matcher) {
            MatcherType.EMPTY_NAME, MatcherType.NO_NUMBER -> PatternResult.Ok(null)
            MatcherType.NAME_WILDCARD ->
                if (text.isEmpty()) PatternResult.Failed(DraftProblem.PATTERN_REQUIRED) else PatternResult.Ok(text)
            MatcherType.EXACT, MatcherType.PREFIX -> numberPattern(text, region)
        }
    }

    private fun numberPattern(text: String, region: String?): PatternResult {
        if (text.isEmpty()) return PatternResult.Failed(DraftProblem.PATTERN_REQUIRED)
        val digits = text.filter { it !in " -().\t" }
        if (!NUMBER_TEXT.matches(digits)) return PatternResult.Failed(DraftProblem.NOT_A_NUMBER)
        val pattern = if (matcher == MatcherType.EXACT) normalizeNumber(text, region) else prefixOf(digits, region)
        return PatternResult.Ok(pattern)
    }
}

private sealed interface PatternResult {
    data class Ok(val pattern: String?) : PatternResult
    data class Failed(val problem: DraftProblem) : PatternResult
}

/** A prefix typed with a plus is international; without one it is read in the phone's own country. */
private fun prefixOf(digits: String, region: String?): String {
    if (digits.startsWith("+")) return digits
    val countryCode = region?.let { PhoneNumberUtil.getInstance().getCountryCodeForRegion(it) } ?: 0
    return if (countryCode > 0) "+$countryCode$digits" else digits
}

fun draftOf(rule: Rule): RuleDraft = RuleDraft(
    id = rule.id,
    enabled = rule.enabled,
    kind = rule.kind,
    matcher = rule.matcher,
    patternText = rule.pattern.orEmpty(),
    customWindow = rule.window != null,
    days = rule.window?.days ?: WEEKDAYS,
    startMinute = rule.window?.startMinute ?: DEFAULT_START,
    endMinute = rule.window?.endMinute ?: DEFAULT_END,
    action = rule.action ?: BlockAction.REJECT_QUIET,
)
