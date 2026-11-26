# Sport Assistant

Android app (Kotlin + Jetpack Compose) for desk workers 40+: gentle daily plans, progress, reminders.
UI languages: Ukrainian and English (follows the system language).

## Android app

```
.\gradlew.bat :app:assembleDebug     # APK: app/build/outputs/apk/debug/
```

Implemented (MVP): onboarding questionnaire, 14 exercises with step-by-step text, daily plan that
adapts to complaints, workout runner with timers, points / streak / badges, reminders
(move, water, teeth) via WorkManager.

Also: camera rep counting (ML Kit Pose) for squats, push-ups, wall push-ups and glute bridge;
GPS walk tracking (foreground service, offline route preview, distance/pace/kcal, history).

Not yet: Health Connect (Redmi Watch 5 Lite), food photo log,
photo-based observations, exercise illustrations (emoji placeholders for now).

## Updates

Settings → "Check for updates" reads a static `version.json` over HTTPS:

```json
{"versionCode": 3, "versionName": "0.3.0", "apkUrl": "https://example.com/app.apk", "sha256": "<optional hex>", "notes": "What's new"}
```

Build with the URL baked in: `.\gradlew.bat :app:assembleDebug -PupdateUrl=https://example.com/version.json`
(or put `updateUrl=...` in `gradle.properties`). Any static HTTPS host works, e.g. GitHub Releases.
Android only installs the update if it is signed with the same key as the installed app, so keep
using the same keystore for every build. Raise `versionCode` in `app/build.gradle.kts` per release.
Without `updateUrl` the button reports that no source is configured.

## Install on a phone over USB

Enable Developer options, then USB debugging (on Xiaomi also "Install via USB" and set USB mode to
"File transfer"), accept the RSA prompt on the phone, check `adb devices`, then:
`adb install -r app\build\outputs\apk\debug\app-debug.apk`.

## AI proxy (`proxy/`)

Keeps provider keys off the device. Not used by the app yet; it is ready for the food-photo stage.

```
cd proxy
$env:APP_TOKEN="..."; $env:GEMINI_API_KEY="..."; node server.mjs
```

`POST /v1/analyze` with header `x-app-token`, body
`{"task":"food|posture|skin","image":"<base64>","mimeType":"image/jpeg","lang":"uk","provider":"gemini|claude"}`.
Gemini is the default; if it fails and a Claude key is set, the proxy falls back to Claude.
