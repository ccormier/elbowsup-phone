package com.keejii.elbowsup.core

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEngineTest {
    private val number = "+14155551234"

    private var nextId = 1L

    private fun block(
        matcher: MatcherType,
        pattern: String? = null,
        action: BlockAction = BlockAction.REJECT_QUIET,
        window: TimeWindow? = null,
        enabled: Boolean = true,
    ) = Rule(nextId++, enabled, RuleKind.BLOCK, matcher, pattern, window, action)

    private fun allow(matcher: MatcherType, pattern: String? = null) =
        Rule(nextId++, true, RuleKind.ALLOW, matcher, pattern, null, null)

    private fun env(
        stage: Stage = Stage.LATE,
        isEmergency: Boolean = false,
        isContact: Boolean = false,
        paused: Boolean = false,
        canAnswerHangup: Boolean = true,
        day: DayOfWeek = DayOfWeek.MONDAY,
        minuteOfDay: Int = 15 * 60,
    ) = Env(stage, isEmergency, isContact, paused, canAnswerHangup, day, minuteOfDay)

    private fun outcome(rules: List<Rule>, num: String? = number, name: String? = null, env: Env = env()) =
        evaluate(rules, CallInfo(num, name), env).outcome

    @Test
    fun noRulesAllows() {
        assertEquals(Outcome.ALLOW, outcome(emptyList()))
    }

    @Test
    fun aMatchingBlockRuleAppliesItsAction() {
        val rules = listOf(block(MatcherType.PREFIX, "+1415", BlockAction.SILENCE))
        assertEquals(Outcome.SILENCE, outcome(rules))
        assertEquals(Outcome.ALLOW, outcome(rules, "+16045551234"))
    }

    @Test
    fun exactMatchesTheWholeNumberOnly() {
        val rules = listOf(block(MatcherType.EXACT, number))
        assertEquals(Outcome.REJECT_QUIET, outcome(rules))
        assertEquals(Outcome.ALLOW, outcome(rules, "+14155551235"))
        assertEquals(Outcome.ALLOW, outcome(rules, null))
    }

    @Test
    fun numberRulesNeverMatchACallWithoutANumber() {
        val rules = listOf(block(MatcherType.PREFIX, "+1"), block(MatcherType.EXACT, number))
        assertEquals(Outcome.ALLOW, outcome(rules, null))
    }

    @Test
    fun noNumberMatchesOnlyAHiddenCaller() {
        val rules = listOf(block(MatcherType.NO_NUMBER))
        assertEquals(Outcome.REJECT_QUIET, outcome(rules, null))
        assertEquals(Outcome.ALLOW, outcome(rules))
    }

    @Test
    fun firstMatchWinsEvenWhenALaterPrefixIsLonger() {
        val rules = listOf(
            block(MatcherType.PREFIX, "+1", BlockAction.REJECT),
            block(MatcherType.PREFIX, "+1415555", BlockAction.SILENCE),
        )
        val decision = evaluate(rules, CallInfo(number, null), env())
        assertEquals(Outcome.REJECT, decision.outcome)
        assertEquals(rules[0], decision.rule)
    }

    @Test
    fun anAllowAboveABlockRingsAndABlockAboveAnAllowBlocks() {
        val allowFirst = listOf(allow(MatcherType.EXACT, number), block(MatcherType.PREFIX, "+1415"))
        val blockFirst = listOf(block(MatcherType.PREFIX, "+1415"), allow(MatcherType.EXACT, number))
        assertEquals(Outcome.ALLOW, outcome(allowFirst))
        assertEquals(Outcome.REJECT_QUIET, outcome(blockFirst))
    }

    @Test
    fun contactsAndEmergencyNumbersAllowBeforeTheList() {
        val rules = listOf(block(MatcherType.PREFIX, "+1415"))
        assertEquals(Outcome.ALLOW, outcome(rules, env = env(isContact = true)))
        assertEquals(Outcome.ALLOW, outcome(rules, env = env(isEmergency = true)))
    }

    @Test
    fun aPauseAllowsEverything() {
        val rules = listOf(block(MatcherType.PREFIX, "+1415"))
        assertEquals(Outcome.ALLOW, outcome(rules, env = env(paused = true)))
    }

    @Test
    fun aDisabledRuleIsSkipped() {
        val rules = listOf(
            block(MatcherType.PREFIX, "+1415", enabled = false),
            block(MatcherType.PREFIX, "+1", BlockAction.SILENCE),
        )
        assertEquals(Outcome.SILENCE, outcome(rules))
    }

    @Test
    fun aCustomWindowOnlyAppliesInsideItself() {
        val weekdays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
        val night = TimeWindow(weekdays, 22 * 60, 7 * 60)
        val rules = listOf(block(MatcherType.PREFIX, "+1415", window = night))
        fun at(day: DayOfWeek, hour: Int) = outcome(rules, env = env(day = day, minuteOfDay = hour * 60))
        assertEquals(Outcome.ALLOW, at(DayOfWeek.MONDAY, 15))
        assertEquals(Outcome.REJECT_QUIET, at(DayOfWeek.MONDAY, 23))
        assertEquals(Outcome.REJECT_QUIET, at(DayOfWeek.SATURDAY, 3))
        assertEquals(Outcome.ALLOW, at(DayOfWeek.MONDAY, 3))
    }

    @Test
    fun aWindowedRuleThatDoesNotApplyDoesNotStopTheWalk() {
        val never = TimeWindow(setOf(DayOfWeek.SUNDAY), 0, 60)
        val rules = listOf(
            block(MatcherType.PREFIX, "+1", BlockAction.REJECT, window = never),
            block(MatcherType.PREFIX, "+1415", BlockAction.SILENCE),
        )
        assertEquals(Outcome.SILENCE, outcome(rules))
    }

    @Test
    fun aNameWildcardMatchesTheLabelCaseInsensitively() {
        val rules = listOf(block(MatcherType.NAME_WILDCARD, "likely*"))
        assertEquals(Outcome.REJECT_QUIET, outcome(rules, name = "Likely Spam"))
        assertEquals(Outcome.ALLOW, outcome(rules, name = "Bob"))
    }

    @Test
    fun aWildcardNeverMatchesAMissingName() {
        val rules = listOf(block(MatcherType.NAME_WILDCARD, "*"))
        assertEquals(Outcome.ALLOW, outcome(rules, name = null))
    }

    @Test
    fun wildcardMetacharactersAreLiteralExceptStarAndQuestion() {
        val rules = listOf(block(MatcherType.NAME_WILDCARD, "a.c(1)?"))
        assertEquals(Outcome.REJECT_QUIET, outcome(rules, name = "a.c(1)x"))
        assertEquals(Outcome.ALLOW, outcome(rules, name = "abc(1)x"))
    }

    @Test
    fun emptyNameNeedsANumberAndNoRealName() {
        val rules = listOf(block(MatcherType.EMPTY_NAME))
        assertEquals(Outcome.REJECT_QUIET, outcome(rules, name = null))
        assertEquals(Outcome.ALLOW, outcome(rules, name = "Bob"))
        assertEquals(Outcome.ALLOW, outcome(rules, num = null, name = null))
    }

    @Test
    fun theEarlyStageDefersInsteadOfGuessingAName() {
        val wildcard = listOf(block(MatcherType.NAME_WILDCARD, "likely*"))
        val emptyName = listOf(block(MatcherType.EMPTY_NAME))
        assertEquals(Outcome.DEFER, outcome(wildcard, env = env(Stage.EARLY)))
        assertEquals(Outcome.DEFER, outcome(emptyName, env = env(Stage.EARLY)))
    }

    @Test
    fun theEarlyStageDoesNotDeferWhenThereIsNoNumberToJudge() {
        val emptyName = listOf(block(MatcherType.EMPTY_NAME))
        assertEquals(Outcome.ALLOW, outcome(emptyName, num = null, env = env(Stage.EARLY)))
    }

    @Test
    fun aNumberRuleAboveANameRuleDecidesEarly() {
        val rules = listOf(
            block(MatcherType.PREFIX, "+1415", BlockAction.REJECT),
            block(MatcherType.NAME_WILDCARD, "likely*"),
        )
        assertEquals(Outcome.REJECT, outcome(rules, env = env(Stage.EARLY)))
    }

    @Test
    fun aNameRuleAboveANumberRuleDefersBecauseItMightWin() {
        val rules = listOf(
            block(MatcherType.NAME_WILDCARD, "likely*"),
            block(MatcherType.PREFIX, "+1415", BlockAction.REJECT),
        )
        assertEquals(Outcome.DEFER, outcome(rules, env = env(Stage.EARLY)))
    }

    @Test
    fun aDisabledOrOffWindowNameRuleDoesNotDefer() {
        val never = TimeWindow(setOf(DayOfWeek.SUNDAY), 0, 60)
        val rules = listOf(
            block(MatcherType.NAME_WILDCARD, "likely*", enabled = false),
            block(MatcherType.EMPTY_NAME, window = never),
        )
        assertEquals(Outcome.ALLOW, outcome(rules, env = env(Stage.EARLY)))
    }

    @Test
    fun anAllowRuleNeedingANameAlsoDefers() {
        val rules = listOf(allow(MatcherType.NAME_WILDCARD, "Dr*"), block(MatcherType.PREFIX, "+1415"))
        assertEquals(Outcome.DEFER, outcome(rules, env = env(Stage.EARLY)))
    }

    @Test
    fun contactsPauseAndEmergencyBeatDeferral() {
        val rules = listOf(block(MatcherType.EMPTY_NAME))
        assertEquals(Outcome.ALLOW, outcome(rules, env = env(Stage.EARLY, isContact = true)))
        assertEquals(Outcome.ALLOW, outcome(rules, env = env(Stage.EARLY, paused = true)))
        assertEquals(Outcome.ALLOW, outcome(rules, env = env(Stage.EARLY, isEmergency = true)))
    }

    @Test
    fun answerAndHangUpFallsBackToQuietRejectWithoutSupport() {
        val rules = listOf(block(MatcherType.PREFIX, "+1415", BlockAction.ANSWER_HANGUP))
        assertEquals(Outcome.ANSWER_HANGUP, outcome(rules))
        val decision = evaluate(rules, CallInfo(number, null), env(canAnswerHangup = false))
        assertEquals(Outcome.REJECT_QUIET, decision.outcome)
        assertEquals(BlockAction.REJECT_QUIET, decision.recordedAction)
    }

    @Test
    fun aDecisionNamesTheRuleAndTheRecordedAction() {
        val rule = block(MatcherType.PREFIX, "+1415", BlockAction.SILENCE)
        val decision = evaluate(listOf(rule), CallInfo(number, null), env())
        assertEquals(rule, decision.rule)
        assertEquals(BlockAction.SILENCE, decision.recordedAction)
        assertNull(evaluate(emptyList(), CallInfo(number, null), env()).recordedAction)
    }
}
