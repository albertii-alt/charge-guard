package com.example.chargeguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chargeguard.onboarding.OnboardingScreen
import com.example.chargeguard.settings.ChargeSettings
import com.example.chargeguard.settings.SettingsRepository
import com.example.chargeguard.settings.SettingsScreen
import com.example.chargeguard.ui.theme.ChargeGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = SettingsRepository(applicationContext)
        setContent {
            ChargeGuardTheme {
                val settings by repo.settings.collectAsStateWithLifecycle(
                    initialValue = ChargeSettings()
                )
                if (!settings.onboardingShown) {
                    OnboardingScreen(
                        repo = repo,
                        onFinished = { /* state update via DataStore triggers recomposition */ }
                    )
                } else {
                    SettingsScreen(repo = repo)
                }
            }
        }
    }
}
