package com.keejii.elbowsup.core

import java.time.ZonedDateTime

enum class DialerAction {
    /** Start ringing. */
    RING,

    /** Leave the call up but do not ring. */
    NO_RING,

    /** Reject the call before it rings. */
    REJECT,

    /** Answer the call, then hang up. */
    ANSWER_HANGUP,
}

/** [event] is the block to record before acting, if any. */
data class DialerPlan(val action: DialerAction, val event: PlannedEvent?)

/**
 * Decides an incoming call in the dialer, where the caller name is known. The whole rule list runs
 * again, so a call that screening already silenced comes out silenced here too; [silentRequested]
 * says screening did that and already recorded it. Without the screening role blocking is off here too,
 * as the status screen says. Ringing is the default for anything unclear.
 */
@Suppress("LongParameterList")
fun planDialer(
    snapshot: BlockerSnapshot,
    rawNumber: String?,
    name: String?,
    region: String?,
    sdkInt: Int,
    now: ZonedDateTime,
    canAnswerHangup: Boolean,
    silentRequested: Boolean,
    screeningHeld: Boolean,
    isEmergency: () -> Boolean,
    contact: () -> ContactStatus,
): DialerPlan {
    val quiet = DialerPlan(if (silentRequested) DialerAction.NO_RING else DialerAction.RING, null)
    if (!screeningHeld || !blockingActive(snapshot.setupComplete, sdkInt) || snapshot.rules.isEmpty()) return quiet
    val number = normalizeNumber(rawNumber, region)
    val env = buildEnv(Stage.LATE, snapshot, rawNumber != null && isEmergency(), contact(), canAnswerHangup, now)
    val decision = evaluate(snapshot.rules, CallInfo(number, name), env)
    val action = when (decision.outcome) {
        Outcome.ALLOW, Outcome.DEFER -> return quiet
        Outcome.REJECT_QUIET, Outcome.REJECT -> DialerAction.REJECT
        Outcome.SILENCE -> DialerAction.NO_RING
        Outcome.ANSWER_HANGUP -> DialerAction.ANSWER_HANGUP
    }
    val alreadyRecorded = decision.outcome == Outcome.SILENCE && silentRequested
    val event = if (alreadyRecorded) {
        null
    } else {
        PlannedEvent(
            number = number,
            action = requireNotNull(decision.recordedAction),
            ruleId = decision.rule?.id,
            ruleSummary = decision.rule?.let { ruleSummary(it) }.orEmpty(),
        )
    }
    return DialerPlan(action, event)
}
