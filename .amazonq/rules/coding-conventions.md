# Coding Conventions

- Language: Kotlin only, no Java files.
- UI: Jetpack Compose, Material 3 components.
- Package root: `com.example.chargeguard` (rename to your real applicationId
  before building/signing).
- Prefer immutable data classes + `StateFlow` for UI state over mutable
  live objects.
- All user-facing strings go in `strings.xml`, no hardcoded UI text in
  Kotlin files — makes future localization trivial.
- Keep `ChargeMonitorService` free of UI code; it should only touch
  `AlertEscalationManager`, `NotificationHelper`, and `SettingsRepository`.
- Every broadcast receiver and timer callback must check
  `isCharging == true` before proceeding — never assume state carried
  over correctly from the previous callback.
- Log key state transitions (`IDLE → THRESHOLD_REACHED`, etc.) at
  `Log.d` level tagged `"ChargeGuard"` to make on-device debugging via
  `adb logcat` straightforward, since this is a personal-use app with no
  crash reporting service.
- No third-party analytics or crash-reporting SDKs.
