package com.example.chargeguard.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.chargeguard.R

const val CHANNEL_CHARGE_STATUS = "charge_status"  // low-priority, ongoing foreground notification
const val CHANNEL_CHARGE_ALERTS = "charge_alerts"  // high-priority, escalating alert notifications

const val NOTIF_ID_FOREGROUND = 1
const val NOTIF_ID_ALERT = 2

class NotificationHelper(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHARGE_STATUS,
                context.getString(R.string.notif_channel_status_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.notif_channel_status_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHARGE_ALERTS,
                context.getString(R.string.notif_channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_alerts_desc)
                enableVibration(true)
            }
        )
    }

    /** Persistent foreground-service notification showing live battery level and target. */
    fun buildStatusNotification(level: Int, upperThreshold: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_CHARGE_STATUS)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(context.getString(R.string.notif_status_title))
            .setContentText(context.getString(R.string.notif_status_text, level, upperThreshold))
            .setOngoing(true)
            .setSilent(true)
            .build()

    /** T+0: normal-priority alert — "Battery at X% — consider unplugging." */
    fun showThresholdNotification(level: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_CHARGE_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notif_alert_title))
            .setContentText(context.getString(R.string.notif_alert_text, level))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .setAutoCancel(false)
            .build()
        manager.notify(NOTIF_ID_ALERT, notification)
    }

    /** T+2min: high-priority alert with sound (caller checks soundEnabled before calling). */
    fun showSoundNotification(level: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_CHARGE_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notif_sound_title))
            .setContentText(context.getString(R.string.notif_sound_text, level))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .build()
        manager.notify(NOTIF_ID_ALERT, notification)
    }

    /**
     * T+5min: full-screen intent notification.
     * [fullScreenIntent] is built by the caller (AlertEscalationManager) once
     * FullScreenAlertActivity exists; passing null falls back to a heads-up notification.
     */
    fun showFullScreenNotification(level: Int, fullScreenIntent: android.app.PendingIntent?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_CHARGE_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notif_fullscreen_title))
            .setContentText(context.getString(R.string.notif_fullscreen_text, level))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
        fullScreenIntent?.let { builder.setFullScreenIntent(it, true) }
        manager.notify(NOTIF_ID_ALERT, builder.build())
    }

    /** Cancels all active alert notifications (called on POWER_DISCONNECTED or snooze-reset). */
    fun cancelAlerts() {
        manager.cancel(NOTIF_ID_ALERT)
    }
}
