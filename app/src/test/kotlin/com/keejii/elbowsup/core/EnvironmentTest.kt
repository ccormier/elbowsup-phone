package com.keejii.elbowsup.core

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentTest {
    private val zone = ZoneId.of("America/Toronto")

    @Test
    fun theFirstNonBlankCountryWinsInUpperCase() {
        assertEquals("CA", resolveRegion("ca", "US", "GB"))
        assertEquals("US", resolveRegion("", " us ", "GB"))
        assertEquals("GB", resolveRegion(null, "  ", "gb"))
        assertNull(resolveRegion(null, "", " "))
    }

    @Test
    fun blockingNeedsAndroidTenOrNewer() {
        assertFalse(blockingSupported(28))
        assertTrue(blockingSupported(29))
        assertTrue(blockingSupported(37))
    }

    @Test
    fun blockingStaysOffUntilSetupIsComplete() {
        assertFalse(blockingActive(setupComplete = false, sdkInt = 36))
        assertFalse(blockingActive(setupComplete = true, sdkInt = 28))
        assertTrue(blockingActive(setupComplete = true, sdkInt = 36))
    }

    @Test
    fun answerAndHangUpNeedsAndroid15AndTheDialerRole() {
        assertFalse(canAnswerHangup(sdkInt = 34, dialerHeld = true))
        assertFalse(canAnswerHangup(sdkInt = 35, dialerHeld = false))
        assertTrue(canAnswerHangup(sdkInt = 35, dialerHeld = true))
    }

    @Test
    fun onlyAConfirmedNonContactCanBeBlocked() {
        assertFalse(ContactStatus.NOT_CONTACT.allowsCall)
        assertTrue(ContactStatus.CONTACT.allowsCall)
        assertTrue(ContactStatus.UNKNOWN.allowsCall)
    }

    @Test
    fun theEnvReadsTheLocalClock() {
        val snapshot = BlockerSnapshot(emptyList(), emptyList(), TimedPause.NONE, setupComplete = true)
        // 2026-09-26 is a Saturday
        val now = ZonedDateTime.of(2026, 9, 26, 3, 15, 0, 0, zone)
        val env = buildEnv(Stage.LATE, snapshot, false, ContactStatus.NOT_CONTACT, true, now)
        assertEquals(DayOfWeek.SATURDAY, env.day)
        assertEquals(3 * 60 + 15, env.minuteOfDay)
        assertFalse(env.paused)
        assertFalse(env.isContact)
        assertTrue(env.canAnswerHangup)
    }

    @Test
    fun theEnvIsPausedByATimedPauseOrAScheduleAndSeesUnknownContactsAsContacts() {
        val everyDay = TimeWindow(DayOfWeek.entries.toSet(), 0, 0)
        val now = ZonedDateTime.of(2026, 9, 26, 12, 0, 0, 0, zone)
        val timed = BlockerSnapshot(
            emptyList(),
            emptyList(),
            TimedPause(now.toInstant().toEpochMilli() + 1, false),
            true,
        )
        val schedule = PauseSchedule(1, "All", true, everyDay)
        val scheduled = BlockerSnapshot(emptyList(), listOf(schedule), TimedPause.NONE, true)
        assertTrue(buildEnv(Stage.EARLY, timed, false, ContactStatus.UNKNOWN, false, now).paused)
        assertTrue(buildEnv(Stage.EARLY, scheduled, false, ContactStatus.UNKNOWN, false, now).paused)
        assertTrue(buildEnv(Stage.EARLY, timed, false, ContactStatus.UNKNOWN, false, now).isContact)
    }
}
