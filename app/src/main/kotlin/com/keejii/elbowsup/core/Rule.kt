package com.keejii.elbowsup.core

enum class RuleKind { ALLOW, BLOCK }

enum class MatcherType { EXACT, PREFIX, NO_NUMBER, NAME_WILDCARD, EMPTY_NAME }

enum class BlockAction { REJECT_QUIET, REJECT, SILENCE, ANSWER_HANGUP }

/**
 * One row of the ordered list; list order is priority. A null [window] means always.
 * [emptyNameOnly] only applies to [MatcherType.EXACT] and [MatcherType.PREFIX].
 */
data class Rule(
    val id: Long,
    val enabled: Boolean,
    val kind: RuleKind,
    val matcher: MatcherType,
    val pattern: String?,
    val emptyNameOnly: Boolean,
    val window: TimeWindow?,
    val action: BlockAction?,
)
