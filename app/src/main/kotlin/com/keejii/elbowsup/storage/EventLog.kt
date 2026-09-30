package com.keejii.elbowsup.storage

import com.google.gson.Gson
import java.io.File
import java.io.IOException

data class BlockedEvent(
    val id: Long,
    val timeEpochMillis: Long,
    val number: String?,
    val displayName: String?,
    /** A `BlockAction` name; kept as text so an action this version does not know still shows. */
    val action: String,
    val ruleId: Long?,
    val ruleSummary: String,
)

private data class EventDto(
    val id: Long = 0,
    val time: Long = 0,
    val number: String? = null,
    val name: String? = null,
    val action: String? = null,
    val ruleId: Long? = null,
    val summary: String? = null,
)

/**
 * A capped log of what was blocked, one JSON line per event. Appending is cheap enough for the call
 * path and never throws: if the file cannot be written the event is still kept in memory.
 */
class EventLog(private val file: File, private val cap: Int = DEFAULT_CAP) {
    private val gson = Gson()
    private val events = ArrayList<BlockedEvent>()
    private var nextId = 1L

    init {
        load()
    }

    /** Stores the event under a new id and returns that id; the id already on [event] is ignored. */
    @Synchronized
    fun append(event: BlockedEvent): Long {
        val stored = event.copy(id = nextId++)
        events += stored
        if (events.size > cap + PRUNE_SLACK) {
            trim()
            rewrite()
        } else {
            write { file.appendText(gson.toJson(stored.toDto()) + "\n") }
        }
        return stored.id
    }

    @Synchronized
    fun remove(id: Long): Boolean {
        if (!events.removeAll { it.id == id }) return false
        rewrite()
        return true
    }

    @Synchronized
    fun clear() {
        if (events.isEmpty()) return
        events.clear()
        rewrite()
    }

    /** Newest first. */
    @Synchronized
    fun all(): List<BlockedEvent> = events.asReversed().toList()

    private fun load() {
        val lines = runCatching { if (file.exists()) file.readLines() else emptyList() }.getOrDefault(emptyList())
        events += lines.mapNotNull { line -> runCatching { gson.fromJson(line, EventDto::class.java) }.getOrNull() }
            .mapNotNull { it.toEventOrNull() }
        events.sortBy { it.id }
        nextId = (events.lastOrNull()?.id ?: 0) + 1
        if (events.size > cap) {
            trim()
            rewrite()
        }
    }

    private fun trim() {
        while (events.size > cap) events.removeAt(0)
    }

    private fun rewrite() = write { file.writeText(events.joinToString("") { gson.toJson(it.toDto()) + "\n" }) }

    private fun write(action: () -> Unit) {
        try {
            file.parentFile?.mkdirs()
            action()
        } catch (_: IOException) {
            // The event stays in memory; losing a log line must never change what happens to a call.
        }
    }

    companion object {
        const val DEFAULT_CAP = 500
        const val PRUNE_SLACK = 50
    }
}

private fun EventDto.toEventOrNull(): BlockedEvent? =
    if (id > 0 && action != null) BlockedEvent(id, time, number, name, action, ruleId, summary.orEmpty()) else null

private fun BlockedEvent.toDto() = EventDto(id, timeEpochMillis, number, displayName, action, ruleId, ruleSummary)
