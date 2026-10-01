package com.keejii.elbowsup.telecom

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.keejii.elbowsup.core.ContactStatus
import com.keejii.elbowsup.core.resolveRegion
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.isDefaultDialer
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.SimpleContactsHelper
import java.util.Locale

/** Whether [rawNumber] belongs to a contact, using the same lookup Fossify's own screening uses. */
fun Context.contactStatus(rawNumber: String?): ContactStatus {
    if (rawNumber.isNullOrEmpty()) return ContactStatus.NOT_CONTACT
    return try {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        when (SimpleContactsHelper(this).existsSync(rawNumber, privateCursor)) {
            ContactLookupResult.Found -> ContactStatus.CONTACT
            ContactLookupResult.NotFound -> ContactStatus.NOT_CONTACT
            ContactLookupResult.Undetermined -> ContactStatus.UNKNOWN
        }
    } catch (_: Exception) {
        ContactStatus.UNKNOWN
    }
}

/** The region used to read national-format numbers: SIM country, then network, then the phone's locale. */
fun Context.currentRegion(): String? {
    val telephony = getSystemService(TelephonyManager::class.java)
    return resolveRegion(telephony?.simCountryIso, telephony?.networkCountryIso, Locale.getDefault().country)
}

fun Context.dialerRoleHeld(): Boolean = isDefaultDialer()

@RequiresApi(Build.VERSION_CODES.Q)
fun Context.screeningRoleHeld(): Boolean =
    getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

/** When it cannot be checked the number counts as an emergency, so nothing is blocked. */
@RequiresApi(Build.VERSION_CODES.Q)
fun Context.isEmergencyNumberOrUnknown(rawNumber: String?): Boolean = try {
    getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(rawNumber.orEmpty()) ?: true
} catch (_: Exception) {
    true
}
