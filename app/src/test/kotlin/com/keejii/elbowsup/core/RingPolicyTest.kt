package com.keejii.elbowsup.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RingPolicyTest {
    private fun policy(
        mode: RingerMode,
        dndAllowsCall: Boolean = true,
        vibrateWhenRinging: Boolean = false,
    ) = ringPolicy(mode, dndAllowsCall, vibrateWhenRinging)

    @Test
    fun normalModeRingsAndVibratesOnlyWhenVibrateWhenRingingIsOn() {
        assertEquals(RingPolicy(sound = true, vibrate = false), policy(RingerMode.NORMAL))
        assertEquals(RingPolicy(sound = true, vibrate = true), policy(RingerMode.NORMAL, vibrateWhenRinging = true))
    }

    @Test
    fun vibrateModeVibratesWithoutSound() {
        assertEquals(RingPolicy(sound = false, vibrate = true), policy(RingerMode.VIBRATE))
    }

    @Test
    fun silentModeIsSilent() {
        assertEquals(RingPolicy(sound = false, vibrate = false), policy(RingerMode.SILENT, vibrateWhenRinging = true))
    }

    @Test
    fun doNotDisturbThatBlocksTheCallIsSilentInEveryMode() {
        val silent = RingPolicy(sound = false, vibrate = false)
        RingerMode.values().forEach {
            assertEquals(silent, policy(it, dndAllowsCall = false, vibrateWhenRinging = true))
        }
    }
}
