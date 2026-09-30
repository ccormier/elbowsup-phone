package com.keejii.elbowsup.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.keejii.elbowsup.BlockerRuntime
import com.keejii.elbowsup.core.DialerAction
import com.keejii.elbowsup.core.DialerPlan
import com.keejii.elbowsup.core.canAnswerHangup
import com.keejii.elbowsup.core.planDialer
import com.keejii.elbowsup.storage.BlockedEvent
import org.fossify.phone.extensions.isOutgoing
import java.time.ZonedDateTime

/** The blocker's half of the dialer: decides an incoming call once its caller name is known, and rings it. */
object BlockerCalls {
    /**
     * Returns true when the call was dealt with here and Fossify should not show it. Everything else,
     * including any failure, returns false after making sure the call rings.
     */
    fun onCallAdded(service: InCallService, call: Call): Boolean {
        if (call.state != Call.STATE_RINGING || call.isOutgoing()) return false
        return IncomingCall(service, call).begin()
    }

    fun onSilenceRinger() = Ringer.stop()

    private class IncomingCall(private val service: InCallService, private val call: Call) : Call.Callback() {
        private var blocked = false

        fun begin(): Boolean {
            call.registerCallback(this)
            return evaluate(firstLook = true)
        }

        override fun onStateChanged(call: Call, state: Int) {
            if (state != Call.STATE_RINGING) Ringer.stop()
            if (state == Call.STATE_ACTIVE && blocked) call.disconnect()
            if (state == Call.STATE_DISCONNECTED) call.unregisterCallback(this)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            if (!blocked && call.state == Call.STATE_RINGING) evaluate(firstLook = false)
        }

        private fun evaluate(firstLook: Boolean): Boolean {
            val silentRequested = silentRequested()
            var recorded: Pair<BlockerRuntime, Long>? = null
            return try {
                val plan = plan(silentRequested)
                recorded = record(plan)
                val claimed = act(plan, firstLook)
                if (plan.action == DialerAction.REJECT || plan.action == DialerAction.ANSWER_HANGUP) blocked = true
                claimed
            } catch (_: Throwable) {
                recorded?.let { (runtime, id) -> runCatching { runtime.events.remove(id) } }
                blocked = false
                if (!silentRequested) runCatching { Ringer.start(service, call) }
                false
            }
        }

        private fun plan(silentRequested: Boolean): DialerPlan {
            val runtime = BlockerRuntime.get(service)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ringOnly(silentRequested)
            val rawNumber = call.details.handle?.schemeSpecificPart
            return planDialer(
                snapshot = runtime.snapshot(),
                rawNumber = rawNumber,
                name = call.carrierName(),
                region = service.currentRegion(),
                sdkInt = Build.VERSION.SDK_INT,
                now = ZonedDateTime.now(),
                canAnswerHangup = canAnswerHangup(Build.VERSION.SDK_INT, dialerHeld = true),
                silentRequested = silentRequested,
                isEmergency = { service.isEmergencyNumberOrUnknown(rawNumber) },
                contact = { service.contactStatus(rawNumber) },
            )
        }

        private fun ringOnly(silentRequested: Boolean) =
            DialerPlan(if (silentRequested) DialerAction.NO_RING else DialerAction.RING, null)

        private fun record(plan: DialerPlan): Pair<BlockerRuntime, Long>? {
            val planned = plan.event ?: return null
            val runtime = BlockerRuntime.get(service)
            val event = BlockedEvent(
                id = 0,
                timeEpochMillis = System.currentTimeMillis(),
                number = planned.number,
                displayName = call.carrierName(),
                action = planned.action.name,
                ruleId = planned.ruleId,
                ruleSummary = planned.ruleSummary,
            )
            return runtime to runtime.events.append(event)
        }

        private fun act(plan: DialerPlan, firstLook: Boolean): Boolean = when (plan.action) {
            DialerAction.RING -> {
                Ringer.start(service, call)
                false
            }
            DialerAction.NO_RING -> {
                Ringer.stop()
                false
            }
            DialerAction.REJECT -> {
                Ringer.stop()
                call.reject(false, null)
                firstLook
            }
            DialerAction.ANSWER_HANGUP -> {
                Ringer.stop()
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                firstLook
            }
        }

        private fun silentRequested(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            call.details.extras?.getBoolean(Call.EXTRA_SILENT_RINGING_REQUESTED, false) == true
    }
}
