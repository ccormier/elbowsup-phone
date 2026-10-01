package com.keejii.elbowsup.core

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialerPlanTest {
    private val now = ZonedDateTime.of(2026, 9, 28, 15, 0, 0, 0, ZoneId.of("America/Toronto"))
    private var nextId = 1L

    private fun block(matcher: MatcherType, pattern: String?, action: BlockAction = BlockAction.REJECT_QUIET) =
        Rule(nextId++, true, RuleKind.BLOCK, matcher, pattern, null, action)

    private fun plan(
        rules: List<Rule>,
        name: String? = null,
        raw: String? = "+14155551234",
        silentRequested: Boolean = false,
        screeningHeld: Boolean = true,
        setup: Boolean = true,
        sdk: Int = 36,
        emergency: Boolean = false,
        contact: ContactStatus = ContactStatus.NOT_CONTACT,
        canAnswerHangup: Boolean = true,
        passed: Boolean = false,
    ) = planDialer(
        BlockerSnapshot(rules, emptyList(), TimedPause.NONE, setup),
        raw,
        name,
        "US",
        sdk,
        now,
        canAnswerHangup,
        silentRequested,
        screeningHeld,
        passed,
        isEmergency = { emergency },
        contact = { contact },
    )

    @Test
    fun withoutAVerdictTheCallRingsUnlessScreeningAskedForSilence() {
        assertEquals(DialerAction.RING, plan(emptyList()).action)
        assertEquals(DialerAction.NO_RING, plan(emptyList(), silentRequested = true).action)
    }

    @Test
    fun blockingThatIsNotSetUpOrSupportedRings() {
        val rules = listOf(block(MatcherType.NAME_WILDCARD, "Likely*"))
        assertEquals(DialerAction.RING, plan(rules, name = "Likely Spam", setup = false).action)
        assertEquals(DialerAction.RING, plan(rules, name = "Likely Spam", sdk = 28).action)
    }

    @Test
    fun withoutTheScreeningRoleBlockingIsOffAtTheDialerToo() {
        val rules = listOf(block(MatcherType.PREFIX, "+1415"), block(MatcherType.NAME_WILDCARD, "Likely*"))
        val byNumber = plan(rules, screeningHeld = false)
        assertEquals(DialerAction.RING, byNumber.action)
        assertNull(byNumber.event)
        assertEquals(DialerAction.RING, plan(rules, name = "Likely Spam", screeningHeld = false).action)
        assertEquals(DialerAction.NO_RING, plan(rules, screeningHeld = false, silentRequested = true).action)
    }

    @Test
    fun aCallThatUsedUpTheNextCallPauseIsNotBlockedAtTheLateStageEither() {
        val rules = listOf(block(MatcherType.NAME_WILDCARD, "Likely*"), block(MatcherType.PREFIX, "+1415"))
        val result = plan(rules, name = "Likely Spam", passed = true)
        assertEquals(DialerAction.RING, result.action)
        assertNull(result.event)
        val silenced = plan(rules, name = "Likely Spam", passed = true, silentRequested = true)
        assertEquals(DialerAction.NO_RING, silenced.action)
    }

    @Test
    fun noRulesLooksNothingUp() {
        val result = planDialer(
            BlockerSnapshot(emptyList(), emptyList(), TimedPause.NONE, true),
            "+14155551234",
            null,
            "US",
            36,
            now,
            true,
            false,
            true,
            false,
            isEmergency = { error("no emergency lookup needed") },
            contact = { error("no contact lookup needed") },
        )
        assertEquals(DialerAction.RING, result.action)
    }

    @Test
    fun aNameRuleRejectsOnceTheCarrierNameIsKnown() {
        val rule = block(MatcherType.NAME_WILDCARD, "Likely*", BlockAction.REJECT_QUIET)
        val result = plan(listOf(rule), name = "Likely Spam")
        assertEquals(DialerAction.REJECT, result.action)
        val event = result.event!!
        assertEquals("+14155551234", event.number)
        assertEquals(BlockAction.REJECT_QUIET, event.action)
        assertEquals(rule.id, event.ruleId)
    }

    @Test
    fun aNameRuleThatDoesNotMatchRings() {
        val rules = listOf(block(MatcherType.NAME_WILDCARD, "Likely*"))
        assertEquals(DialerAction.RING, plan(rules, name = "Grandma").action)
        assertEquals(DialerAction.RING, plan(rules, name = null).action)
    }

    @Test
    fun emptyNameRuleMatchesOnlyWhenThereIsNoRealName() {
        val rules = listOf(block(MatcherType.EMPTY_NAME, null))
        assertEquals(DialerAction.REJECT, plan(rules, name = null).action)
        assertEquals(DialerAction.RING, plan(rules, name = "Bank of Somewhere").action)
    }

    @Test
    fun contactsAndEmergencyNumbersAreNeverBlocked() {
        val rules = listOf(block(MatcherType.NAME_WILDCARD, "*"))
        assertEquals(DialerAction.RING, plan(rules, name = "X", contact = ContactStatus.CONTACT).action)
        assertEquals(DialerAction.RING, plan(rules, name = "X", contact = ContactStatus.UNKNOWN).action)
        assertEquals(DialerAction.RING, plan(rules, name = "X", emergency = true).action)
    }

    @Test
    fun aSilenceRuleStopsTheRingAndIsRecordedOnlyIfScreeningDidNotAlready() {
        val rules = listOf(block(MatcherType.NAME_WILDCARD, "Likely*", BlockAction.SILENCE))
        val late = plan(rules, name = "Likely Spam")
        assertEquals(DialerAction.NO_RING, late.action)
        assertEquals(BlockAction.SILENCE, late.event!!.action)

        val alreadyRecorded = plan(rules, name = "Likely Spam", silentRequested = true)
        assertEquals(DialerAction.NO_RING, alreadyRecorded.action)
        assertNull(alreadyRecorded.event)
    }

    @Test
    fun aSilenceRuleThatScreeningAlreadyHandledIsNotRecordedAgain() {
        val rules = listOf(block(MatcherType.PREFIX, "+1415", BlockAction.SILENCE))
        val result = plan(rules, silentRequested = true)
        assertEquals(DialerAction.NO_RING, result.action)
        assertNull(result.event)
    }

    @Test
    fun answerAndHangUpAnswersAndIsAlwaysRecordedHere() {
        val rules = listOf(block(MatcherType.PREFIX, "+1415", BlockAction.ANSWER_HANGUP))
        val result = plan(rules, silentRequested = true)
        assertEquals(DialerAction.ANSWER_HANGUP, result.action)
        assertEquals(BlockAction.ANSWER_HANGUP, result.event!!.action)
    }

    @Test
    fun answerAndHangUpFallsBackToARejectWhenItIsUnavailable() {
        val rules = listOf(block(MatcherType.PREFIX, "+1415", BlockAction.ANSWER_HANGUP))
        val result = plan(rules, canAnswerHangup = false)
        assertEquals(DialerAction.REJECT, result.action)
        assertEquals(BlockAction.REJECT_QUIET, result.event!!.action)
    }

    @Test
    fun anAllowRuleBeatsALaterBlockRule() {
        val allow = Rule(nextId++, true, RuleKind.ALLOW, MatcherType.NAME_WILDCARD, "Likely Friend", null, null)
        val rules = listOf(allow, block(MatcherType.NAME_WILDCARD, "Likely*"))
        val result = plan(rules, name = "Likely Friend")
        assertEquals(DialerAction.RING, result.action)
        assertNull(result.event)
    }

    @Test
    fun theRuleSummaryIsStoredWithTheEvent() {
        val rule = block(MatcherType.NAME_WILDCARD, "Likely*")
        assertEquals(ruleSummary(rule), plan(listOf(rule), name = "Likely Spam").event!!.ruleSummary)
    }
}
