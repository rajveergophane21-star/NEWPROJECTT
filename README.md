# 🔥 Momentum — Habits & Detox

A **100% offline** Android app that helps you build good habits and break the
compulsive-phone loop. No account, no internet, no tracking — every byte of your
data stays on your device in private app storage.

> **Download:** grab the ready-to-install APK from [`dist/Momentum-1.0.apk`](dist/Momentum-1.0.apk).

---

## What it does

**Habits tab**
- Add *tiny* habits and attach each to an existing routine ("**After I** brush my teeth…").
- One-tap daily check-in with an immediate celebration.
- Per-habit **current + best streak** and a GitHub-style **contribution heatmap**.
- A daily completion ring and at-a-glance stats.

**Detox tab**
- Start a **phone-free focus session** (15 / 25 / 45 / 60 min) with a full-screen timer.
- When a craving hits, tap **"I feel an urge"** to run a guided **urge-surfing** breathing
  exercise — ride the wave instead of acting on it.
- Tracks focus minutes, sessions today, **urges surfed**, and your longest session.

---

## The psychology it's built on

Every feature maps to established behavior-change research:

| Feature | Principle |
|---|---|
| "Keep it tiny" prompts | **Fogg Behavior Model — B = MAP** (Motivation × Ability × Prompt). Shrinking the behavior is the most reliable lever. |
| "After I ___" cue field | **Implementation intentions** (Gollwitzer) — if-then plans roughly 2–3× follow-through. |
| Streaks + heatmaps | **Don't break the chain** / **loss aversion** — we work harder to avoid losing progress than to gain it. |
| Check-in celebration | **Immediate reward** wires the habit loop (cue → routine → reward). |
| Phone-free focus sessions | Phones run on **variable rewards** (Lembke); a detox session removes the slot machine and adds **friction**. |
| Urge-surfing breathing | **Mindfulness / CBT** — cravings are waves that peak and pass in ~90s if you don't feed them. |

> Momentum is a self-help tool, not medical treatment.

### Sources
- BJ Fogg, *Tiny Habits* / Fogg Behavior Model (B=MAP)
- Gollwitzer, P. M. — Implementation intentions (if-then planning)
- Anna Lembke, *Dopamine Nation* — variable reward & smartphone compulsion
- Research on social-media digital detox and screen-time reduction

---

## Build it yourself

Requirements: JDK 17+, Android SDK (platform 34, build-tools 34.0.0).

```bash
# point the build at your SDK
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# build a debug APK
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

## Install on a phone

1. Copy `dist/Momentum-1.0.apk` to your Android device.
2. Open it and allow "install from unknown sources" if prompted.
3. Launch **Momentum**.

(The APK is signed with the standard Android **debug** key — fine for personal
sideloading. For Play Store distribution you'd sign a release build with your own key.)

## Tech

- Kotlin · Android Views · Material 3 (dark)
- Custom `RingView` (progress ring) and `HeatmapView` (contribution grid)
- Local persistence via `SharedPreferences` + JSON — **no network permission requested**
- minSdk 26 · targetSdk 34
