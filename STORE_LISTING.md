# Google Play Store Listing — Margin

Copy/paste-ready text + the exact answers for the Console forms.

## App name
**Margin**

## Short description (≤ 80 chars)
> Block distracting apps on your schedule. Private, offline, no account.

## Full description (≤ 4000 chars)
> **Margin is a calm, private app blocker that helps you reach for your phone less.**
>
> Choose the apps that pull you in, set when they're off-limits, and decide what happens when you open them. No account, no ads, no data leaves your phone.
>
> **Two ways to intervene**
> • **Block** — a calm wall. When you open a blocked app, a full-screen stop screen appears over it. The only way through is to turn back.
> • **Friction** — a short, mandatory breath. After a few seconds you can choose to continue or turn back. Just enough of a pause to break the autopilot.
>
> **Commit to it**
> Add a **commitment lock** to any block and, while its schedule is active, you can't turn it off or edit it — you decide in a clear moment, not a weak one.
>
> **Build better habits**
> Add a few tiny daily habits and check them off. A simple streak keeps you honest. That's it — no journals, no questionnaires.
>
> **Private by design**
> Margin is fully offline. It has no internet permission, no servers, no analytics, and no account. Your blocks and habits live only on your device. The accessibility permission is used only to notice which app is in front so the block screen can appear — it never reads your screen content.
>
> Reclaim the margins of your day.

## Category & tags
- **Category:** Productivity (alternative: Health & Fitness)
- **Tags:** digital wellbeing, focus, app blocker, screen time, habits

## Content rating
Answer the IARC questionnaire honestly — Margin has no violence, no user-generated content, no ads, no data sharing → expected rating **Everyone / PEGI 3**.

## Data safety form (Play Console → App content → Data safety)
- **Does your app collect or share any user data?** → **No.**
- **Is all of the user data encrypted in transit?** → Not applicable (no data is transmitted).
- **Do you provide a way for users to request data deletion?** → Data is on-device only; uninstalling removes it.
- Result: a clean "**No data collected**" data-safety label.

## Privacy policy URL
Host `PRIVACY.md` (e.g. GitHub Pages, or paste into a free host) and put the public URL here. **Required** because the app uses sensitive permissions.

## Permission / API declarations (Console will ask for these)
- **AccessibilityService usage (Prominent disclosure + Permissions declaration):**
  > Margin uses the AccessibilityService API solely to detect which application is currently in the foreground, so it can display the user's pre-configured block/intervention screen at the right moment. It does not read screen content, collect, or transmit any data. This is core, user-initiated functionality (a non-root app blocker) that cannot be achieved another way on unrooted Android. Users are shown a clear in-app disclosure and must explicitly enable the service.
- **Foreground service type "specialUse":**
  > A persistent foreground service keeps the app-blocking shield running so scheduled blocks are enforced in the background. Declared as specialUse because it is a digital-wellbeing blocker that monitors the foreground app to enforce user-defined schedules.
- **Usage access (PACKAGE_USAGE_STATS):** fallback foreground-app detection when the accessibility service is unavailable.

## Screenshots to capture (phone, 1080×1920 or similar; 2–8 required)
1. **Blocks** tab with a couple of rules (one showing the LOCKED tag).
2. The **block** stop screen ("Not now.") over an app.
3. The **friction** breath screen with the countdown.
4. The **rule editor** (apps + schedule + Block/Friction + Commitment lock).
5. **Habits** tab with the today-progress box and a streak.
6. The **onboarding/permissions** screen (shows the privacy stance).

## Feature graphic
1024×500 PNG — the lantern + "MARGIN" wordmark on the warm background, tagline "Reach for your phone less."
