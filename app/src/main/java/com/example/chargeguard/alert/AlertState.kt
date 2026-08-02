package com.example.chargeguard.alert

enum class AlertState {
    IDLE,
    THRESHOLD_REACHED,      // T+0: normal notification sent, 2-min timer running
    ESCALATED_SOUND,        // T+2min: high-priority + sound notification sent, 3-min timer running
    ESCALATED_FULLSCREEN    // T+5min: full-screen intent fired (or sound repeated if disabled)
}
