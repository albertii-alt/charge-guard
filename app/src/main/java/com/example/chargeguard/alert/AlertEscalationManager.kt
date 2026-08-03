package com.example.chargeguard.alert

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.chargeguard.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ChargeGuard"

private const val WAKE_LOCK_TAG = "ChargeGuard::EscalationWakeLock"
private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10-min safety-net timeout

private const val DELAY_T2_MS = 2 * 60 * 1000L      // 2 minutes → ESCALATED_SOUND
private const val DELAY_T3_MS = 3 * 60 * 1000L      // +3 minutes (5 min total) → ESCALATED_FULLSCREEN
private const val DELAY_SNOOZE_MS = 10 * 60 * 1000L // snooze re-escalation window

class AlertEscalationManager(
    private val scope: CoroutineScope,
    private val notificationHelper: NotificationHelper,
    private val settingsRepo: SettingsRepository,
    private val context: Context
) {
    private var state: AlertState = AlertState.IDLE
    private var timerJob: Job? = null

    // Last known values — re-validated inside every timer callback per coding-conventions.md
    private var lastLevel: Int = 0
    private var lastIsCharging: Boolean = false

    private val wakeLock: PowerManager.WakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) } // single acquire/release pair, not a counter

    /**
     * Entry point called by ChargeMonitorService on every battery broadcast.
     * Coding-convention: isCharging is always re-validated before any action.
     */
    fun onBatteryUpdate(level: Int, isCharging: Boolean) {
        lastLevel = level
        lastIsCharging = isCharging

        if (!isCharging) {
            if (state != AlertState.IDLE) {
                transitionTo(AlertState.IDLE)
                cancelTimerAndAlerts() // releases wake lock
            }
            return
        }

        scope.launch {
            val settings = settingsRepo.settings.first()
            if (level >= settings.upperThresholdPercent && state == AlertState.IDLE) {
                transitionTo(AlertState.THRESHOLD_REACHED) // acquires wake lock
                notificationHelper.showThresholdNotification(level)
                scheduleEscalation(DELAY_T2_MS) { onT2Elapsed() }
            }
        }
    }

    /**
     * Called when the user taps "I've unplugged it" in FullScreenAlertActivity.
     * Resets fully to IDLE — user has acknowledged the alert.
     */
    fun onAlertDismissed() {
        Log.d(TAG, "Alert dismissed by user from state=$state")
        transitionTo(AlertState.IDLE)
        cancelTimerAndAlerts() // releases wake lock
    }

    /**
     * Called when the user taps "Snooze 10 min" in FullScreenAlertActivity.
     * Resets to THRESHOLD_REACHED and restarts a 10-min timer before re-escalating.
     * cancelTimerAndAlerts() releases the wake lock before transitionTo re-acquires it,
     * so there is no double-acquire.
     */
    fun onSnoozeRequested() {
        if (!lastIsCharging) return  // unplugged while snooze dialog was open — ignore
        Log.d(TAG, "Snooze requested from state=$state")
        cancelTimerAndAlerts()                             // releases wake lock
        transitionTo(AlertState.THRESHOLD_REACHED)        // re-acquires wake lock
        scheduleEscalation(DELAY_SNOOZE_MS) { onT2Elapsed() }
    }

    // --- Timer callbacks ---

    // suspend funs called directly inside the timer coroutine body — no nested scope.launch,
    // which eliminates the race where a concurrent onBatteryUpdate could cancel timerJob
    // between scheduleEscalation assigning it and the delay actually starting.

    private suspend fun onT2Elapsed() {
        if (!lastIsCharging) return
        val settings = settingsRepo.settings.first()
        if (!lastIsCharging || lastLevel < settings.upperThresholdPercent) return
        transitionTo(AlertState.ESCALATED_SOUND) // wake lock already held; no change
        if (settings.soundEnabled) notificationHelper.showSoundNotification(lastLevel)
        scheduleEscalation(DELAY_T3_MS) { onT5Elapsed() }
    }

    private suspend fun onT5Elapsed() {
        if (!lastIsCharging) return
        val settings = settingsRepo.settings.first()
        if (!lastIsCharging || lastLevel < settings.upperThresholdPercent) return

        if (settings.fullScreenEscalationEnabled) {
            transitionTo(AlertState.ESCALATED_FULLSCREEN) // wake lock already held; no change
            val pi = PendingIntent.getActivity(
                context,
                0,
                Intent(context, FullScreenAlertActivity::class.java).apply {
                    putExtra(EXTRA_BATTERY_LEVEL, lastLevel)
                    putExtra(EXTRA_UPPER_THRESHOLD, settings.upperThresholdPercent)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            notificationHelper.showFullScreenNotification(lastLevel, pi)
        } else {
            // Full-screen escalation disabled — repeat sound alert every 5 min
            if (settings.soundEnabled) notificationHelper.showSoundNotification(lastLevel)
            scheduleEscalation(DELAY_T3_MS) { onT5Elapsed() }
        }
    }

    // --- Helpers ---

    private fun scheduleEscalation(delayMs: Long, block: suspend () -> Unit) {
        timerJob?.cancel()
        timerJob = scope.launch {
            delay(delayMs)
            block()
        }
    }

    /**
     * Cancels the active timer, dismisses notifications, and releases the wake lock.
     * Always called before any transition back to IDLE.
     */
    private fun cancelTimerAndAlerts() {
        timerJob?.cancel()
        timerJob = null
        notificationHelper.cancelAlerts()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock.isHeld) {
            // Belt-and-suspenders: should not happen given the state-machine guards,
            // but log and skip rather than double-acquiring.
            Log.d(TAG, "WakeLock: already held, skipping acquire")
            return
        }
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        Log.d(TAG, "WakeLock: acquired (timeout=${WAKE_LOCK_TIMEOUT_MS / 1000}s)")
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
            Log.d(TAG, "WakeLock: released")
        }
        // If not held, release() would throw — silently skip, nothing to release.
    }

    private fun transitionTo(next: AlertState) {
        Log.d(TAG, "AlertState: $state → $next")
        state = next
        when (next) {
            AlertState.THRESHOLD_REACHED -> acquireWakeLock()
            AlertState.IDLE -> releaseWakeLock() // defensive: cancelTimerAndAlerts() normally handles this
            else -> { /* ESCALATED_SOUND / ESCALATED_FULLSCREEN: wake lock already held */ }
        }
    }
}
