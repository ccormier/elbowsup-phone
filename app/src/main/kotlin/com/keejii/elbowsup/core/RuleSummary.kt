package com.keejii.elbowsup.core

/** A one-line description of a rule, stored with each blocked event so it survives edits. */
fun ruleSummary(rule: Rule): String {
    val parts = mutableListOf(
        (if (rule.kind == RuleKind.ALLOW) "Allow" else "Block") + " " + matcherText(rule),
    )
    if (rule.emptyNameOnly && rule.matcher in NUMBER_MATCHERS) parts += "empty name only"
    rule.window?.let { parts += "${clock(it.startMinute)}-${clock(it.endMinute)}" }
    if (rule.kind == RuleKind.BLOCK) parts += actionText(rule.action ?: BlockAction.REJECT_QUIET)
    return parts.joinToString(", ")
}

private val NUMBER_MATCHERS = setOf(MatcherType.EXACT, MatcherType.PREFIX)

private fun matcherText(rule: Rule): String = when (rule.matcher) {
    MatcherType.EXACT -> "exact ${rule.pattern}"
    MatcherType.PREFIX -> "prefix ${rule.pattern}"
    MatcherType.NAME_WILDCARD -> "name ${rule.pattern}"
    MatcherType.EMPTY_NAME -> "empty name"
    MatcherType.NO_NUMBER -> "no number"
}

private fun actionText(action: BlockAction): String = when (action) {
    BlockAction.REJECT_QUIET -> "reject quietly"
    BlockAction.REJECT -> "reject"
    BlockAction.SILENCE -> "silence"
    BlockAction.ANSWER_HANGUP -> "answer and hang up"
}

private fun clock(minuteOfDay: Int) = "%02d:%02d".format(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
