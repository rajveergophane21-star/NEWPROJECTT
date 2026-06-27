# ⚓ Anchor — *Decide once. Stay the course.*

An **offline, non-root Android app** that helps you take back your attention. It
pairs a real **app blocker** (scheduled, per-app, with intervention screens) with
the two things the research says blocking needs to actually stick: a **commitment
device** and **replacement behaviours**.

No account. No internet permission. Nothing leaves your device.

> **Install:** [`dist/Anchor-1.0.apk`](dist/Anchor-1.0.apk) — sideload on any Android 8.0+ phone.

---

## Why it's built the way it is

This isn't a generic "screen time" wrapper. The design follows the evidence:

- **Friction works in the moment, but not on its own.** A mandatory pause reliably
  stops the *current* impulse, yet studies show it doesn't durably reduce how often
  you reach for an app over weeks. So Anchor never relies on friction alone.
- **Commitment beats willpower.** A *Ulysses contract* — a decision your past self
  locks so your future self can't wriggle out — is the strongest lever. That's the
  **Commitment lock**: while a rule is active, you can't switch it off.
- **You can't remove a habit, only replace it.** Cutting an app leaves a gap.
  Anchor makes you name the **replacement behaviour** and tracks it like a habit.
- **Calm by design.** The interface is deliberately low-saturation. A quiet surface
  is itself attention-protective; bright, rewarding UI works against the goal.

---

## What it does

**Shield — the blocker**
- Create **rules**: a set of apps + a **schedule** (time slots × days of week) + a mode.
- Two intervention modes:
  - **Block** — a calm wall; the only way through is to put the phone down.
  - **Friction** — a mandatory ~12-second breath, then an honest choice (most urges crest and fall in under a minute).
- **Commitment lock** (strict mode): a locked rule can't be edited or disabled while it's active.
- **Focus now**: seal off every rule's apps for 25 / 45 / 60 minutes on demand.

**Habits — replacement behaviours**
- Tiny habits anchored to an existing routine ("After I ___"), one-tap check-ins,
  streaks, and a contribution heatmap.

**Insights**
- Urges turned away vs. opened anyway, a 14-day trend, and your streaks — with an
  honest note on what the science does and doesn't support.

---

## How the blocking works (technical)

Fully **non-root**, using Android's own controls:

| Concern | Approach |
|---|---|
| Which app is in front | `UsageStatsManager.queryEvents` sampled ~1×/sec in a foreground service (no Accessibility service required) |
| Showing the intercept | A normal full-screen `Activity` launched from the background — permitted because the app holds **`SYSTEM_ALERT_WINDOW`** (an official background-activity-launch exception) |
| Staying alive | A `specialUse` **foreground service** (`FOREGROUND_SERVICE_SPECIAL_USE` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`, per Android 14 rules), `START_STICKY`, restarted on boot |
| Scheduling | The service evaluates each rule's time-windows against the clock in real time — no exact-alarm permission needed |
| Choosing apps | `PackageManager` launcher query (`QUERY_ALL_PACKAGES`) |

**Permissions requested (and exactly why):**
- `PACKAGE_USAGE_STATS` — read *which* app is foreground (never content)
- `SYSTEM_ALERT_WINDOW` — draw the intercept over a blocked app
- `POST_NOTIFICATIONS` — the persistent shield notification Android requires
- `FOREGROUND_SERVICE` / `_SPECIAL_USE` — run the monitor
- `RECEIVE_BOOT_COMPLETED` — re-arm after reboot
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — optional; stops the OS killing the shield
- `QUERY_ALL_PACKAGES` — list installable apps to block

### Honest limitations
- This is **friction, not a vault.** A determined user can disable it in system
  settings unless Commitment lock is active — and even then OEM settings exist. The
  goal is to beat *impulse*, not to imprison you.
- Aggressive OEM power management (Xiaomi/MIUI, some Samsung, etc.) can kill
  background services. The battery-optimisation exemption helps; see
  [dontkillmyapp.com](https://dontkillmyapp.com).
- Detection has ~1s latency, so a blocked app may flash briefly before the intercept.
- For Google **Play** distribution, `QUERY_ALL_PACKAGES`, accessibility/usage access,
  and the `specialUse` FGS each require a policy declaration. Sideloading needs none.

### Research the design draws on
- Fogg Behaviour Model (B=MAP) · Gollwitzer, implementation intentions
- Lembke, *Dopamine Nation* — variable reward & self-binding
- PNAS 2023 (friction/added-delay study) — per-instance abandonment, limited durable effect
- ACM CHI / CSCW work on digital-wellbeing tool **abandonment**

---

## Build it yourself

Requires JDK 17, Android SDK (platform 34, build-tools 34.0.0).

```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

## Stack
Kotlin · Android Views · Material 3 · custom `RingView`/`HeatmapView` ·
`SharedPreferences`+JSON persistence · **no network permission** ·
Gradle 8.9 wrapper, AGP 8.7.3 · minSdk 26 / targetSdk 34.

*Anchor is a self-help tool, not medical treatment.*
