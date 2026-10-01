package com.keejii.elbowsup

import android.content.Context
import com.keejii.elbowsup.core.BlockerSnapshot
import com.keejii.elbowsup.storage.BlockerConfig
import com.keejii.elbowsup.storage.EventLog
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

private const val PREFS_NAME = "elbowsup_blocker"
private const val EVENTS_FILE = "elbowsup/blocked_events.jsonl"

/**
 * The blocker's in-memory state, created on first use by whichever service or screen needs it first.
 * Loading is synchronous and reads only two small local files, so a call that starts a cold process
 * is screened against real rules instead of an empty cache.
 */
class BlockerRuntime private constructor(context: Context) {
    private val config = BlockerConfig(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    // Kept out of Android's cloud backup: it lists who called, and it is not worth restoring on a new phone.
    val events = EventLog(File(context.noBackupFilesDir, EVENTS_FILE))

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile
    private var current: BlockerSnapshot = config.loadWithDefaults()

    fun snapshot(): BlockerSnapshot = current

    /** Applies [change], saves it, and tells listeners if anything actually changed. */
    fun update(change: (BlockerSnapshot) -> BlockerSnapshot) {
        val changed = synchronized(this) {
            val old = current
            val new = change(old)
            if (new != old) {
                config.save(old, new)
                current = new
            }
            new != old
        }
        if (changed) listeners.forEach { it() }
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
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
