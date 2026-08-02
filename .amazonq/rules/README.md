# ChargeGuard

Native Android app that helps users manually stop charging at a target
battery percentage on devices that lack a built-in "smart charging" /
charge-limit feature (e.g. POCO M3 Pro 5G on MIUI, stock, unrooted).

## Why this app exists

Non-rooted Android has no public API to physically stop charging current.
That control lives at the kernel/driver level and requires root. This app
does **not** attempt to bypass that. Instead it solves the same real-world
problem — protecting battery health — by watching the charge level in the
background and escalating alerts (notification → sound → full-screen
alarm-style screen) until the user unplugs the phone at their chosen
threshold.

## Target device / environment

- Primary test device: POCO M3 Pro 5G
- OS: MIUI (Android 11+ base), unrooted, stock ROM
- Min SDK: 26 (Android 8.0) — required for foreground service notification channels
- Target/Compile SDK: 34

## Constraints (do not violate these)

- No root, no shell `su`, no writing to `/sys/class/power_supply/*`
- No use of accessibility services to "fake" unplugging
- No third-party cloud backend — everything runs locally on-device
- Must survive MIUI's aggressive background app killing (see
  `.amazonq/rules/miui-constraints.md`)

## Where to look

- `.amazonq/rules/requirements.md` — functional & non-functional requirements
- `.amazonq/rules/architecture.md` — components, classes, data flow
- `.amazonq/rules/miui-constraints.md` — MIUI-specific background/battery quirks
- `.amazonq/rules/coding-conventions.md` — style, package layout, naming
