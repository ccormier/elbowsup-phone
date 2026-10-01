package com.keejii.elbowsup.core

import java.time.ZonedDateTime

/** What to record for a block, before it gets an id and a timestamp. */
data class PlannedEvent(
    val number: String?,
    val action: BlockAction,
    val ruleId: Long?,
    val ruleSummary: String,
)

/** This call is the one a pause until the next call was waiting for; [number] is in normalized form. */
data class NextCallPass(val number: String?)

/**
 * The screening answer. Null [flags] means the blocker has no verdict and Fossify's own screening
 * should run unchanged. [event] is the block to record before responding, if any. [nextCallPass] is set
 * when this call used up a pause until the next call: it is let through, and the caller clears the pause
 * and remembers the call so the late stage lets it through as well.
 */
data class ScreeningPlan(
    val flags: ScreeningFlags?,
    val event: PlannedEvent?,
    val nextCallPass: NextCallPass? = null,
) {
    companion object {
        val PASS_THROUGH = ScreeningPlan(null, null)
    }
}

/**
 * Decides an incoming call at screening time, when the caller name is not known yet. Lookups are
 * lambdas so they only run once there is something to decide, and a call with no rules costs nothing.
 * Telecom also binds this service as the phone app's when nobody holds the screening role, and the
 * status screen says blocking is off then, so without the role there is no verdict.
 */
@Suppress("LongParameterList")
fun planScreening(
    snapshot: BlockerSnapshot,
    rawNumber: String?,
    region: String?,
    sdkInt: Int,
    now: ZonedDateTime,
    canAnswerHangup: Boolean,
    screeningHeld: Boolean,
    isEmergency: () -> Boolean,
    contact: () -> ContactStatus,
): ScreeningPlan {
    if (!screeningHeld || !blockingActive(snapshot.setupComplete, sdkInt)) return ScreeningPlan.PASS_THROUGH
    val number = normalizeNumber(rawNumber, region)
    if (snapshot.timedPause.untilNextCall) return ScreeningPlan(null, null, NextCallPass(number))
    if (snapshot.rules.isEmpty()) return ScreeningPlan.PASS_THROUGH
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
