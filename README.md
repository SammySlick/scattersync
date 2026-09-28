# ScatterSync

Sam's own Health Connect sync app. Replaces HCGateway's Android app.

## What it does
- Reads Health Connect with FULL pagination (no 1,000-record cap)
- Uploads to the HCGateway-compatible server (same protocol, same DB, same collections)
- Background sync every 30 minutes via WorkManager (survives restarts)
- Real status screen: last-sync time per record type
- Backfills 30 days on first run per type

## Supported types
HeartRate, Steps, SleepSession, Weight, BodyFat, ExerciseSession,
TotalCaloriesBurned, RestingHeartRate, BasalMetabolicRate, Nutrition

## Build
GitHub Actions builds the APK on every push — download from the run's
Artifacts section (ScatterSync-debug-apk), or build locally:

    gradle :app:assembleDebug

The APK lands in app/build/outputs/apk/debug/app-debug.apk

## Install (sideload)
1. Copy the APK to the phone
2. Tap it — allow "install unknown apps" for the browser/file manager
3. First run: tap "Grant Health Connect permissions"
4. Fill in server URL / username / password (same account as HCGateway)
5. Tap "Sync now", then "Enable 30-min background sync"

## Switching over
- Install ScatterSync, get one successful sync
- THEN uninstall the old HCGateway app
- Both apps share the same server and dedupe by record ID, so no data is lost
