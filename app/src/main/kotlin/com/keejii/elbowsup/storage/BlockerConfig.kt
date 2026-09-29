package com.keejii.elbowsup.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import com.keejii.elbowsup.core.BlockerSnapshot
import com.keejii.elbowsup.core.PauseSchedule
import com.keejii.elbowsup.core.Rule
import com.keejii.elbowsup.core.TimedPause

private const val KEY_RULES = "rules"
private const val KEY_SCHEDULES = "schedules"
private const val KEY_TIMED_UNTIL = "timed_until"
private const val KEY_UNTIL_RESUME = "until_resume"
private const val KEY_SETUP_COMPLETE = "setup_complete"
private const val KEY_LAST_ID = "last_id"

/** The blocker's own preferences file, kept apart from Fossify's [org.fossify.phone.helpers.Config]. */
class BlockerConfig(private val prefs: SharedPreferences) {
    fun load(): BlockerSnapshot = BlockerSnapshot(
        rules = BlockerJson.rulesFromJson(prefs.getString(KEY_RULES, null)),
        schedules = BlockerJson.schedulesFromJson(prefs.getString(KEY_SCHEDULES, null)),
        timedPause = TimedPause(
            untilEpochMillis = if (prefs.contains(KEY_TIMED_UNTIL)) prefs.getLong(KEY_TIMED_UNTIL, 0) else null,
            untilResume = prefs.getBoolean(KEY_UNTIL_RESUME, false),
        ),
        setupComplete = prefs.getBoolean(KEY_SETUP_COMPLETE, false),
    )

    /** Writes only what differs between [old] and [new]. */
    fun save(old: BlockerSnapshot, new: BlockerSnapshot) {
        prefs.edit(commit = true) {
            if (new.rules != old.rules) putString(KEY_RULES, BlockerJson.rulesToJson(new.rules))
            if (new.schedules != old.schedules) putString(KEY_SCHEDULES, BlockerJson.schedulesToJson(new.schedules))
            if (new.timedPause != old.timedPause) {
                val until = new.timedPause.untilEpochMillis
                if (until == null) remove(KEY_TIMED_UNTIL) else putLong(KEY_TIMED_UNTIL, until)
                putBoolean(KEY_UNTIL_RESUME, new.timedPause.untilResume)
            }
            if (new.setupComplete != old.setupComplete) putBoolean(KEY_SETUP_COMPLETE, new.setupComplete)
        }
    }

    /** A new id for a rule or schedule; ids are never reused. */
    @Synchronized
    fun nextId(): Long {
        val id = prefs.getLong(KEY_LAST_ID, 0) + 1
        prefs.edit(commit = true) { putLong(KEY_LAST_ID, id) }
        return id
    }
}
