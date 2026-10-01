package com.keejii.elbowsup.core

enum class BlockerStatus {
    UNSUPPORTED,
    NEEDS_SETUP,
    SCREENING_MISSING,
    CONTACTS_PERMISSION_MISSING,
    PAUSED_TIMED,
    PAUSED_UNTIL_RESUME,
    PAUSED_NEXT_CALL,
    PAUSED_SCHEDULE,
    ACTIVE,
}

/** The first thing that stops blocking, or why it is paused, or [BlockerStatus.ACTIVE]. */
fun blockerStatus(
    setupComplete: Boolean,
    sdkInt: Int,
    screeningHeld: Boolean,
    contactsPermission: Boolean,
    pause: PauseState,
): BlockerStatus = when {
    !blockingSupported(sdkInt) -> BlockerStatus.UNSUPPORTED
    !setupComplete -> BlockerStatus.NEEDS_SETUP
    !screeningHeld -> BlockerStatus.SCREENING_MISSING
    !contactsPermission -> BlockerStatus.CONTACTS_PERMISSION_MISSING
    pause == PauseState.TIMED -> BlockerStatus.PAUSED_TIMED
    pause == PauseState.UNTIL_RESUME -> BlockerStatus.PAUSED_UNTIL_RESUME
    pause == PauseState.NEXT_CALL -> BlockerStatus.PAUSED_NEXT_CALL
    pause == PauseState.SCHEDULE -> BlockerStatus.PAUSED_SCHEDULE
    else -> BlockerStatus.ACTIVE
}

/** Setup is done once, the first time the screening role and contacts permission are both held. */
fun shouldCompleteSetup(
    setupComplete: Boolean,
    sdkInt: Int,
    screeningHeld: Boolean,
    contactsPermission: Boolean,
): Boolean = !setupComplete && blockingSupported(sdkInt) && screeningHeld && contactsPermission
