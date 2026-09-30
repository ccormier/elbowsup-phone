package com.keejii.elbowsup.core

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleSummaryTest {
    private fun rule(
        kind: RuleKind = RuleKind.BLOCK,
        matcher: MatcherType = MatcherType.PREFIX,
        pattern: String? = "+1415",
        emptyNameOnly: Boolean = false,
        action: BlockAction? = BlockAction.REJECT_QUIET,
        window: TimeWindow? = null,
    ) = Rule(1, true, kind, matcher, pattern, emptyNameOnly, window, action)

    @Test
    fun everyActionIsNamed() {
        assertEquals("Block prefix +1415, reject (hide missed call)", ruleSummary(rule()))
        assertEquals("Block prefix +1415, reject (show missed call)", ruleSummary(rule(action = BlockAction.REJECT)))
        assertEquals("Block prefix +1415, silence", ruleSummary(rule(action = BlockAction.SILENCE)))
        val hangUp = rule(action = BlockAction.ANSWER_HANGUP)
        assertEquals("Block prefix +1415, answer and hang up", ruleSummary(hangUp))
    }

    @Test
    fun anAllowRuleHasNoAction() {
        val allow = rule(RuleKind.ALLOW, MatcherType.EXACT, "+14155551234", action = null)
        assertEquals("Allow exact +14155551234", ruleSummary(allow))
    }

    @Test
    fun everyMatcherHasWording() {
        val name = rule(matcher = MatcherType.NAME_WILDCARD, pattern = "likely*")
        val empty = rule(matcher = MatcherType.EMPTY_NAME, pattern = null)
        val hidden = rule(matcher = MatcherType.NO_NUMBER, pattern = null)
        assertEquals("Block name likely*, reject (hide missed call)", ruleSummary(name))
        assertEquals("Block empty name, reject (hide missed call)", ruleSummary(empty))
        assertEquals("Block no number, reject (hide missed call)", ruleSummary(hidden))
    }

    @Test
    fun theFlagAndAWindowAreMentioned() {
        val night = TimeWindow(setOf(DayOfWeek.MONDAY), 22 * 60, 7 * 60)
        assertEquals(
            "Block prefix +1415, empty name only, 22:00-07:00, reject (hide missed call)",
            ruleSummary(rule(emptyNameOnly = true, window = night)),
        )
    }
}
