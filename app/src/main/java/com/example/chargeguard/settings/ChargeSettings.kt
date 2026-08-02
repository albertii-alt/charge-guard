package com.example.chargeguard.settings

data class ChargeSettings(
    val upperThresholdPercent: Int = 80,          // alert when charging reaches this level (50–100)
    val lowerThresholdPercent: Int = 20,           // remind to plug in when level drops here (5–40)
    val soundEnabled: Boolean = true,              // play sound on T+2min escalation
    val fullScreenEscalationEnabled: Boolean = true, // show full-screen alert at T+5min
    val monitoringEnabled: Boolean = false,        // whether ChargeMonitorService should be running
    val onboardingShown: Boolean = false           // true once user has seen and dismissed onboarding
)
