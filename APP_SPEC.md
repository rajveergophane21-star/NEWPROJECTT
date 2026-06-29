# App Spec

A private, offline Android app that helps you stop doom-scrolling, especially short-form video.

**Platform:** Android, non-root, fully offline — no account, no servers, no analytics; all data stays on the device.

## Core features

### 1. Block or Friction apps — for a specific time or duration
The user picks apps and a schedule (specific hours/days, "all day," or an on-the-spot timer like "next 60 minutes") and a mode. In both modes the blocked app is **closed** — the user is sent back to the home screen, not left inside it.

- **Block:** the app closes and a full-screen stop screen is shown; the only way forward is to turn back.
- **Friction:** the app closes and a short mandatory pause is shown (a breath / countdown); afterward the user chooses to reopen it or stay out.

### 2. Block or Friction short-form video feeds — for a specific time or duration
Blocks the *feed itself*, not the whole app: Instagram Reels, YouTube Shorts, TikTok For You, Snapchat Spotlight, Facebook Reels. The user can still use the app for messaging while the short-form feed is blocked. Same Block/Friction modes and time/duration controls; when triggered, the user is taken off the feed.

### 3. Reel counter — a live on-screen counter
While the user watches reels/shorts, a small floating box appears in the corner showing a live count of reels watched (and optionally time spent), updating in real time as they scroll. The user can set a daily reel limit; once exceeded, that feed switches to Friction or Block for the rest of the day.

## Supporting features

- **Daily habits:** a check-off list with a history grid and alarm-style reminders (ring until stopped).
- **Commitment lock:** mark a rule as committed and it can't be turned off or edited while it's active.

## How it works (tech)

Uses Android's AccessibilityService to detect the foreground app and the short-form feed, an overlay (display-over-other-apps) permission for the stop screen and the floating corner counter, and AlarmManager for habit reminders.
