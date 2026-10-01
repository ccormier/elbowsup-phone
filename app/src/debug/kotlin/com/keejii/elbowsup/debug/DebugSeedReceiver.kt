package com.keejii.elbowsup.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.keejii.elbowsup.BlockerRuntime
import com.keejii.elbowsup.core.BlockAction
import com.keejii.elbowsup.core.MatcherType
import com.keejii.elbowsup.core.Rule
import com.keejii.elbowsup.core.RuleKind
import com.keejii.elbowsup.core.TimedPause

private const val TAG = "ElbowsUpDebug"

/**
 * Sets up blocker state from adb, until there is a screen for it:
 *
 *   adb shell am broadcast -n <pkg>/com.keejii.elbowsup.debug.DebugSeedReceiver --es op clear
 *   ... --es op add --es matcher PREFIX --es pattern +1226 --es action SILENCE [--es kind ALLOW]
 *   ... --es op setup        (marks setup complete)
 *   ... --es op pause --es mode resume|nextcall|none
 *   ... --es op dump         (logs the state and recent blocked events under tag ElbowsUpDebug)
 */
class DebugSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val runtime = BlockerRuntime.get(context)
        when (val op = intent.getStringExtra("op")) {
            "clear" -> runtime.update {
                it.copy(rules = emptyList(), schedules = emptyList(), timedPause = TimedPause.NONE)
            }
            "setup" -> runtime.update { it.copy(setupComplete = true) }
            "pause" -> {
                val pause = when (intent.getStringExtra("mode")) {
                    "resume" -> TimedPause.UNTIL_RESUME
                    "nextcall" -> TimedPause.NEXT_CALL
                    else -> TimedPause.NONE
                }
                runtime.update { it.copy(timedPause = pause) }
            }
            "add" -> {
                val rule = Rule(
                    id = runtime.nextId(),
                    enabled = true,
                    kind = RuleKind.valueOf(intent.getStringExtra("kind") ?: RuleKind.BLOCK.name),
                    matcher = MatcherType.valueOf(intent.getStringExtra("matcher") ?: MatcherType.PREFIX.name),
                    pattern = intent.getStringExtra("pattern"),
                    window = null,
                    action = intent.getStringExtra("action")?.let { BlockAction.valueOf(it) },
                )
                runtime.update { it.copy(rules = it.rules + rule) }
            }
            "dump" -> Unit
            else -> Log.w(TAG, "unknown op '$op'")
        }
        val snapshot = runtime.snapshot()
        Log.i(TAG, "op=${intent.getStringExtra("op")} setup=${snapshot.setupComplete} pause=${snapshot.timedPause}")
        snapshot.rules.forEach { Log.i(TAG, "rule $it") }
        runtime.events.all().take(10).forEach { Log.i(TAG, "event $it") }
    }
}
