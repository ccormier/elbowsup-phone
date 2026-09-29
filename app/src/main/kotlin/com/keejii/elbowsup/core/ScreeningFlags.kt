package com.keejii.elbowsup.core

/**
 * The `CallScreeningService.CallResponse` flags for an early-stage outcome. Silence is never
 * combined with disallow, because a disallowed call never reaches the dialer. Answer-and-hang-up
 * only silences here; the dialer answers and disconnects it later.
 */
data class ScreeningFlags(
    val disallow: Boolean,
    val reject: Boolean,
    val silence: Boolean,
    val skipCallLog: Boolean,
    val skipNotification: Boolean,
)

fun screeningFlags(outcome: Outcome): ScreeningFlags = when (outcome) {
    Outcome.ALLOW, Outcome.DEFER -> ScreeningFlags(false, false, false, false, false)
    Outcome.REJECT_QUIET -> ScreeningFlags(true, true, false, false, true)
    Outcome.REJECT -> ScreeningFlags(true, true, false, false, false)
    Outcome.SILENCE, Outcome.ANSWER_HANGUP -> ScreeningFlags(false, false, true, false, false)
}
