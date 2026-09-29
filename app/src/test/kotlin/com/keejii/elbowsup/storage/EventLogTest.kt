package com.keejii.elbowsup.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EventLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun event(time: Long, number: String? = "+14155551234", action: String = "REJECT_QUIET") =
        BlockedEvent(0, time, number, "Likely Spam", action, 7L, "Block prefix +1415, reject quietly")

    private fun log(cap: Int = 500, name: String = "events.jsonl") = EventLog(File(folder.root, name), cap)

    @Test
    fun appendAssignsIncreasingIdsAndListsNewestFirst() {
        val log = log()
        val first = log.append(event(1_000))
        val second = log.append(event(2_000))
        assertTrue(second > first)
        assertEquals(listOf(second, first), log.all().map { it.id })
    }

    @Test
    fun eventsSurviveAReload() {
        val log = log()
        val id = log.append(event(1_000, action = "SILENCE"))
        val reloaded = log()
        assertEquals(listOf(id), reloaded.all().map { it.id })
        assertEquals("SILENCE", reloaded.all().single().action)
        assertEquals("Likely Spam", reloaded.all().single().displayName)
        assertTrue(reloaded.append(event(2_000)) > id)
    }

    @Test
    fun aNullNumberAndNameRoundTrip() {
        val log = log()
        log.append(BlockedEvent(0, 5L, null, null, "REJECT", null, ""))
        val stored = log().all().single()
        assertNull(stored.number)
        assertNull(stored.displayName)
        assertNull(stored.ruleId)
    }

    @Test
    fun removeDeletesOnlyThatEventEvenAfterAReload() {
        val log = log()
        val keep = log.append(event(1_000))
        val drop = log.append(event(2_000))
        assertTrue(log.remove(drop))
        assertFalse(log.remove(drop))
        assertEquals(listOf(keep), log().all().map { it.id })
    }

    @Test
    fun theOldestEventsAreDroppedPastTheCap() {
        val log = log(cap = 5)
        val ids = (1..80).map { log.append(event(it * 1_000L)) }
        val kept = log.all().map { it.id }
        assertTrue(kept.size <= 5 + EventLog.PRUNE_SLACK)
        assertEquals(ids.last(), kept.first())
        assertTrue(log(cap = 5).all().size <= 5)
    }

    @Test
    fun aCorruptLineIsSkipped() {
        val file = File(folder.root, "events.jsonl")
        val good = EventLog(file, 500)
        val id = good.append(event(1_000))
        file.appendText("{broken\n\nnot json at all\n")
        assertEquals(listOf(id), EventLog(file, 500).all().map { it.id })
    }

    @Test
    fun anUnwritableFileNeverThrowsAndTheEventStaysInMemory() {
        val blocker = folder.newFile("blocker")
        val log = EventLog(File(blocker, "events.jsonl"), 500)
        val id = log.append(event(1_000))
        assertEquals(listOf(id), log.all().map { it.id })
    }

    @Test
    fun aMissingFileIsEmpty() {
        assertTrue(log(name = "never-written.jsonl").all().isEmpty())
    }
}
