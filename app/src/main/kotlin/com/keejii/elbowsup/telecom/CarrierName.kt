package com.keejii.elbowsup.telecom

import android.telecom.Call
import android.telecom.TelecomManager
import com.keejii.elbowsup.core.realCallerName
import org.fossify.phone.extensions.isOutgoing

/** The carrier's caller name (CNAM) for an incoming call, or null when there is no real name. */
fun Call.carrierName(): String? = try {
    if (isOutgoing()) {
        null
    } else {
        val callDetails = details
        realCallerName(
            name = callDetails.callerDisplayName,
            presentationAllowed = callDetails.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED,
            number = callDetails.handle?.schemeSpecificPart,
        )
    }
} catch (_: NullPointerException) {
    null
}
