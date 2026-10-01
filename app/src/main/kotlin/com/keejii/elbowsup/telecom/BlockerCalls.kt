package com.keejii.elbowsup.telecom

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.annotation.RequiresApi
import com.keejii.elbowsup.BlockerRuntime
import com.keejii.elbowsup.core.DialerAction
import com.keejii.elbowsup.core.DialerPlan
import com.keejii.elbowsup.core.RecordStep
import com.keejii.elbowsup.core.RecordedBlock
import com.keejii.elbowsup.core.canAnswerHangup
import com.keejii.elbowsup.core.PlannedEvent
import com.keejii.elbowsup.core.planDialer
import com.keejii.elbowsup.core.recordStep
import com.keejii.elbowsup.storage.BlockedEvent
import org.fossify.phone.extensions.isOutgoing
import java.time.ZonedDateTime

private const val SILENCE_STEP_MILLIS = 300L
/** Eleven tries 300 ms apart cover the first 3 seconds, well past the moment Telecom starts ringing. */
private const val SILENCE_STEPS = 11

/**
 * The blocker's half of the dialer: decides an incoming call once its caller name is known. Telecom
 * does the ringing and holds it about a second for the dialer to get ready, so a reject that comes
 * straight away is never heard. A silence has to be repeated because Telecom ignores one that arrives
 * before it has started ringing. Any failure here leaves the call ringing.
 */
object BlockerCalls {
    /** Returns true when the call was dealt with here and Fossify should not show it. */
    fun onCallAdded(service: InCallService, call: Call): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        if (call.state != Call.STATE_RINGING || call.isOutgoing()) return false
        return IncomingCall(service, call).begin()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private class IncomingCall(private val service: InCallService, private val call: Call) : Call.Callback() {
        private var blocked = false

        // One instance per call, and a call only rings once, so this is never reset: onStateChanged clears
        // the pending silences when the call stops ringing, and nothing starts them again afterwards.
        private var silencing = false
        private var recorded: RecordedBlock? = null
        private val handler = Handler(Looper.getMainLooper())

        fun begin(): Boolean {
            call.registerCallback(this)
            return evaluate(firstLook = true)
        }

        override fun onStateChanged(call: Call, state: Int) {
            if (state != Call.STATE_RINGING) handler.removeCallbacksAndMessages(null)
            if (state == Call.STATE_ACTIVE && blocked) call.disconnect()
            if (state == Call.STATE_DISCONNECTED) call.unregisterCallback(this)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            if (!blocked && call.state == Call.STATE_RINGING) evaluate(firstLook = false)
        }

        /**
         * Catches everything, errors included: this runs inside the phone app's own call service, where a
         * crash would take down the call screen, and failing open here only means the call rings.
         */
        private fun evaluate(firstLook: Boolean): Boolean {
            val silentRequested = silentRequested()
            var written: Long? = null
            return try {
                val plan = plan(silentRequested)
                val planned = plan.event
                val step = recordStep(recorded, planned)
                if (step is RecordStep.Write && planned != null) written = append(planned)
                val claimed = act(plan, silentRequested, firstLook)
                if (step is RecordStep.Write && planned != null && written != null) {
                    step.replacing?.let { runCatching { BlockerRuntime.get(service).events.remove(it) } }
                    recorded = RecordedBlock(written, planned.action, planned.ruleId)
                }
                if (plan.action == DialerAction.REJECT || plan.action == DialerAction.ANSWER_HANGUP) blocked = true
                claimed
            } catch (_: Throwable) {
                written?.let { id -> runCatching { BlockerRuntime.get(service).events.remove(id) } }
                blocked = false
                false
            }
        }

        private fun plan(silentRequested: Boolean): DialerPlan {
            val runtime = BlockerRuntime.get(service)
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
                screeningHeld = service.screeningRoleHeld(),
                isEmergency = { service.isEmergencyNumberOrUnknown(rawNumber) },
                contact = { service.contactStatus(rawNumber) },
            )
        }

        private fun append(planned: PlannedEvent): Long {
            val event = BlockedEvent(
                id = 0,
                timeEpochMillis = System.currentTimeMillis(),
                number = planned.number,
                displayName = call.carrierName(),
                action = planned.action.name,
                ruleId = planned.ruleId,
                ruleSummary = planned.ruleSummary,
            )
            return BlockerRuntime.get(service).events.append(event)
        }

        private fun act(plan: DialerPlan, silentRequested: Boolean, firstLook: Boolean): Boolean = when (plan.action) {
            DialerAction.RING -> false
            DialerAction.NO_RING -> {
                if (!silentRequested) silenceRepeatedly()
                false
            }
            DialerAction.REJECT -> {
                call.reject(false, null)
                firstLook
            }
            DialerAction.ANSWER_HANGUP -> {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                firstLook
            }
        }

        private fun silenceRepeatedly() {
            if (silencing) return
            silencing = true
            repeat(SILENCE_STEPS) { step ->
                handler.postDelayed({ silenceOnce() }, step * SILENCE_STEP_MILLIS)
            }
        }

        // Telecom lets the default dialer silence the ringer without MODIFY_PHONE_STATE.
        @SuppressLint("MissingPermission")
        private fun silenceOnce() {
            if (call.state != Call.STATE_RINGING) return
            runCatching { service.getSystemService(TelecomManager::class.java)?.silenceRinger() }
        }

        private fun silentRequested(): Boolean =
            call.details.extras?.getBoolean(Call.EXTRA_SILENT_RINGING_REQUESTED, false) == true
    }
}
