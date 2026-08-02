# MIUI-Specific Constraints

MIUI (used on POCO devices) applies far more aggressive background
process/battery management than stock Android. These are not optional
edge cases — without handling them, `ChargeMonitorService` will get
killed within roughly 15–60 minutes and the app will silently stop
working.

## Required user-side settings (app should guide the user to these, not attempt to set them programmatically — MIUI does not expose APIs for that)

1. **Autostart permission**
   - Path: Settings → Apps → Manage apps → ChargeGuard → Autostart → Enable
   - Deep link intent (best-effort, varies by MIUI version):
     ```kotlin
     Intent().apply {
         component = ComponentName(
             "com.miui.securitycenter",
             "com.miui.permcenter.autostart.AutoStartManagementActivity"
         )
     }
     ```
   - Always wrap in try/catch and fall back to opening the app's generic
     settings page (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) if the
     component isn't found, since this activity name changes across MIUI
     versions.

2. **Battery saver exemption ("No restrictions")**
   - Path: Settings → Battery & performance → App battery saver →
     ChargeGuard → No restrictions
   - Also request exemption from Doze via standard Android API:
     ```kotlin
     Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
     ```

3. **Lock app in Recents**
   - Not programmatically controllable. Mention it as a suggested manual
     step in onboarding copy: "swipe down on the app card in Recents to
     lock it."

4. **"Show on lock screen" / notification permission**
   - Needed so the full-screen alert activity actually shows over the
     lock screen. Set `setShowWhenLocked(true)` and
     `setTurnScreenOn(true)` on `FullScreenAlertActivity`, and additionally
     call `KeyguardManager.requestDismissKeyguard()` if targeting a
     smoother unlock flow.

## Testing notes for Amazon Q

- Because these MIUI battery-management screens can't be fully automated,
  do not write instrumented tests that assume Autostart/Doze exemption
  are granted — assume worst case (killed process) and test that
  `BootReceiver` + service restart logic recovers monitoring state
  correctly from persisted `ChargeSettings`.
- When in doubt about whether a given MIUI intent/component exists on the
  test device, guard with `packageManager.resolveActivity()` before
  launching it.
