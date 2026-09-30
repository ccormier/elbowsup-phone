package com.keejii.elbowsup.storage

import kotlin.math.abs

/** The call log stamps a call when it starts; our event is written a moment after, once it is decided. */
const val EVENT_BEFORE_MILLIS = 2_000L
const val EVENT_AFTER_MILLIS = 20_000L

/**
 * The blocked event that belongs to a call log row: same number (both hidden counts) within the
 * window around the call's start, the closest in time, and the newest on a tie. [events] is newest
 * first, as [EventLog.all] returns it. Both numbers must be in normalized form.
 */
fun matchBlockedEvent(events: List<BlockedEvent>, normalizedNumber: String?, callStartMillis: Long): BlockedEvent? {
    var best: BlockedEvent? = null
    var bestGap = Long.MAX_VALUE
    for (event in events) {
        val offset = event.timeEpochMillis - callStartMillis
        if (event.number != normalizedNumber || offset < -EVENT_BEFORE_MILLIS || offset > EVENT_AFTER_MILLIS) continue
        val gap = abs(offset)
        if (gap < bestGap) {
            best = event
            bestGap = gap
        }
    }
    return best
}
