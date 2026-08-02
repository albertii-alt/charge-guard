package com.example.chargeguard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "charge_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val UPPER_THRESHOLD = intPreferencesKey("upper_threshold_percent")       // 50–100
        val LOWER_THRESHOLD = intPreferencesKey("lower_threshold_percent")       // 5–40
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val FULLSCREEN_ESCALATION = booleanPreferencesKey("fullscreen_escalation_enabled")
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val ONBOARDING_SHOWN = booleanPreferencesKey("onboarding_shown")         // false until user completes onboarding
    }

    val settings: Flow<ChargeSettings> = context.dataStore.data.map { prefs ->
        ChargeSettings(
            upperThresholdPercent = prefs[Keys.UPPER_THRESHOLD] ?: 80,
            lowerThresholdPercent = prefs[Keys.LOWER_THRESHOLD] ?: 20,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            fullScreenEscalationEnabled = prefs[Keys.FULLSCREEN_ESCALATION] ?: true,
            monitoringEnabled = prefs[Keys.MONITORING_ENABLED] ?: false,
            onboardingShown = prefs[Keys.ONBOARDING_SHOWN] ?: false
        )
    }

    suspend fun updateUpperThreshold(value: Int) {
        require(value in 50..100) { "upperThresholdPercent must be 50–100" }
        context.dataStore.edit { it[Keys.UPPER_THRESHOLD] = value }
    }

    suspend fun updateLowerThreshold(value: Int) {
        require(value in 5..40) { "lowerThresholdPercent must be 5–40" }
        context.dataStore.edit { it[Keys.LOWER_THRESHOLD] = value }
    }

    suspend fun setSoundEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = value }
    }

    suspend fun setFullScreenEscalationEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.FULLSCREEN_ESCALATION] = value }
    }

    suspend fun setMonitoringEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.MONITORING_ENABLED] = value }
    }

    suspend fun setOnboardingShown(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_SHOWN] = value }
    }
}
