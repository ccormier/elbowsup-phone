package com.keejii.elbowsup.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScreeningFlagsTest {
    @Test
    fun flagsMatchTheDesign() {
        assertEquals(ScreeningFlags(false, false, false, false, false), screeningFlags(Outcome.ALLOW))
        assertEquals(ScreeningFlags(false, false, false, false, false), screeningFlags(Outcome.DEFER))
        assertEquals(ScreeningFlags(true, true, false, false, true), screeningFlags(Outcome.REJECT_QUIET))
        assertEquals(ScreeningFlags(true, true, false, false, false), screeningFlags(Outcome.REJECT))
        assertEquals(ScreeningFlags(false, false, true, false, false), screeningFlags(Outcome.SILENCE))
        assertEquals(ScreeningFlags(false, false, true, false, false), screeningFlags(Outcome.ANSWER_HANGUP))
    }

    @Test
    fun silenceIsNeverCombinedWithDisallow() {
        for (outcome in Outcome.entries) {
            val flags = screeningFlags(outcome)
            assertFalse("$outcome combines silence and disallow", flags.silence && flags.disallow)
        }
    }

    @Test
    fun theCallLogIsNeverSkipped() {
        for (outcome in Outcome.entries) {
            assertFalse(screeningFlags(outcome).skipCallLog)
        }
    }
}
