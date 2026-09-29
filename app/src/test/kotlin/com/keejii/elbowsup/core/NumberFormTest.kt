package com.keejii.elbowsup.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberFormTest {
    @Test
    fun aNationalNumberEqualsItsInternationalForm() {
        assertEquals("+14155551234", normalizeNumber("(415) 555-1234", "US"))
        assertEquals("+14155551234", normalizeNumber("+1 415-555-1234", "US"))
        assertEquals("+14155551234", normalizeNumber("tel:4155551234".removePrefix("tel:"), "US"))
    }

    @Test
    fun theRegionOnlyMattersForNationalNumbers() {
        assertEquals("+442079460958", normalizeNumber("+442079460958", "US"))
        assertEquals("+442079460958", normalizeNumber("020 7946 0958", "GB"))
    }

    @Test
    fun anUnparsableNumberKeepsItsDigits() {
        assertEquals("12345", normalizeNumber("12345", null))
        assertEquals("+12345", normalizeNumber("+1 2345", null))
    }

    @Test
    fun noDigitsMeansNoNumber() {
        assertNull(normalizeNumber(null, "US"))
        assertNull(normalizeNumber("", "US"))
        assertNull(normalizeNumber("Private", "US"))
    }
}
