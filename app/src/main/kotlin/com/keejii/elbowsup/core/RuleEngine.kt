package com.keejii.elbowsup.core

import java.time.DayOfWeek

/** EARLY is call screening, where the caller name is not known yet. LATE is the dialer, where it is. */
enum class Stage { EARLY, LATE }

enum class Outcome { ALLOW, DEFER, REJECT_QUIET, REJECT, SILENCE, ANSWER_HANGUP }

/** [number] is in [normalizeNumber] form; [name] is a [realCallerName] or null. */
data class CallInfo(val number: String?, val name: String?)

data class Env(
    val stage: Stage,
    val isEmergency: Boolean,
    val isContact: Boolean,
    val paused: Boolean,
    val canAnswerHangup: Boolean,
    val day: DayOfWeek,
    val minuteOfDay: Int,
)

/**
 * [rule] is the rule that decided, or for [Outcome.DEFER] the first rule that needs the name.
 * [recordedAction] is what to record for a block, which differs from the rule's action when
 * answer-and-hang-up is unavailable.
 */
data class Decision(val outcome: Outcome, val rule: Rule?, val recordedAction: BlockAction?)

private val ALLOW_DECISION = Decision(Outcome.ALLOW, null, null)

private enum class Verdict { MATCH, NO_MATCH, NEEDS_NAME }

fun evaluate(rules: List<Rule>, call: CallInfo, env: Env): Decision {
    if (env.isEmergency || env.isContact || env.paused) return ALLOW_DECISION
    return rules.firstNotNullOfOrNull { decisionFor(it, call, env) } ?: ALLOW_DECISION
}

/** The decision this rule makes, or null when it does not apply and the walk should go on. */
private fun decisionFor(rule: Rule, call: CallInfo, env: Env): Decision? {
    if (!rule.enabled) return null
    if (rule.window != null && !rule.window.contains(env.day, env.minuteOfDay)) return null
    return when (verdictFor(rule, call, env.stage)) {
        Verdict.NO_MATCH -> null
        Verdict.NEEDS_NAME -> Decision(Outcome.DEFER, rule, null)
        Verdict.MATCH -> decide(rule, env)
    }
}

private fun verdictFor(rule: Rule, call: CallInfo, stage: Stage): Verdict = when (rule.matcher) {
    MatcherType.NO_NUMBER -> verdict(call.number == null)
    MatcherType.EXACT -> verdict(call.number != null && call.number == rule.pattern)
    MatcherType.PREFIX -> verdict(startsWithPattern(call.number, rule.pattern))
    MatcherType.EMPTY_NAME -> emptyNameVerdict(call, stage)
    MatcherType.NAME_WILDCARD -> nameWildcardVerdict(rule, call, stage)
}

private fun startsWithPattern(number: String?, pattern: String?): Boolean =
    number != null && pattern != null && number.startsWith(pattern)

private fun emptyNameVerdict(call: CallInfo, stage: Stage): Verdict = when {
    call.number == null -> Verdict.NO_MATCH
    stage == Stage.EARLY -> Verdict.NEEDS_NAME
    else -> verdict(call.name == null)
}

private fun nameWildcardVerdict(rule: Rule, call: CallInfo, stage: Stage): Verdict = when {
    stage == Stage.EARLY -> Verdict.NEEDS_NAME
    else -> verdict(call.name != null && wildcardMatches(rule.pattern, call.name))
}

private fun verdict(matches: Boolean) = if (matches) Verdict.MATCH else Verdict.NO_MATCH

/**
 * `*` is any run of characters, `?` is one character, case-insensitive; everything else is literal.
 * Matched by walking both strings once and backing up to the latest `*`, so the time is bounded by the
 * two lengths however many stars the pattern has.
 */
private fun wildcardMatches(pattern: String?, name: String): Boolean {
    if (pattern == null) return false
    var p = 0
    var n = 0
    var star = -1
    var resume = 0
    while (n < name.length) {
        val ch = pattern.getOrNull(p)
        when {
            ch == '*' -> {
                star = p++
                resume = n
            }
            ch != null && (ch == '?' || ch.equals(name[n], ignoreCase = true)) -> {
                p++
                n++
            }
            star >= 0 -> {
                p = star + 1
                n = ++resume
            }
            else -> return false
        }
    }
    while (pattern.getOrNull(p) == '*') p++
    return p == pattern.length
}

private fun decide(rule: Rule, env: Env): Decision {
    if (rule.kind == RuleKind.ALLOW) return Decision(Outcome.ALLOW, rule, null)
    val requested = rule.action ?: BlockAction.REJECT_QUIET
    val unsupported = requested == BlockAction.ANSWER_HANGUP && !env.canAnswerHangup
    val recorded = if (unsupported) BlockAction.REJECT_QUIET else requested
    val outcome = when (recorded) {
        BlockAction.REJECT_QUIET -> Outcome.REJECT_QUIET
        BlockAction.REJECT -> Outcome.REJECT
        BlockAction.SILENCE -> Outcome.SILENCE
        BlockAction.ANSWER_HANGUP -> Outcome.ANSWER_HANGUP
    }
    return Decision(outcome, rule, recorded)
}
