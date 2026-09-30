package com.keejii.elbowsup.ui

import android.content.Context
import android.provider.CallLog.Calls
import android.widget.TextView
import com.keejii.elbowsup.BlockerRuntime
import com.keejii.elbowsup.core.normalizeNumber
import com.keejii.elbowsup.storage.matchBlockedEvent
import com.keejii.elbowsup.telecom.currentRegion
import org.fossify.phone.R
import org.fossify.phone.models.RecentCall

/** Marks a Recents row as blocked, from this app's event log or from the platform's own blocked type. */
object BlockedRecents {
    /**
     * Appends "Blocked" to [view], and the rule summary too when [detailed]. Recents must keep working
     * whatever happens here, so any failure leaves the row as it was.
     */
    fun annotate(context: Context, view: TextView, call: RecentCall, detailed: Boolean) {
        val callType = call.type
        if (callType == Calls.OUTGOING_TYPE) return
        runCatching {
            val number = normalizeNumber(call.phoneNumber, context.currentRegion())
            val event = matchBlockedEvent(BlockerRuntime.get(context).events.all(), number, call.startTS)
            val label = when {
                event != null && detailed && event.ruleSummary.isNotBlank() ->
                    context.getString(R.string.elbowsup_recents_blocked_detail, event.ruleSummary)
                event != null || callType == Calls.BLOCKED_TYPE -> context.getString(R.string.elbowsup_recents_blocked)
                else -> return
            }
            view.text = context.getString(R.string.elbowsup_recents_suffix, view.text, label)
        }
    }
}
