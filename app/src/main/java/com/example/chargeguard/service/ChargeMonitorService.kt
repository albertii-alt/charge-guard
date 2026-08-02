package com.example.chargeguard.service

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import com.example.chargeguard.alert.AlertEscalationManager
import com.example.chargeguard.alert.NOTIF_ID_FOREGROUND
import com.example.chargeguard.alert.NotificationHelper
import com.example.chargeguard.receiver.BatteryStateReceiver
import com.example.chargeguard.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ChargeGuard"

class ChargeMonitorService : Service() {

    companion object {
        const val ACTION_ALERT_DISMISSED = "com.example.chargeguard.ACTION_ALERT_DISMISSED"
        const val ACTION_ALERT_SNOOZE = "com.example.chargeguard.ACTION_ALERT_SNOOZE"
    }

    private lateinit var receiver: BatteryStateReceiver
    private lateinit var alertManager: AlertEscalationManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var settingsRepo: SettingsRepository

    // Scope tied to the service lifetime; cancelled in onDestroy
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ChargeMonitorService onCreate")

        settingsRepo = SettingsRepository(applicationContext)
        notificationHelper = NotificationHelper(applicationContext)
        alertManager = AlertEscalationManager(serviceScope, notificationHelper, settingsRepo, applicationContext)

        receiver = BatteryStateReceiver { level, isCharging ->
            Log.d(TAG, "Battery update received: level=$level isCharging=$isCharging")
            alertManager.onBatteryUpdate(level, isCharging)

            // Keep the foreground notification text current
            serviceScope.launch {
                val threshold = settingsRepo.settings.first().upperThresholdPercent
                val notification = notificationHelper.buildStatusNotification(level, threshold)
                startForeground(NOTIF_ID_FOREGROUND, notification)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(receiver, filter)
        Log.d(TAG, "BatteryStateReceiver registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ALERT_DISMISSED -> {
                Log.d(TAG, "onStartCommand: alert dismissed by user")
                alertManager.onAlertDismissed()
                return START_STICKY
            }
            ACTION_ALERT_SNOOZE -> {
                Log.d(TAG, "onStartCommand: snooze requested by user")
                alertManager.onSnoozeRequested()
                return START_STICKY
            }
        }

        Log.d(TAG, "ChargeMonitorService onStartCommand")

        // Post an initial foreground notification immediately; update text once settings load
        val placeholder = notificationHelper.buildStatusNotification(0, 80)
        startForeground(NOTIF_ID_FOREGROUND, placeholder)

        serviceScope.launch {
            val settings = settingsRepo.settings.first()
            val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = sticky?.let {
                val raw = it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (scale > 0) raw * 100 / scale else 0
            } ?: 0
            val notification = notificationHelper.buildStatusNotification(level, settings.upperThresholdPercent)
            startForeground(NOTIF_ID_FOREGROUND, notification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        serviceScope.cancel()
        Log.d(TAG, "ChargeMonitorService destroyed, receiver unregistered")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
