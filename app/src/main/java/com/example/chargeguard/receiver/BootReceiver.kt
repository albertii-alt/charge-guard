package com.example.chargeguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.chargeguard.service.ChargeMonitorService
import com.example.chargeguard.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ChargeGuard"

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "BootReceiver: BOOT_COMPLETED received")

        // goAsync() tells the system this receiver is still working after onReceive returns,
        // preventing premature process recycling while we await the DataStore read.
        // GlobalScope is intentional here — BroadcastReceivers have no lifecycle to bind a
        // scope to, and pendingResult.finish() provides the explicit completion signal.
        val pendingResult = goAsync()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val settings = SettingsRepository(context).settings.first()
                if (settings.monitoringEnabled) {
                    Log.d(TAG, "BootReceiver: monitoringEnabled=true — starting ChargeMonitorService")
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, ChargeMonitorService::class.java)
                    )
                } else {
                    Log.d(TAG, "BootReceiver: monitoringEnabled=false — doing nothing")
                }
            } finally {
                // Must always be called, even if an exception occurs, or the system
                // will hold a wakelock indefinitely waiting for this receiver to finish.
                pendingResult.finish()
            }
        }
    }
}
