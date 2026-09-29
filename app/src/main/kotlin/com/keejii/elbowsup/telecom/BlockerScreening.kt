package com.keejii.elbowsup.telecom

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService.CallResponse
import android.telephony.TelephonyManager
import com.keejii.elbowsup.BlockerRuntime
import com.keejii.elbowsup.core.ScreeningFlags
import com.keejii.elbowsup.core.planScreening
import com.keejii.elbowsup.storage.BlockedEvent
import java.time.ZonedDateTime

/** The blocker's half of call screening. Fossify's own screening runs whenever this has no verdict. */
object BlockerScreening {
    /**
     * Returns true when the call was answered here. Any failure returns false so that Fossify's
     * screening runs and the call is never blocked by accident; a blocked event that was written
     * before a failed response is taken back.
     */
    fun screen(context: Context, details: Call.Details, respond: (CallResponse) -> Unit): Boolean {
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
                canAnswerHangup = false, // the dialer half arrives in Phase 5
                isEmergency = { isEmergencyNumber(context, rawNumber) },
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
            respond(flags.toCallResponse())
            true
        } catch (_: Throwable) {
            pendingEventId?.let { id -> runCatching { runtime?.events?.remove(id) } }
            false
        }
    }

    /** When it cannot be checked the number counts as an emergency, so nothing is blocked. */
    private fun isEmergencyNumber(context: Context, rawNumber: String?): Boolean = try {
        context.getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(rawNumber.orEmpty()) ?: true
    } catch (_: Exception) {
        true
    }
}

fun ScreeningFlags.toCallResponse(): CallResponse = CallResponse.Builder()
    .setDisallowCall(disallow)
    .setRejectCall(reject)
    .setSilenceCall(silence)
    .setSkipCallLog(skipCallLog)
    .setSkipNotification(skipNotification)
    .build()
