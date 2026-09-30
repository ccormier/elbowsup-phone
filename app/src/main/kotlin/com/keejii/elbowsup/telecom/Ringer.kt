package com.keejii.elbowsup.telecom

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telecom.Call
import android.telecom.InCallService
import com.keejii.elbowsup.core.RingPolicy
import com.keejii.elbowsup.core.RingerMode
import com.keejii.elbowsup.core.ringPolicy

private const val RINGING_META_DATA = "android.telecom.IN_CALL_SERVICE_RINGING"
private const val VIBRATE_WHEN_RINGING = "vibrate_when_ringing"
private const val MAX_RING_MILLIS = 120_000L
private const val VIBRATION_MILLIS = 1000L

/**
 * Rings incoming calls when the manifest says this app owns ringing, and does nothing otherwise so
 * Telecom keeps ringing. Everything here fails toward making noise: an error never leaves a call
 * silent unless the phone's ringer mode or Do Not Disturb asked for that. Main thread only.
 */
object Ringer {
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val watchdog = Runnable { stop() }

    var isRinging = false
        private set

    private var owns: Boolean? = null

    fun ownsRinging(service: InCallService): Boolean = owns ?: readOwnsRinging(service).also { owns = it }

    fun start(service: InCallService, call: Call) {
        if (!ownsRinging(service) || isRinging) return
        isRinging = true
        handler.postDelayed(watchdog, MAX_RING_MILLIS)
        val policy = currentPolicy(service, call)
        val soundFailed = !(policy.sound && playRingtone(service))
        if (policy.vibrate || (policy.sound && soundFailed)) vibrate(service)
    }

    fun stop() {
        if (!isRinging) return
        isRinging = false
        handler.removeCallbacks(watchdog)
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun readOwnsRinging(service: InCallService): Boolean = try {
        val component = ComponentName(service, service.javaClass)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            service.packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(META_DATA))
        } else {
            @Suppress("DEPRECATION")
            service.packageManager.getServiceInfo(component, META_DATA.toInt())
        }
        info.metaData?.getBoolean(RINGING_META_DATA, false) == true
    } catch (_: Exception) {
        false
    }

    private fun currentPolicy(context: Context, call: Call): RingPolicy = try {
        val mode = when (context.getSystemService(AudioManager::class.java)?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else -> RingerMode.NORMAL
        }
        val vibrateWhenRinging = Settings.System.getInt(context.contentResolver, VIBRATE_WHEN_RINGING, 1) != 0
        ringPolicy(mode, dndAllowsCall(context, call), vibrateWhenRinging)
    } catch (_: Exception) {
        RingPolicy(sound = true, vibrate = true)
    }

    /** Below Android 13 a Priority-only filter cannot be checked per caller, so it rings rather than miss a call. */
    private fun dndAllowsCall(context: Context, call: Call): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        return when (manager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_ALARMS,
            -> false
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> {
                val handle = call.details.handle
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && handle != null) {
                    manager.matchesCallFilter(handle)
                } else {
                    true
                }
            }
            else -> true
        }
    }

    private fun playRingtone(context: Context): Boolean = try {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val tone = RingtoneManager.getRingtone(context, uri)
        if (tone == null) {
            false
        } else {
            tone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tone.isLooping = true
            tone.play()
            ringtone = tone
            true
        }
    } catch (_: Exception) {
        false
    }

    private fun vibrate(context: Context) {
        runCatching {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            device?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, VIBRATION_MILLIS, VIBRATION_MILLIS), 0))
            vibrator = device
        }
    }

    private const val META_DATA = PackageManager.GET_META_DATA.toLong()
}
