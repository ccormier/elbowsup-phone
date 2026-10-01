package com.keejii.elbowsup.storage

import com.keejii.elbowsup.core.BlockAction
import com.keejii.elbowsup.core.MatcherType
import com.keejii.elbowsup.core.Rule
import com.keejii.elbowsup.core.RuleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockerConfigDefaultsTest {
    private val prefs = FakePreferences()

    private fun start(): Pair<BlockerConfig, com.keejii.elbowsup.core.BlockerSnapshot> {
        val config = BlockerConfig(prefs)
        return config to config.loadWithDefaults()
    }

    private fun names(rules: List<Rule>) = rules.map { it.pattern }

    @Test
    fun theFirstRunStartsWithTheTwoDefaultRulesAndSavesThem() {
        val (_, first) = start()
        assertEquals(listOf("Likely Spam", "Likely Fraud"), names(first.rules))
        assertEquals(names(first.rules), names(BlockerConfig(prefs).load().rules))
    }

    @Test
    fun aRuleTheUserDeletedDoesNotComeBack() {
        val (config, first) = start()
        config.save(first, first.copy(rules = first.rules.filter { it.pattern == "Likely Fraud" }))
        assertEquals(listOf("Likely Fraud"), names(start().second.rules))
    }

    @Test
    fun deletingEveryRuleLeavesTheListEmptyOnTheNextStart() {
        val (config, first) = start()
        config.save(first, first.copy(rules = emptyList()))
        assertTrue(start().second.rules.isEmpty())
    }

    @Test
    fun rulesThatAlreadyExistOnTheFirstRunAreLeftAlone() {
        val own = Rule(5, true, RuleKind.BLOCK, MatcherType.PREFIX, "+1415", null, BlockAction.SILENCE)
        val config = BlockerConfig(prefs)
        config.save(config.load(), config.load().copy(rules = listOf(own)))
        assertEquals(listOf(own), config.loadWithDefaults().rules)

        val loaded = config.load()
        config.save(loaded, loaded.copy(rules = emptyList()))
        assertTrue(BlockerConfig(prefs).loadWithDefaults().rules.isEmpty())
    }

    @Test
    fun ruleIdsAfterTheDefaultsAreNeverReused() {
        val (config, first) = start()
        assertTrue(config.nextId() > first.rules.maxOf { it.id })
    }
}
