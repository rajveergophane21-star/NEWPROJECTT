# Margin — Play Store Launch Checklist

Everything needed to publish, in order. The repo side is **done**; the rest are steps in your Google Play Console.

## ✅ Already done (in this repo)
- **Target API 35** (Android 15) — meets Google Play's requirement for new apps.
- **Signed release App Bundle** built: `dist/Margin-1.0.aab` (signed with your upload key).
- **Sensitive permissions cleaned up:** removed `QUERY_ALL_PACKAGES` (replaced with scoped `<queries>`) and the Play-restricted `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (battery step now opens the settings list instead).
- **Explicit `android:exported`** on every component; foreground-service type declared.
- **In-app accessibility disclosure** shown during onboarding.
- `versionCode 1`, `versionName "1.0"`.

## 🔑 Your signing key — READ THIS
- Your upload keystore is `upload-keystore.jks` (NOT committed to git) with these credentials:
  - store/key password: `margin-upload-2026`  · alias: `upload`
  - **Change this password and keep the file safe.** It is configured via `keystore.properties` (also git-ignored).
- **Back up `upload-keystore.jks` somewhere safe.** If you lose it you can still recover via Play App Signing's upload-key reset, but don't rely on that.
- **Use Play App Signing** (default for new apps): Google holds the real app-signing key; you only ever sign uploads with this upload key.

## 📦 Rebuilding the AAB later
```
# from the repo root, with the Android SDK available:
./gradlew :app:bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab
```
Bump `versionCode` (and usually `versionName`) in `app/build.gradle` for every new upload.

## ⚠️ One decision before you upload — the package name
The app's `applicationId` is **`com.anchor.app`** ("anchor" is a leftover from an earlier name). **It can never be changed after your first upload.** If you'd prefer something branded (e.g. `com.margin.app` or `com.yourname.margin`), tell me and I'll change it before you upload. Otherwise `com.anchor.app` is perfectly fine.

## 🏪 Play Console steps
1. **Create a developer account** ($25 one-time) at play.google.com/console, if you haven't.
2. **Create app** → name **Margin**, default language, **App**, **Free**.
3. **Upload the bundle:** Release → Testing → **Internal testing** (do this first, not Production) → Create release → upload `dist/Margin-1.0.aab` → roll out to yourself and test on a real device.
4. **Store listing** (Main store listing): paste the short/full description, category, etc. from `STORE_LISTING.md`. Add icon (already in the app), feature graphic, and **2–8 screenshots** (see the capture list in `STORE_LISTING.md`).
5. **Privacy policy:** host `PRIVACY.md` at a public URL and paste it in (App content → Privacy policy). **Replace the placeholder contact email first.**
6. **Data safety:** answer **"No data collected/shared"** per `STORE_LISTING.md`.
7. **App access:** the app needs Accessibility + Overlay enabled — provide test instructions so reviewers can reach all features (no login needed; explain how to enable the accessibility service).
8. **Accessibility / permissions declaration:** when prompted about AccessibilityService use, paste the justification from `STORE_LISTING.md`. Google may ask for a short screen-recording showing the blocking flow — record one (open a blocked app → stop screen appears).
9. **Content rating:** complete the IARC questionnaire → Everyone.
10. **Target audience & content:** not directed at children.
11. **Promote the internal release to Production** once you've tested and the review passes.

## 🔎 Before you hit publish — sanity test on a device
- Grant Accessibility + Overlay in onboarding.
- Add a Block rule (all-day) for one app → open it → the stop screen appears **over** it; "Turn back" returns home.
- Switch the rule to Friction → open the app → breath + "Open anyway" after the pause.
- Add a Commitment lock with a schedule active now → confirm you can't disable it.
- Add a habit, check it off, confirm the streak.
- Reboot the phone → confirm blocking still works.
