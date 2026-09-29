package com.keejii.elbowsup.core

import java.time.ZonedDateTime

/** What to record for a block, before it gets an id and a timestamp. */
data class PlannedEvent(
    val number: String?,
    val action: BlockAction,
    val ruleId: Long?,
    val ruleSummary: String,
)

/**
 * The screening answer. Null [flags] means the blocker has no verdict and Fossify's own screening
 * should run unchanged. [event] is the block to record before responding, if any.
 */
data class ScreeningPlan(val flags: ScreeningFlags?, val event: PlannedEvent?) {
    companion object {
        val PASS_THROUGH = ScreeningPlan(null, null)
    }
}

/**
 * Decides an incoming call at screening time, when the caller name is not known yet. Lookups are
 * lambdas so they only run once there is something to decide, and a call with no rules costs nothing.
 */
@Suppress("LongParameterList")
fun planScreening(
    snapshot: BlockerSnapshot,
    rawNumber: String?,
    region: String?,
    sdkInt: Int,
    now: ZonedDateTime,
    canAnswerHangup: Boolean,
    isEmergency: () -> Boolean,
    contact: () -> ContactStatus,
): ScreeningPlan {
    if (!blockingActive(snapshot.setupComplete, sdkInt) || snapshot.rules.isEmpty()) return ScreeningPlan.PASS_THROUGH
    val number = normalizeNumber(rawNumber, region)
    val env = buildEnv(Stage.EARLY, snapshot, rawNumber != null && isEmergency(), contact(), canAnswerHangup, now)
    val decision = evaluate(snapshot.rules, CallInfo(number, name = null), env)
    return when (decision.outcome) {
        Outcome.DEFER -> ScreeningPlan.PASS_THROUGH
        Outcome.ALLOW -> if (decision.rule != null) {
            ScreeningPlan(screeningFlags(Outcome.ALLOW), null)
        } else {
            ScreeningPlan.PASS_THROUGH
        }
        Outcome.ANSWER_HANGUP -> ScreeningPlan(screeningFlags(Outcome.ANSWER_HANGUP), null)
        Outcome.REJECT_QUIET, Outcome.REJECT, Outcome.SILENCE -> ScreeningPlan(
            flags = screeningFlags(decision.outcome),
            event = PlannedEvent(
                number = number,
                action = requireNotNull(decision.recordedAction),
                ruleId = decision.rule?.id,
                ruleSummary = decision.rule?.let { ruleSummary(it) }.orEmpty(),
            ),
        )
    }
}
