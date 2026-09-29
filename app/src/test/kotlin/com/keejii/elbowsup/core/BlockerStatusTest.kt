package com.keejii.elbowsup.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockerStatusTest {
    private fun status(
        setup: Boolean = true,
        sdk: Int = 36,
        screening: Boolean = true,
        contacts: Boolean = true,
        pause: PauseState = PauseState.NONE,
    ) = blockerStatus(setup, sdk, screening, contacts, pause)

    @Test
    fun everythingInPlaceIsActive() {
        assertEquals(BlockerStatus.ACTIVE, status())
    }

    @Test
    fun theFirstProblemInPriorityOrderWins() {
        assertEquals(BlockerStatus.UNSUPPORTED, status(sdk = 28, setup = false, screening = false))
        assertEquals(BlockerStatus.NEEDS_SETUP, status(setup = false, screening = false, contacts = false))
        assertEquals(BlockerStatus.SCREENING_MISSING, status(screening = false, contacts = false))
        assertEquals(BlockerStatus.CONTACTS_PERMISSION_MISSING, status(contacts = false, pause = PauseState.TIMED))
    }

    @Test
    fun aPauseIsReportedByItsKind() {
        assertEquals(BlockerStatus.PAUSED_TIMED, status(pause = PauseState.TIMED))
        assertEquals(BlockerStatus.PAUSED_UNTIL_RESUME, status(pause = PauseState.UNTIL_RESUME))
        assertEquals(BlockerStatus.PAUSED_SCHEDULE, status(pause = PauseState.SCHEDULE))
    }

    @Test
    fun setupCompletesOnceTheRoleAndPermissionAreHeld() {
        assertTrue(shouldCompleteSetup(false, 36, screeningHeld = true, contactsPermission = true))
        assertFalse(shouldCompleteSetup(false, 36, screeningHeld = false, contactsPermission = true))
        assertFalse(shouldCompleteSetup(false, 36, screeningHeld = true, contactsPermission = false))
        assertFalse(shouldCompleteSetup(false, 28, screeningHeld = true, contactsPermission = true))
        assertFalse(shouldCompleteSetup(true, 36, screeningHeld = true, contactsPermission = true))
    }

    @Test
    fun timedPausesAreBuiltFromTheClock() {
        assertEquals(TimedPause(1_000L + 15 * 60_000L, false), TimedPause.forMinutes(15, 1_000L))
        assertEquals(TimedPause(null, true), TimedPause.UNTIL_RESUME)
    }
}
