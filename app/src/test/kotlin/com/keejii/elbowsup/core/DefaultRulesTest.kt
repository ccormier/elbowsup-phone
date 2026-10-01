package com.keejii.elbowsup.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRulesTest {
    private var last = 0L
    private val nextId = { ++last }

    private val userRule = Rule(99, true, RuleKind.BLOCK, MatcherType.PREFIX, "+1415", null, BlockAction.SILENCE)

    @Test
    fun anEmptyListStartsWithLikelySpamThenLikelyFraud() {
        val rules = withDefaultRules(emptyList(), nextId)
        assertEquals(listOf("Likely Spam", "Likely Fraud"), rules.map { it.pattern })
    }

    @Test
    fun eachDefaultIsItsOwnBlockRuleOnTheFullCallerIdName() {
        val rules = withDefaultRules(emptyList(), nextId)
        assertTrue(rules.all { it.enabled && it.kind == RuleKind.BLOCK && it.matcher == MatcherType.NAME_WILDCARD })
        assertTrue(rules.none { it.window != null })
        assertFalse(rules.any { it.pattern.orEmpty().contains('*') || it.pattern.orEmpty().contains('?') })
        assertEquals(setOf(BlockAction.REJECT_QUIET), rules.map { it.action }.toSet())
        assertEquals(rules.size, rules.map { it.id }.toSet().size)
    }

    @Test
    fun existingRulesAreLeftAloneAndNoIdIsUsed() {
        val rules = withDefaultRules(listOf(userRule)) { error("no id needed") }
        assertEquals(listOf(userRule), rules)
    }
}
