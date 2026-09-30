package com.keejii.elbowsup.core

enum class RingerMode { NORMAL, VIBRATE, SILENT }

data class RingPolicy(val sound: Boolean, val vibrate: Boolean)

/**
 * What an incoming call may do given the phone's ringer mode and Do Not Disturb. [dndAllowsCall]
 * is true when Do Not Disturb is off or lets this caller through.
 */
fun ringPolicy(mode: RingerMode, dndAllowsCall: Boolean, vibrateWhenRinging: Boolean): RingPolicy = when {
    !dndAllowsCall -> RingPolicy(sound = false, vibrate = false)
    mode == RingerMode.NORMAL -> RingPolicy(sound = true, vibrate = vibrateWhenRinging)
    mode == RingerMode.VIBRATE -> RingPolicy(sound = false, vibrate = true)
    else -> RingPolicy(sound = false, vibrate = false)
}
