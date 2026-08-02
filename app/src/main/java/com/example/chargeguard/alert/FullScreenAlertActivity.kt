package com.example.chargeguard.alert

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.chargeguard.R
import com.example.chargeguard.service.ChargeMonitorService
import com.example.chargeguard.ui.theme.ChargeGuardTheme

private const val TAG = "ChargeGuard"

const val EXTRA_BATTERY_LEVEL = "extra_battery_level"
const val EXTRA_UPPER_THRESHOLD = "extra_upper_threshold"

class FullScreenAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "FullScreenAlertActivity onCreate")

        // Show over lock screen and turn the screen on
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguard = getSystemService(KeyguardManager::class.java)
        keyguard.requestDismissKeyguard(this, null)

        val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, 0)
        val threshold = intent.getIntExtra(EXTRA_UPPER_THRESHOLD, 80)

        setContent {
            ChargeGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlertContent(
                        level = level,
                        threshold = threshold,
                        onUnplugged = {
                            Log.d(TAG, "FullScreenAlertActivity: user confirmed unplug")
                            sendServiceAction(ChargeMonitorService.ACTION_ALERT_DISMISSED)
                            finish()
                        },
                        onSnooze = {
                            Log.d(TAG, "FullScreenAlertActivity: snooze requested")
                            sendServiceAction(ChargeMonitorService.ACTION_ALERT_SNOOZE)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, ChargeMonitorService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }
}

@Composable
private fun AlertContent(
    level: Int,
    threshold: Int,
    onUnplugged: () -> Unit,
    onSnooze: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.alert_battery_label),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "$level%",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.alert_target_label, threshold),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onUnplugged,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.alert_btn_unplugged))
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onSnooze,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.alert_btn_snooze))
        }
    }
}
