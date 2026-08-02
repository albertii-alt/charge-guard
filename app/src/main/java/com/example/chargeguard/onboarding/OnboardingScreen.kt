package com.example.chargeguard.onboarding

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.chargeguard.R
import com.example.chargeguard.settings.SettingsRepository
import kotlinx.coroutines.launch

private const val TAG = "ChargeGuard"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    repo: SettingsRepository,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- Step 1: Notification permission (API 33+ only) ---
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true // below API 33 notifications are granted implicitly
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
        Log.d(TAG, "POST_NOTIFICATIONS granted=$granted")
    }

    // --- Step 2: Battery optimisation exemption ---
    val powerManager = context.getSystemService(PowerManager::class.java)
    var batteryExempt by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.onboarding_title)) }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // Step 1 — Notification permission (API 33+ only)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OnboardingStepCard(
                    title = stringResource(R.string.onboarding_notif_title),
                    description = stringResource(R.string.onboarding_notif_desc),
                    isDone = notifGranted,
                    actionLabel = stringResource(R.string.onboarding_notif_btn),
                    onAction = {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            // Step 2 — Battery optimisation exemption
            OnboardingStepCard(
                title = stringResource(R.string.onboarding_battery_opt_title),
                description = stringResource(R.string.onboarding_battery_opt_desc),
                isDone = batteryExempt,
                actionLabel = stringResource(R.string.onboarding_btn_open),
                onAction = {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                    // Re-check on next recomposition when user returns
                    batteryExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                }
            )
            Spacer(Modifier.height(8.dp))

            // Step 3 — MIUI Autostart (no reliable check; always manual)
            OnboardingManualStepCard(
                title = stringResource(R.string.onboarding_autostart_title),
                description = stringResource(R.string.onboarding_autostart_desc),
                manualNote = stringResource(R.string.onboarding_autostart_manual),
                onOpen = { launchMiuiAutostart(context) }
            )
            Spacer(Modifier.height(8.dp))

            // Step 4 — MIUI battery saver "No restrictions" (no reliable check; always manual)
            OnboardingManualStepCard(
                title = stringResource(R.string.onboarding_miui_battery_title),
                description = stringResource(R.string.onboarding_miui_battery_desc),
                manualNote = stringResource(R.string.onboarding_miui_battery_manual),
                onOpen = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )
            Spacer(Modifier.height(8.dp))

            // Step 5 — Lock in Recents (informational only, no button)
            OnboardingInfoCard(
                title = stringResource(R.string.onboarding_recents_title),
                description = stringResource(R.string.onboarding_recents_desc)
            )

            Spacer(Modifier.height(24.dp))

            // Continue — always enabled; persists flag and navigates to SettingsScreen
            Button(
                onClick = {
                    scope.launch {
                        repo.setOnboardingShown(true)
                        onFinished()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.onboarding_btn_continue))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// --- Step card variants ---

@Composable
private fun OnboardingStepCard(
    title: String,
    description: String,
    isDone: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isDone) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.onboarding_status_done),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun OnboardingManualStepCard(
    title: String,
    description: String,
    manualNote: String,
    onOpen: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onOpen) {
                    Text(stringResource(R.string.onboarding_btn_open))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = manualNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun OnboardingInfoCard(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- MIUI intent helpers ---

/**
 * Attempts to open the MIUI AutoStart management screen.
 * Guards with resolveActivity() per miui-constraints.md testing notes.
 * Falls back to app details settings if the component isn't found.
 */
private fun launchMiuiAutostart(context: Context) {
    val miuiIntent = Intent().apply {
        component = ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
    }
    val canResolveMiui = context.packageManager.resolveActivity(
        miuiIntent, PackageManager.MATCH_DEFAULT_ONLY
    ) != null

    val intent = if (canResolveMiui) {
        Log.d(TAG, "Launching MIUI AutoStart activity")
        miuiIntent
    } else {
        Log.d(TAG, "MIUI AutoStart not found, falling back to app details")
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.d(TAG, "AutoStart fallback intent also failed: ${e.message}")
        // Nothing further to do — user will need to navigate manually
    }
}
