# Synthetic Step Lab

A minimal Android study app that writes **1 synthetic step approximately every 2 seconds** to Health Connect after the user explicitly grants `WRITE_STEPS` permission and presses **Start**.

## What it does

- Uses the native Android Health Connect SDK.
- Requests only `android.permission.health.WRITE_STEPS`.
- Writes a `StepsRecord(count = 1)` about every 2 seconds while the generator is running.
- Marks each record as `Metadata.activelyRecorded(...)` from the phone.
- Health Connect attributes inserted data to this app (`Synthetic Step Lab`).
- Provides Start, Stop, permission, and Health Connect buttons.
- Does not read Health Connect data.
- Does not use a foreground service; generation stops when the app leaves the foreground.

> This intentionally high write frequency is for study/testing. It is not a recommended production step-sync design; production apps should batch/sync more conservatively.

## Requirements

- Android 9 (API 28) or newer.
- Google Play services.
- Android 14+: Health Connect is built into the system.
- Android 13 and lower: install/update the Health Connect app from Google Play.

## Build with GitHub Actions

1. Create a GitHub repository.
2. Upload/push this entire project to the repository.
3. Open the repository's **Actions** tab.
4. Select **Build debug APK**.
5. Click **Run workflow** (or push to `main`/`master`).
6. When the workflow succeeds, open the workflow run.
7. Download the artifact named **synthetic-step-lab-debug-apk**.
8. Unzip the artifact and install `app-debug.apk` on the Android phone.

You may need to allow your browser/file manager to **Install unknown apps** for the sideload.

## Build in Android Studio

1. Install a current Android Studio version.
2. Open this project folder.
3. Let Gradle sync and install Android SDK 36 if prompted.
4. Use JDK 17 for Gradle.
5. Choose **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
6. The debug APK is written to:
   `app/build/outputs/apk/debug/app-debug.apk`

This source bundle intentionally does not include the Gradle Wrapper JAR. GitHub Actions supplies Gradle 8.13 explicitly, and Android Studio can sync the project directly.

## Use on the phone

1. Install and open **Synthetic Step Lab**.
2. Tap **Grant Health Connect permission**.
3. Allow **Steps > Write** access.
4. Tap **Start synthetic steps**.
5. Keep the app visible in the foreground while testing; leaving the app stops generation.
6. Tap **Stop** when finished.
7. Tap **Open Health Connect** to inspect/manage records attributed to the app.

## Data model

Each tick writes one `StepsRecord`:

- `count = 1`
- interval = previous write time to current write time (normally ~2 seconds)
- `Metadata.activelyRecorded`
- device type = `Device.TYPE_PHONE`
- manufacturer/model = the Android phone's build values

Using the previous record end time avoids overlapping intervals if a tick is slightly delayed.

## Important testing notes

- Android timing is not hard real-time, so "every 2 seconds" is approximate.
- If the app leaves the foreground or the process/activity is destroyed, generation stops.
- Health Connect may display/aggregate step records differently from the raw insertion cadence; inspect the app-specific records if the dashboard total does not increase one-for-one.
- Prefer a test device/profile or clear the synthetic records afterward so study data is not confused with real health tracking.
- You can revoke the permission or delete the app's Health Connect data at any time from Health Connect settings.
- Health Connect is not supported in Android work profiles.

## Project versions

- Android Gradle Plugin: 8.13.2
- Gradle in CI: 8.13
- Kotlin: 2.2.0
- compileSdk / targetSdk: 36
- minSdk: 28
- Health Connect client: 1.1.0 (stable)
