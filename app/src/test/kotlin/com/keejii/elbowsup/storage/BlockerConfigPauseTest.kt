package com.keejii.elbowsup.storage

import com.keejii.elbowsup.core.TimedPause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockerConfigPauseTest {
    private val prefs = FakePreferences()

    private fun saved(pause: TimedPause) {
        val config = BlockerConfig(prefs)
        val old = config.load()
        config.save(old, old.copy(timedPause = pause))
    }

    @Test
    fun aPauseUntilTheNextCallIsSavedAndLoaded() {
        saved(TimedPause.NEXT_CALL)
        val pause = BlockerConfig(prefs).load().timedPause
        assertTrue(pause.untilNextCall)
        assertFalse(pause.untilResume)
        assertEquals(null, pause.untilEpochMillis)
    }

    @Test
    fun clearingItIsSavedToo() {
        saved(TimedPause.NEXT_CALL)
        saved(TimedPause.NONE)
        assertEquals(TimedPause.NONE, BlockerConfig(prefs).load().timedPause)
    }

    @Test
    fun theOtherPausesStillRoundTripAndAreNotMistakenForIt() {
        saved(TimedPause.UNTIL_RESUME)
        assertEquals(TimedPause.UNTIL_RESUME, BlockerConfig(prefs).load().timedPause)
        saved(TimedPause.forMinutes(15, 1_000L))
        assertEquals(TimedPause.forMinutes(15, 1_000L), BlockerConfig(prefs).load().timedPause)
    }
}
