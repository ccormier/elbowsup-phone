package com.keejii.elbowsup.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse
import androidx.annotation.RequiresApi
import com.keejii.elbowsup.BlockerRuntime
import com.keejii.elbowsup.core.ScreeningFlags
import com.keejii.elbowsup.core.canAnswerHangup
import com.keejii.elbowsup.core.planScreening
import com.keejii.elbowsup.storage.BlockedEvent
import java.time.ZonedDateTime

/** The blocker's half of call screening. Fossify's own screening runs whenever this has no verdict. */
object BlockerScreening {
    /**
     * Returns true when the call was answered here. Any failure, errors included, returns false so that
     * Fossify's screening runs and the call is never blocked by accident; this runs in the phone app's own
     * process, where letting an error escape would crash the dialer. A blocked event that was written
     * before a failed response is taken back.
     */
    fun screen(context: CallScreeningService, details: Call.Details): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        var runtime: BlockerRuntime? = null
        var pendingEventId: Long? = null
        return try {
            val loaded = BlockerRuntime.get(context)
            runtime = loaded
            val rawNumber = details.handle?.schemeSpecificPart
            val now = ZonedDateTime.now()
            val plan = planScreening(
                snapshot = loaded.snapshot(),
                rawNumber = rawNumber,
                region = context.currentRegion(),
                sdkInt = Build.VERSION.SDK_INT,
                now = now,
                canAnswerHangup = canAnswerHangup(Build.VERSION.SDK_INT, context.dialerRoleHeld()),
                screeningHeld = context.screeningRoleHeld(),
                isEmergency = { context.isEmergencyNumberOrUnknown(rawNumber) },
                contact = { context.contactStatus(rawNumber) },
            )
            val flags = plan.flags ?: return false
            plan.event?.let {
                val event = BlockedEvent(
                    id = 0,
                    timeEpochMillis = now.toInstant().toEpochMilli(),
                    number = it.number,
                    displayName = null,
                    action = it.action.name,
                    ruleId = it.ruleId,
                    ruleSummary = it.ruleSummary,
                )
                pendingEventId = loaded.events.append(event)
            }
            context.respondToCall(details, flags.toCallResponse())
            true
        } catch (_: Throwable) {
            pendingEventId?.let { id -> runCatching { runtime?.events?.remove(id) } }
            false
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
fun ScreeningFlags.toCallResponse(): CallResponse = CallResponse.Builder()
    .setDisallowCall(disallow)
    .setRejectCall(reject)
    .setSilenceCall(silence)
    .setSkipCallLog(skipCallLog)
    .setSkipNotification(skipNotification)
    .build()
