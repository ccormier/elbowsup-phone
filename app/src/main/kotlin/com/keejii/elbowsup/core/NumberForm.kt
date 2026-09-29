package com.keejii.elbowsup.core

import com.google.i18n.phonenumbers.PhoneNumberUtil

private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

/**
 * The comparable form of a number: E.164 when it parses as a valid number, otherwise its digits
 * (keeping a leading plus). Null when there are no digits. Rules and the call use this same form.
 */
fun normalizeNumber(raw: String?, region: String?): String? {
    if (raw.isNullOrBlank()) return null
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) return null
    val parsed = runCatching { phoneUtil.parse(raw, region) }.getOrNull()
    if (parsed != null && phoneUtil.isValidNumber(parsed)) {
        return phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
    }
    return if (raw.trim().startsWith("+")) "+$digits" else digits
}
