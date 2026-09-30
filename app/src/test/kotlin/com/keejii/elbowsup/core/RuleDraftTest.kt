package com.keejii.elbowsup.core

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleDraftTest {
    private fun valid(draft: RuleDraft, region: String? = "US"): Rule =
        (draft.toRule(region) as DraftResult.Valid).rule

    private fun problem(draft: RuleDraft, region: String? = "US"): DraftProblem =
        (draft.toRule(region) as DraftResult.Invalid).problem

    @Test
    fun aPrefixKeepsAnInternationalFormAsTyped() {
        assertEquals("+1415", valid(RuleDraft(patternText = "+1 (415)")).pattern)
    }

    @Test
    fun aPrefixWithoutAPlusGetsTheCountryCodeOfTheRegion() {
        assertEquals("+1415", valid(RuleDraft(patternText = "415")).pattern)
        assertEquals("+44207", valid(RuleDraft(patternText = "207"), region = "GB").pattern)
        assertEquals("415", valid(RuleDraft(patternText = "415"), region = null).pattern)
    }

    @Test
    fun anExactNumberIsNormalizedLikeTheCallIs() {
        val rule = valid(RuleDraft(matcher = MatcherType.EXACT, patternText = "(415) 555-1234"))
        assertEquals("+14155551234", rule.pattern)
    }

    @Test
    fun aNumberPatternMustBeDigits() {
        assertEquals(DraftProblem.NOT_A_NUMBER, problem(RuleDraft(patternText = "abc")))
        assertEquals(DraftProblem.NOT_A_NUMBER, problem(RuleDraft(matcher = MatcherType.EXACT, patternText = "+")))
        assertEquals(DraftProblem.PATTERN_REQUIRED, problem(RuleDraft(patternText = "  ")))
    }

    @Test
    fun aNamePatternIsTrimmedAndRequired() {
        val name = RuleDraft(matcher = MatcherType.NAME_WILDCARD)
        assertEquals("likely*", valid(name.copy(patternText = "  likely* ")).pattern)
        assertEquals(DraftProblem.PATTERN_REQUIRED, problem(name.copy(patternText = "")))
    }

    @Test
    fun emptyNameAndNoNumberTakeNoPattern() {
        assertNull(valid(RuleDraft(matcher = MatcherType.EMPTY_NAME, patternText = "junk")).pattern)
        assertNull(valid(RuleDraft(matcher = MatcherType.NO_NUMBER, patternText = "junk")).pattern)
    }

    @Test
    fun anAllowRuleHasNoActionAndABlockRuleHasOne() {
        assertNull(valid(RuleDraft(kind = RuleKind.ALLOW, patternText = "415", action = BlockAction.SILENCE)).action)
        assertEquals(BlockAction.SILENCE, valid(RuleDraft(patternText = "415", action = BlockAction.SILENCE)).action)
    }

    @Test
    fun alwaysHasNoWindowAndACustomWindowNeedsDays() {
        assertNull(valid(RuleDraft(patternText = "415")).window)
        val custom = RuleDraft(
            patternText = "415",
            customWindow = true,
            days = setOf(DayOfWeek.MONDAY),
            startMinute = 60,
            endMinute = 120,
        )
        assertEquals(TimeWindow(setOf(DayOfWeek.MONDAY), 60, 120), valid(custom).window)
        assertEquals(DraftProblem.NO_DAYS, problem(custom.copy(days = emptySet())))
    }

    @Test
    fun aRuleRoundTripsThroughItsDraft() {
        val window = TimeWindow(setOf(DayOfWeek.FRIDAY), 22 * 60, 7 * 60)
        val rule = Rule(7, false, RuleKind.BLOCK, MatcherType.PREFIX, "+1415", window, BlockAction.ANSWER_HANGUP)
        assertEquals(rule, valid(draftOf(rule)))
        val hidden = Rule(8, true, RuleKind.BLOCK, MatcherType.NO_NUMBER, null, null, BlockAction.REJECT)
        assertEquals(hidden, valid(draftOf(hidden)))
    }

    @Test
    fun theDraftKeepsTheIdAndEnabledFlag() {
        val rule = valid(RuleDraft(id = 9, enabled = false, patternText = "415"))
        assertEquals(9L, rule.id)
        assertFalse(rule.enabled)
    }
}
