package com.keejii.elbowsup.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassedCallTest {
    private val passed = PassedCall("+14155551234", atMillis = 1_000_000L)

    @Test
    fun theSameNumberJustAfterwardsIsCovered() {
        assertTrue(passed.covers("+14155551234", 1_000_000L))
        assertTrue(passed.covers("+14155551234", 1_000_000L + PASSED_CALL_WINDOW_MILLIS))
    }

    @Test
    fun aDifferentNumberIsNotCovered() {
        assertFalse(passed.covers("+14155559999", 1_001_000L))
    }

    @Test
    fun afterTheWindowItIsNotCovered() {
        assertFalse(passed.covers("+14155551234", 1_000_000L + PASSED_CALL_WINDOW_MILLIS + 1))
    }

    @Test
    fun aClockThatWentBackwardsIsNotCovered() {
        assertFalse(passed.covers("+14155551234", 999_999L))
    }

    @Test
    fun hiddenNumbersMatchEachOtherAndNothingElse() {
        val hidden = PassedCall(null, 1_000_000L)
        assertTrue(hidden.covers(null, 1_001_000L))
        assertFalse(hidden.covers("+14155551234", 1_001_000L))
        assertFalse(passed.covers(null, 1_001_000L))
    }

    @Test
    fun noPassedCallCoversNothing() {
        assertFalse(coveredByPassedCall(null, "+14155551234", 1_000_000L))
        assertTrue(coveredByPassedCall(passed, "+14155551234", 1_000_000L))
    }
}
