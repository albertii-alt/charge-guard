package com.example.chargeguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log

private const val TAG = "ChargeGuard"

/**
 * Handles battery level and charging state changes.
 * Must be registered dynamically — ACTION_BATTERY_CHANGED cannot be
 * declared statically in the manifest on API 26+.
 *
 * @param onUpdate called with (level: Int, isCharging: Boolean) on every relevant broadcast.
 */
class BatteryStateReceiver(
    private val onUpdate: (level: Int, isCharging: Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = extractLevel(intent)
                val isCharging = extractIsCharging(intent)
                Log.d(TAG, "ACTION_BATTERY_CHANGED: level=$level isCharging=$isCharging")
                onUpdate(level, isCharging)
            }
            Intent.ACTION_POWER_CONNECTED -> {
                // Re-read level from the sticky ACTION_BATTERY_CHANGED broadcast
                val sticky = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = sticky?.let { extractLevel(it) } ?: 0
                Log.d(TAG, "ACTION_POWER_CONNECTED: level=$level")
                onUpdate(level, true)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                val sticky = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = sticky?.let { extractLevel(it) } ?: 0
                Log.d(TAG, "ACTION_POWER_DISCONNECTED: level=$level")
                onUpdate(level, false)
            }
        }
    }

    private fun extractLevel(intent: Intent): Int {
        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (raw < 0 || scale <= 0) 0 else (raw * 100 / scale)
    }

    private fun extractIsCharging(intent: Intent): Boolean {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }
}
