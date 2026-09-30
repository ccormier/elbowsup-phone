package com.keejii.elbowsup.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockedMatchTest {
    private val start = 1_000_000L

    private fun event(id: Long, number: String?, at: Long, action: String = "REJECT_QUIET") =
        BlockedEvent(id, at, number, null, action, ruleId = 1, ruleSummary = "rule")

    @Test
    fun aBlockJustAfterTheCallStartedMatchesTheSameNumber() {
        val events = listOf(event(1, "+14155551234", start + 2_600))
        assertEquals(1L, matchBlockedEvent(events, "+14155551234", start)?.id)
    }

    @Test
    fun aDifferentNumberDoesNotMatch() {
        val events = listOf(event(1, "+14155551234", start + 1_000))
        assertNull(matchBlockedEvent(events, "+14155559999", start))
    }

    @Test
    fun aBlockOutsideTheWindowDoesNotMatch() {
        val late = start + EVENT_AFTER_MILLIS + 1
        val early = start - EVENT_BEFORE_MILLIS - 1
        assertNull(matchBlockedEvent(listOf(event(1, "+1415", late)), "+1415", start))
        assertNull(matchBlockedEvent(listOf(event(1, "+1415", early)), "+1415", start))
    }

    @Test
    fun theWindowEdgesAreInclusive() {
        assertEquals(1L, matchBlockedEvent(listOf(event(1, "+1415", start + EVENT_AFTER_MILLIS)), "+1415", start)?.id)
        assertEquals(1L, matchBlockedEvent(listOf(event(1, "+1415", start - EVENT_BEFORE_MILLIS)), "+1415", start)?.id)
    }

    @Test
    fun theClosestEventWinsWhenANumberWasBlockedTwiceInARow() {
        val events = listOf(
            event(3, "+1415", start + 9_000),
            event(2, "+1415", start + 1_000),
            event(1, "+1415", start - 1_500),
        )
        assertEquals(2L, matchBlockedEvent(events, "+1415", start)?.id)
    }

    @Test
    fun aTieGoesToTheNewestEvent() {
        val events = listOf(event(2, "+1415", start + 1_000), event(1, "+1415", start - 1_000))
        assertEquals(2L, matchBlockedEvent(events, "+1415", start)?.id)
    }

    @Test
    fun aHiddenNumberMatchesAnEventWithNoNumber() {
        val events = listOf(event(1, null, start + 1_000), event(2, "+1415", start + 500))
        assertEquals(1L, matchBlockedEvent(events, null, start)?.id)
        assertNull(matchBlockedEvent(listOf(event(2, "+1415", start + 500)), null, start))
    }

    @Test
    fun noEventsMatchesNothing() {
        assertNull(matchBlockedEvent(emptyList(), "+1415", start))
    }
}
