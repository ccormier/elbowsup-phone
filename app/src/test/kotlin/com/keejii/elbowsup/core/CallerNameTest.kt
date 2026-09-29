package com.keejii.elbowsup.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallerNameTest {
    private val number = "+12262201234"

    @Test
    fun aCarrierLabelIsARealName() {
        assertEquals("Likely Spam", realCallerName("Likely Spam", true, number))
    }

    @Test
    fun blankOrMissingIsNotAName() {
        assertNull(realCallerName(null, true, number))
        assertNull(realCallerName("", true, number))
        assertNull(realCallerName("   ", true, number))
    }

    @Test
    fun theNumberEchoedBackIsNotAName() {
        assertNull(realCallerName("2262201234", true, number))
        assertNull(realCallerName("+12262201234", true, number))
        assertNull(realCallerName("(226) 220-1234", true, number))
        assertNull(realCallerName("1-226-220-1234", true, "2262201234"))
    }

    @Test
    fun aRestrictedPresentationHidesTheName() {
        assertNull(realCallerName("Likely Spam", false, number))
    }

    @Test
    fun aNameWithLettersIsKeptEvenIfItHasDigits() {
        assertEquals("Pizza 226-220-1234", realCallerName("Pizza 226-220-1234", true, number))
    }

    @Test
    fun aDifferentNumberAsNameIsKept() {
        assertEquals("8005551212", realCallerName("8005551212", true, number))
    }

    @Test
    fun aNoNumberCallKeepsALabel() {
        assertEquals("Private", realCallerName("Private", true, null))
        assertNull(realCallerName("", true, null))
    }

    @Test
    fun theNameIsTrimmed() {
        assertEquals("Likely Spam", realCallerName("  Likely Spam ", true, number))
    }
}
