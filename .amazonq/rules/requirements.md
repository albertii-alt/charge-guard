# Requirements

## Functional Requirements

### FR1 — Battery monitoring
- App must monitor battery percentage in real time while the device is
  connected to a charger.
- Use `BroadcastReceiver` on `Intent.ACTION_BATTERY_CHANGED` for level
  updates, and `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` for
  plug state changes.
- Confirm charging state via `BatteryManager.EXTRA_STATUS` (must equal
  `BATTERY_STATUS_CHARGING` or `BATTERY_STATUS_FULL`), not level alone.

### FR2 — User-configurable thresholds
- Upper threshold ("stop charging around here"): slider, range 50–100%,
  default 80%.
- Lower threshold ("plug in around here"): slider, range 5–40%, default 20%.
- Persist settings locally (`DataStore` preferred over `SharedPreferences`).

### FR3 — Escalating alert system
When charging and level reaches/exceeds the upper threshold:
1. **T+0:** standard notification, normal priority, "Battery at X% — consider unplugging."
2. **T+2 min (if still charging):** high-priority notification + sound.
3. **T+5 min (if still charging):** full-screen `Activity` launched via
   `Notification.Builder.setFullScreenIntent`, alarm-clock style, requires
   explicit dismiss/snooze tap.
- All alerts must stop immediately once `ACTION_POWER_DISCONNECTED` fires.
- Snooze option: re-alert after 10 min if user dismisses without unplugging.

### FR4 — Low-battery reminder (optional, lower priority than FR3)
- If level drops to/below the lower threshold and device is NOT charging,
  send a single normal-priority notification to plug in.

### FR5 — Onboarding / permissions flow
- First-launch screen explains why the app needs to run in the background.
- Buttons that deep-link to:
  - MIUI Autostart settings
  - MIUI "No restrictions" battery saver exemption for this app
  - Notification permission request (Android 13+)
  - Full-screen intent permission confirmation (Android 14+)
- App must detect (best-effort) whether these are already granted and
  skip/gray out steps already completed.

### FR6 — Persistent status
- Foreground service shows an ongoing low-priority notification with
  current battery % and target threshold while charging is active.
- Service stops itself when unplugged (no need to run while on battery,
  except for the optional FR4 check — see architecture doc for how this
  is handled with minimal background footprint).

## Non-Functional Requirements

- **No root usage anywhere in the codebase.**
- **No network calls.** Fully offline app.
- Battery/CPU footprint of the monitoring service itself must be
  negligible — it should be event-driven off broadcasts, not polling.
- Must survive process death and restart via `BOOT_COMPLETED` and
  service restart policies (see `miui-constraints.md`).
- UI: simple, 2-screen app (Settings/Home + Onboarding). Jetpack Compose
  preferred over XML layouts.
- Minimum supported Android version: 8.0 (API 26).

## Explicit Non-Goals

- Does NOT actually stop/limit charging current (impossible without root
  on this device).
- No cloud sync, accounts, or analytics.
- No support for other charge-control hardware (smart plugs) in v1 —
  phone-only solution by design.
