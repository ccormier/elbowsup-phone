package com.keejii.elbowsup.core

import java.time.ZonedDateTime

private const val MIN_SDK_BLOCKING = 29
private const val MIN_SDK_ANSWER_HANGUP = 35

/** Everything the call path reads, held in memory. */
data class BlockerSnapshot(
    val rules: List<Rule>,
    val schedules: List<PauseSchedule>,
    val timedPause: TimedPause,
    val setupComplete: Boolean,
)

/** CONTACT and UNKNOWN both allow the call: when contacts cannot be checked, nothing is blocked. */
enum class ContactStatus {
    CONTACT,
    NOT_CONTACT,
    UNKNOWN;

    val allowsCall: Boolean get() = this != NOT_CONTACT
}

/** The first non-blank country code, upper-cased: SIM, then network, then locale. */
fun resolveRegion(vararg countries: String?): String? =
    countries.firstOrNull { !it.isNullOrBlank() }?.trim()?.uppercase()

/** Call screening roles and `isEmergencyNumber` arrived in Android 10. */
fun blockingSupported(sdkInt: Int): Boolean = sdkInt >= MIN_SDK_BLOCKING

/** Blocking stays off until setup has completed once, and below Android 10. */
fun blockingActive(setupComplete: Boolean, sdkInt: Int): Boolean = setupComplete && blockingSupported(sdkInt)

/** `Call.Details.getId()`, which the handoff is keyed on, needs Android 15. */
fun canAnswerHangup(sdkInt: Int, dialerHeld: Boolean): Boolean = sdkInt >= MIN_SDK_ANSWER_HANGUP && dialerHeld

fun buildEnv(
    stage: Stage,
    snapshot: BlockerSnapshot,
    isEmergency: Boolean,
    contact: ContactStatus,
    canAnswerHangup: Boolean,
    now: ZonedDateTime,
): Env {
    val day = now.dayOfWeek
    val minuteOfDay = now.hour * MINUTES_PER_HOUR + now.minute
    val pause = pauseState(snapshot.timedPause, snapshot.schedules, now.toInstant().toEpochMilli(), day, minuteOfDay)
    return Env(stage, isEmergency, contact.allowsCall, pause.isPaused, canAnswerHangup, day, minuteOfDay)
}
