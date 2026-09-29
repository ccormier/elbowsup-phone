package com.keejii.elbowsup

import android.content.Context
import com.keejii.elbowsup.core.BlockerSnapshot
import com.keejii.elbowsup.storage.BlockerConfig
import com.keejii.elbowsup.storage.EventLog
import java.io.File

private const val PREFS_NAME = "elbowsup_blocker"
private const val EVENTS_FILE = "elbowsup/blocked_events.jsonl"

/**
 * The blocker's in-memory state, created on first use by whichever service or screen needs it first.
 * Loading is synchronous and reads only two small local files, so a call that starts a cold process
 * is screened against real rules instead of an empty cache.
 */
class BlockerRuntime private constructor(context: Context) {
    private val config = BlockerConfig(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    val events = EventLog(File(context.filesDir, EVENTS_FILE))

    @Volatile
    private var current: BlockerSnapshot = config.load()

    fun snapshot(): BlockerSnapshot = current

    @Synchronized
    fun update(change: (BlockerSnapshot) -> BlockerSnapshot) {
        val old = current
        val new = change(old)
        config.save(old, new)
        current = new
    }

    fun nextId(): Long = config.nextId()

    companion object {
        @Volatile
        private var instance: BlockerRuntime? = null

        fun get(context: Context): BlockerRuntime = instance ?: synchronized(this) {
            instance ?: BlockerRuntime(context.applicationContext).also { instance = it }
        }
    }
}
