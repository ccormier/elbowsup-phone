package com.keejii.elbowsup.storage

import com.keejii.elbowsup.core.BlockAction
import com.keejii.elbowsup.core.MatcherType
import com.keejii.elbowsup.core.PauseSchedule
import com.keejii.elbowsup.core.Rule
import com.keejii.elbowsup.core.RuleKind
import com.keejii.elbowsup.core.TimeWindow
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockerJsonTest {
    private val window = TimeWindow(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 22 * 60, 7 * 60)

    private val block = Rule(3, true, RuleKind.BLOCK, MatcherType.PREFIX, "+1415", window, BlockAction.SILENCE)
    private val allow = Rule(4, false, RuleKind.ALLOW, MatcherType.EXACT, "+14155551234", null, null)
    private val hidden = Rule(5, true, RuleKind.BLOCK, MatcherType.NO_NUMBER, null, null, BlockAction.REJECT)

    @Test
    fun rulesRoundTripInOrder() {
        val rules = listOf(block, allow, hidden)
        assertEquals(rules, BlockerJson.rulesFromJson(BlockerJson.rulesToJson(rules)))
    }

    @Test
    fun everyActionRoundTrips() {
        for (action in BlockAction.entries) {
            val rule = block.copy(action = action)
            assertEquals(listOf(rule), BlockerJson.rulesFromJson(BlockerJson.rulesToJson(listOf(rule))))
        }
    }

    @Test
    fun missingOrCorruptJsonIsAnEmptyList() {
        assertTrue(BlockerJson.rulesFromJson(null).isEmpty())
        assertTrue(BlockerJson.rulesFromJson("").isEmpty())
        assertTrue(BlockerJson.rulesFromJson("{not json").isEmpty())
        assertTrue(BlockerJson.rulesFromJson("{\"a\":1}").isEmpty())
    }

    @Test
    fun aRuleFromANewerVersionIsDroppedAndTheRestKept() {
        val json = BlockerJson.rulesToJson(listOf(block, allow))
            .replace("\"PREFIX\"", "\"REGEX\"")
        assertEquals(listOf(allow), BlockerJson.rulesFromJson(json))
    }

    @Test
    fun aRuleSavedWithTheRemovedEmptyNameFlagIsDroppedNotWidened() {
        val ticked = "{\"id\":1,\"kind\":\"BLOCK\",\"matcher\":\"PREFIX\",\"pattern\":\"+1415\"," +
            "\"emptyNameOnly\":true,\"action\":\"REJECT\"}"
        val unticked = "{\"id\":2,\"kind\":\"BLOCK\",\"matcher\":\"PREFIX\",\"pattern\":\"+1604\"," +
            "\"emptyNameOnly\":false,\"action\":\"REJECT\"}"
        assertEquals(listOf(2L), BlockerJson.rulesFromJson("[$ticked,$unticked]").map { it.id })
    }

    @Test
    fun aBlockRuleWithAnUnknownActionIsDropped() {
        val json = BlockerJson.rulesToJson(listOf(block, hidden)).replace("\"SILENCE\"", "\"EXPLODE\"")
        assertEquals(listOf(hidden), BlockerJson.rulesFromJson(json))
    }

    @Test
    fun schedulesRoundTrip() {
        val schedules = listOf(PauseSchedule(1, "Work", true, window), PauseSchedule(2, "Sleep", false, window))
        assertEquals(schedules, BlockerJson.schedulesFromJson(BlockerJson.schedulesToJson(schedules)))
        assertTrue(BlockerJson.schedulesFromJson("garbage").isEmpty())
        assertTrue(BlockerJson.schedulesFromJson(null).isEmpty())
    }
}
