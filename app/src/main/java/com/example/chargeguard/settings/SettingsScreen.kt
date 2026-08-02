package com.example.chargeguard.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chargeguard.R
import com.example.chargeguard.service.ChargeMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repo: SettingsRepository) {
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = ChargeSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            ThresholdSlider(
                labelRes = R.string.settings_upper_threshold_label,
                persistedValue = settings.upperThresholdPercent,
                valueRange = 50f..100f,
                steps = 49,  // (100-50)/1 - 1 = 49 interior steps → 1% per step
                clamp = { it.coerceIn(50, 100) },
                onCommit = { scope.launch { repo.updateUpperThreshold(it) } }
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            ThresholdSlider(
                labelRes = R.string.settings_lower_threshold_label,
                persistedValue = settings.lowerThresholdPercent,
                valueRange = 5f..40f,
                steps = 34,  // (40-5)/1 - 1 = 34 interior steps → 1% per step
                clamp = { it.coerceIn(5, 40) },
                onCommit = { scope.launch { repo.updateLowerThreshold(it) } }
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            SettingsSwitchRow(
                label = stringResource(R.string.settings_sound_label),
                description = stringResource(R.string.settings_sound_desc),
                checked = settings.soundEnabled,
                onCheckedChange = { scope.launch { repo.setSoundEnabled(it) } }
            )

            HorizontalDivider()

            SettingsSwitchRow(
                label = stringResource(R.string.settings_fullscreen_label),
                description = stringResource(R.string.settings_fullscreen_desc),
                checked = settings.fullScreenEscalationEnabled,
                onCheckedChange = { scope.launch { repo.setFullScreenEscalationEnabled(it) } }
            )

            HorizontalDivider()

            // Monitoring toggle: persists the flag AND starts/stops ChargeMonitorService
            SettingsSwitchRow(
                label = stringResource(R.string.settings_monitoring_label),
                description = stringResource(R.string.settings_monitoring_desc),
                checked = settings.monitoringEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        repo.setMonitoringEnabled(enabled)
                        val serviceIntent = Intent(context, ChargeMonitorService::class.java)
                        if (enabled) {
                            ContextCompat.startForegroundService(context, serviceIntent)
                        } else {
                            context.stopService(serviceIntent)
                        }
                    }
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Slider that tracks drag position in local remembered state for responsiveness,
 * then writes the clamped integer value to the repository only on finger-up.
 * [persistedValue] is used to seed the remembered state when the composable
 * first enters composition (e.g. on launch from DataStore).
 */
@Composable
private fun ThresholdSlider(
    labelRes: Int,
    persistedValue: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    clamp: (Int) -> Int,
    onCommit: (Int) -> Unit
) {
    // Seed from persisted value; survives recomposition during drag
    var sliderPosition by remember(persistedValue) { mutableFloatStateOf(persistedValue.toFloat()) }

    Text(
        text = stringResource(labelRes, sliderPosition.roundToInt()),
        style = MaterialTheme.typography.bodyLarge
    )
    Slider(
        value = sliderPosition,
        onValueChange = { sliderPosition = it },
        onValueChangeFinished = { onCommit(clamp(sliderPosition.roundToInt())) },
        valueRange = valueRange,
        steps = steps,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
