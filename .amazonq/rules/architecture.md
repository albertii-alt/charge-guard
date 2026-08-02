# Architecture

## Tech stack
- Kotlin, Jetpack Compose for UI
- Min SDK 26 / Target & Compile SDK 34
- Jetpack DataStore (Preferences) for settings persistence
- No DI framework needed for v1 — app is small enough for manual wiring
  (or Hilt if Amazon Q defaults to it, that's fine too)

## Package layout

```
com.example.chargeguard/
├── MainActivity.kt                 # Compose host, navigation between Home/Onboarding
├── service/
│   └── ChargeMonitorService.kt     # Foreground service, owns the BroadcastReceiver
├── receiver/
│   ├── BatteryStateReceiver.kt     # Handles ACTION_BATTERY_CHANGED / POWER_CONNECTED/DISCONNECTED
│   └── BootReceiver.kt             # Restarts service on BOOT_COMPLETED
├── alert/
│   ├── AlertEscalationManager.kt   # State machine: T+0 / T+2min / T+5min logic, snooze timers
│   ├── FullScreenAlertActivity.kt  # Alarm-style screen for the T+5min escalation
│   └── NotificationHelper.kt       # Channel creation, notification building
├── settings/
│   ├── SettingsRepository.kt       # DataStore wrapper (thresholds, alert prefs)
│   └── SettingsScreen.kt           # Compose UI: sliders + toggles
├── onboarding/
│   └── OnboardingScreen.kt         # Compose UI with deep-link buttons to MIUI settings
└── ui/theme/                       # Compose theme files
```

## Core flow

1. `MainActivity` checks onboarding-completion flag on launch. If MIUI
   autostart/battery-exemption steps aren't confirmed, show
   `OnboardingScreen` first.
2. `ChargeMonitorService` is started (as a foreground service) either:
   - manually by the user toggling "Enable monitoring" in `SettingsScreen`, or
   - automatically by `BootReceiver` if monitoring was previously enabled.
3. Inside the service, `BatteryStateReceiver` is registered dynamically
   (not in the manifest, since `ACTION_BATTERY_CHANGED` is a sticky
   broadcast that cannot be registered statically post-Android 8).
4. On each battery update:
   - Service reads current level (`EXTRA_LEVEL` / `EXTRA_SCALE`) and
     charging status (`EXTRA_STATUS`).
   - Passes `(level, isCharging)` to `AlertEscalationManager`.
5. `AlertEscalationManager` is a simple state machine:
   - `IDLE` → `THRESHOLD_REACHED` (fires T+0 alert, starts a 2-min timer)
   - `THRESHOLD_REACHED` → `ESCALATED_SOUND` (T+2min timer fires, still charging)
   - `ESCALATED_SOUND` → `ESCALATED_FULLSCREEN` (T+5min timer fires, still charging)
   - Any state → `IDLE` immediately on `ACTION_POWER_DISCONNECTED`
   - Timers implemented with `Handler(Looper.getMainLooper()).postDelayed`
     or `WorkManager` one-off requests if Amazon Q prefers a more
     process-death-resistant approach (service is already foreground so
     plain Handler timers are acceptable here).
6. `NotificationHelper` owns two notification channels:
   - `"charge_status"` — low priority, ongoing foreground service notification
   - `"charge_alerts"` — high priority/alarm-like, for T+2min and T+5min escalations
7. `FullScreenAlertActivity` launches via `setFullScreenIntent()` on the
   `"charge_alerts"` channel notification; requires `USE_FULL_SCREEN_INTENT`
   permission (auto-granted below API 34, runtime-requestable on 34+).

## Data model

```kotlin
data class ChargeSettings(
    val upperThresholdPercent: Int = 80,
    val lowerThresholdPercent: Int = 20,
    val soundEnabled: Boolean = true,
    val fullScreenEscalationEnabled: Boolean = true,
    val monitoringEnabled: Boolean = false
)

enum class AlertState { IDLE, THRESHOLD_REACHED, ESCALATED_SOUND, ESCALATED_FULLSCREEN }
```

## Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
```

`FOREGROUND_SERVICE_SPECIAL_USE` requires a declared use-case string in
the manifest (`android:value="battery-charge-monitoring"` in the
`<service>` tag's `<property>` block) for Play Store compliance — include
this even though this is a sideloaded/personal app, since Amazon Q may
scaffold for Play compliance by default.
