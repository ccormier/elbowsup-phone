package com.keejii.elbowsup.core

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningPlanTest {
    private val now = ZonedDateTime.of(2026, 9, 28, 15, 0, 0, 0, ZoneId.of("America/Toronto")) // a Monday
    private var nextId = 1L

    private fun block(
        matcher: MatcherType,
        pattern: String? = null,
        action: BlockAction = BlockAction.REJECT_QUIET,
    ) = Rule(nextId++, true, RuleKind.BLOCK, matcher, pattern, false, null, action)

    private fun allow(pattern: String) =
        Rule(nextId++, true, RuleKind.ALLOW, MatcherType.EXACT, pattern, false, null, null)

    private fun snapshot(rules: List<Rule>, setup: Boolean = true, pause: TimedPause = TimedPause.NONE) =
        BlockerSnapshot(rules, emptyList(), pause, setup)

    private fun plan(
        rules: List<Rule>,
        raw: String? = "+14155551234",
        setup: Boolean = true,
        sdk: Int = 36,
        emergency: Boolean = false,
        contact: ContactStatus = ContactStatus.NOT_CONTACT,
        canAnswerHangup: Boolean = false,
        pause: TimedPause = TimedPause.NONE,
        region: String? = "US",
    ) = planScreening(
        snapshot(rules, setup, pause),
        raw,
        region,
        sdk,
        now,
        canAnswerHangup,
        isEmergency = { emergency },
        contact = { contact },
    )

    @Test
    fun blockingThatIsNotSetUpOrSupportedPassesThrough() {
        val rules = listOf(block(MatcherType.PREFIX, "+1415"))
        assertNull(plan(rules, setup = false).flags)
        assertNull(plan(rules, sdk = 28).flags)
    }

    @Test
    fun noRulesPassesThroughWithoutLookingAnythingUp() {
        val result = planScreening(
            snapshot(emptyList()),
            "+14155551234",
            "US",
            36,
            now,
            false,
            isEmergency = { error("no emergency lookup needed") },
            contact = { error("no contact lookup needed") },
        )
        assertNull(result.flags)
    }

    @Test
    fun aMatchingBlockRuleRespondsAndRecordsTheEvent() {
        val rule = block(MatcherType.PREFIX, "+1415", BlockAction.REJECT)
        val result = plan(listOf(rule))
        assertEquals(screeningFlags(Outcome.REJECT), result.flags)
        val event = result.event!!
        assertEquals("+14155551234", event.number)
        assertEquals(BlockAction.REJECT, event.action)
        assertEquals(rule.id, event.ruleId)
        assertEquals("Block prefix +1415, reject", event.ruleSummary)
    }

    @Test
    fun aQuietRejectDoesNotRaiseANotification() {
        val flags = plan(listOf(block(MatcherType.PREFIX, "+1415"))).flags!!
        assertTrue(flags.disallow && flags.reject && flags.skipNotification)
        assertFalse(flags.silence)
    }

    @Test
    fun silenceRecordsAndNeverDisallows() {
        val result = plan(listOf(block(MatcherType.PREFIX, "+1415", BlockAction.SILENCE)))
        assertTrue(result.flags!!.silence)
        assertFalse(result.flags.disallow)
        assertEquals(BlockAction.SILENCE, result.event!!.action)
    }

    @Test
    fun aNationalNumberIsNormalizedWithTheRegionBeforeMatching() {
        val rules = listOf(block(MatcherType.PREFIX, "+1415"))
        assertNotNull(plan(rules, raw = "(415) 555-1234", region = "US").flags)
        assertNull(plan(rules, raw = "(415) 555-1234", region = null).flags)
    }

    @Test
    fun aCallWithNoNumberCanBeBlockedAndIsRecordedWithoutOne() {
        val result = plan(listOf(block(MatcherType.NO_NUMBER)), raw = null)
        assertNotNull(result.flags)
        assertNull(result.event!!.number)
    }

    @Test
    fun contactsEmergencyAndPauseFallThroughToFossify() {
        val rules = listOf(block(MatcherType.PREFIX, "+1415"))
        assertNull(plan(rules, contact = ContactStatus.CONTACT).flags)
        assertNull(plan(rules, contact = ContactStatus.UNKNOWN).flags)
        assertNull(plan(rules, emergency = true).flags)
        assertNull(plan(rules, pause = TimedPause(null, untilResume = true)).flags)
    }

    @Test
    fun anExplicitAllowRuleRespondsAllowWithoutAnEvent() {
        val result = plan(listOf(allow("+14155551234"), block(MatcherType.PREFIX, "+1415")))
        assertEquals(screeningFlags(Outcome.ALLOW), result.flags)
        assertNull(result.event)
    }

    @Test
    fun aNameRuleDefersToTheDialerAndFallsThrough() {
        val result = plan(listOf(block(MatcherType.NAME_WILDCARD, "likely*")))
        assertNull(result.flags)
        assertNull(result.event)
    }

    @Test
    fun aNumberRuleAboveANameRuleStillDecidesAtScreening() {
        val rules = listOf(block(MatcherType.PREFIX, "+1415"), block(MatcherType.NAME_WILDCARD, "likely*"))
        assertNotNull(plan(rules).flags)
    }

    @Test
    fun answerAndHangUpRejectsQuietlyWithoutTheDialerStage() {
        val result = plan(listOf(block(MatcherType.PREFIX, "+1415", BlockAction.ANSWER_HANGUP)))
        assertEquals(screeningFlags(Outcome.REJECT_QUIET), result.flags)
        assertEquals(BlockAction.REJECT_QUIET, result.event!!.action)
    }

    @Test
    fun answerAndHangUpSilencesAndLeavesTheEventToTheDialer() {
        val result = plan(listOf(block(MatcherType.PREFIX, "+1415", BlockAction.ANSWER_HANGUP)), canAnswerHangup = true)
        assertEquals(screeningFlags(Outcome.ANSWER_HANGUP), result.flags)
        assertNull(result.event)
    }

    @Test
    fun theClockDecidesWhichRulesApply() {
        val sundayOnly = TimeWindow(setOf(DayOfWeek.SUNDAY), 0, 0)
        val rule = block(MatcherType.PREFIX, "+1415").copy(window = sundayOnly)
        assertNull(plan(listOf(rule)).flags)
    }
}
