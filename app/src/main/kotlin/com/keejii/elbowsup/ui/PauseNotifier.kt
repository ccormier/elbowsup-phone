package com.keejii.elbowsup.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import com.keejii.elbowsup.BlockerRuntime
import com.keejii.elbowsup.core.TimedPause
import org.fossify.phone.R
import java.util.Date

private const val CHANNEL_ID = "elbowsup_pause"
private const val NOTIFICATION_ID = 7301
private const val ACTION_RESUME = "com.keejii.elbowsup.RESUME"

/** Shows an ongoing notification with a Resume button while blocking is paused by hand. */
object PauseNotifier {
    fun sync(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val pause = BlockerRuntime.get(context).snapshot().timedPause
        val untilMillis = pause.untilEpochMillis
        val timedActive = untilMillis != null && nowMillis < untilMillis
        if (!pause.untilResume && !pause.untilNextCall && !timedActive) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!canNotify(context)) return
        val channelName = context.getString(R.string.elbowsup_pause_channel)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW),
        )
        val text = if (pause.untilNextCall) {
            context.getString(R.string.elbowsup_pause_notification_next_call)
        } else if (pause.untilResume || untilMillis == null) {
            context.getString(R.string.elbowsup_pause_notification_until_resume)
        } else {
            val time = DateFormat.getTimeFormat(context).format(Date(untilMillis))
            context.getString(R.string.elbowsup_pause_notification_until, time)
        }
        val resume = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, PauseActionReceiver::class.java).setAction(ACTION_RESUME),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, BlockerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle(context.getString(R.string.elbowsup_pause_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.elbowsup_resume), resume).build())
        if (!pause.untilResume && !pause.untilNextCall && untilMillis != null) {
            builder.setTimeoutAfter(untilMillis - nowMillis)
        }
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        return granted == PackageManager.PERMISSION_GRANTED
    }
}

/** The Resume button on the pause notification. */
class PauseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BlockerRuntime.get(context).update { it.copy(timedPause = TimedPause.NONE) }
        PauseNotifier.sync(context)
    }
}
