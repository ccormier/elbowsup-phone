package com.keejii.elbowsup.core

private const val MIN_ECHO_SUFFIX_DIGITS = 7

/**
 * The carrier-supplied caller name, or null when there is no real name to show or match.
 *
 * Some carriers echo the number back as the name for unlabeled callers, so a name made only of
 * digits that equals the number (ignoring formatting and a country prefix) is not a real name.
 */
fun realCallerName(name: String?, presentationAllowed: Boolean, number: String?): String? {
    if (!presentationAllowed) return null
    val trimmed = name?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (number != null && trimmed.none { it.isLetter() } && echoesNumber(trimmed, number)) return null
    return trimmed
}

private fun echoesNumber(name: String, number: String): Boolean {
    val nameDigits = name.filter { it.isDigit() }
    val numberDigits = number.filter { it.isDigit() }
    if (nameDigits.isEmpty() || numberDigits.isEmpty()) return false
    if (nameDigits == numberDigits) return true
    val shortest = minOf(nameDigits.length, numberDigits.length)
    return shortest >= MIN_ECHO_SUFFIX_DIGITS &&
        (numberDigits.endsWith(nameDigits) || nameDigits.endsWith(numberDigits))
}
