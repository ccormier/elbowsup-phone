package com.keejii.elbowsup.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleListTest {
    private fun block(id: Long, pattern: String, action: BlockAction = BlockAction.REJECT_QUIET) =
        Rule(id, true, RuleKind.BLOCK, MatcherType.PREFIX, pattern, null, action)

    private fun allow(id: Long, pattern: String) =
        Rule(id, true, RuleKind.ALLOW, MatcherType.EXACT, pattern, null, null)

    private val a = block(1, "+1415")
    private val b = block(2, "+1604")
    private val c = allow(3, "+14155551234")

    @Test
    fun aNewBlockGoesToTheBottomAndANewAllowToTheTop() {
        assertEquals(listOf(a, b), addRule(listOf(a), b))
        assertEquals(listOf(c, a), addRule(listOf(a), c))
    }

    @Test
    fun addingTheSameRuleAgainUpdatesItInPlaceAndDoesNotMoveIt() {
        val again = block(99, "+1415", BlockAction.SILENCE).copy(enabled = false)
        val result = addRule(listOf(a, b), again)
        assertEquals(listOf(1L, 2L), result.map { it.id })
        assertEquals(BlockAction.SILENCE, result[0].action)
        assertEquals(false, result[0].enabled)
    }

    @Test
    fun replacingKeepsPositionAndIdentityOfOthers() {
        val edited = a.copy(pattern = "+1416")
        assertEquals(listOf(edited, b), replaceRule(listOf(a, b), edited))
    }

    @Test
    fun replacingIsRefusedWhenAnotherRuleAlreadyHasThatIdentity() {
        assertNull(replaceRule(listOf(a, b), a.copy(pattern = "+1604")))
    }

    @Test
    fun replacingARuleWithItsOwnIdentityIsFine() {
        val edited = a.copy(action = BlockAction.REJECT)
        assertEquals(listOf(edited, b), replaceRule(listOf(a, b), edited))
    }

    @Test
    fun replacingAnUnknownIdIsRefused() {
        assertNull(replaceRule(listOf(a), b))
    }

    @Test
    fun deleteRemovesOnlyThatRule() {
        assertEquals(listOf(b), deleteRule(listOf(a, b), 1))
        assertEquals(listOf(a, b), deleteRule(listOf(a, b), 42))
    }

    @Test
    fun moveShiftsOneStepAndStopsAtTheEnds() {
        assertEquals(listOf(b, a, c), moveRule(listOf(a, b, c), 2, -1))
        assertEquals(listOf(a, c, b), moveRule(listOf(a, b, c), 2, 1))
        assertEquals(listOf(a, b, c), moveRule(listOf(a, b, c), 1, -1))
        assertEquals(listOf(a, b, c), moveRule(listOf(a, b, c), 3, 1))
        assertEquals(listOf(a, b, c), moveRule(listOf(a, b, c), 42, 1))
    }

    @Test
    fun enabledCanBeToggled() {
        assertEquals(false, setRuleEnabled(listOf(a, b), 2, false)[1].enabled)
        assertEquals(true, setRuleEnabled(listOf(a, b), 2, false)[0].enabled)
    }
}
