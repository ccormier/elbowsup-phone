package com.keejii.elbowsup.core

/** The blocked event already written for one call, so checking the call again does not write it twice. */
data class RecordedBlock(val id: Long, val action: BlockAction, val ruleId: Long?)

sealed interface RecordStep {
    /** Leave the log as it is. */
    object Keep : RecordStep

    /** Write the planned event, and drop the event with id [replacing] if the decision has changed. */
    data class Write(val replacing: Long?) : RecordStep
}

/**
 * A call can be checked more than once while it rings, for example when its caller ID name arrives
 * late. It still gets one event: the same decision is not written again, and a changed decision
 * replaces the earlier event instead of adding a second one.
 */
fun recordStep(previous: RecordedBlock?, planned: PlannedEvent?): RecordStep = when {
    planned == null -> RecordStep.Keep
    previous == null -> RecordStep.Write(replacing = null)
    previous.action == planned.action && previous.ruleId == planned.ruleId -> RecordStep.Keep
    else -> RecordStep.Write(replacing = previous.id)
}
