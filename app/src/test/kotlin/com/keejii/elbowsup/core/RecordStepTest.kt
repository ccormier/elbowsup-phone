package com.keejii.elbowsup.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordStepTest {
    private fun planned(action: BlockAction, ruleId: Long? = 1) = PlannedEvent("+14155551234", action, ruleId, "rule")

    private fun recorded(action: BlockAction, ruleId: Long? = 1, id: Long = 7) = RecordedBlock(id, action, ruleId)

    @Test
    fun aCallWithNothingToRecordKeepsWhatIsThere() {
        assertEquals(RecordStep.Keep, recordStep(null, null))
        assertEquals(RecordStep.Keep, recordStep(recorded(BlockAction.SILENCE), null))
    }

    @Test
    fun theFirstEventForACallIsWritten() {
        assertEquals(RecordStep.Write(replacing = null), recordStep(null, planned(BlockAction.SILENCE)))
    }

    @Test
    fun checkingTheSameDecisionAgainDoesNotWriteItAgain() {
        assertEquals(RecordStep.Keep, recordStep(recorded(BlockAction.SILENCE), planned(BlockAction.SILENCE)))
    }

    @Test
    fun aChangedActionReplacesTheEarlierEvent() {
        val step = recordStep(recorded(BlockAction.SILENCE, id = 7), planned(BlockAction.REJECT_QUIET))
        assertEquals(RecordStep.Write(replacing = 7), step)
    }

    @Test
    fun aDifferentRuleWithTheSameActionReplacesTheEarlierEvent() {
        val previous = recorded(BlockAction.SILENCE, ruleId = 1, id = 7)
        val step = recordStep(previous, planned(BlockAction.SILENCE, ruleId = 2))
        assertEquals(RecordStep.Write(replacing = 7), step)
    }
}
