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

Also: camera rep counting (ML Kit Pose) for squats, push-ups, wall push-ups and glute bridge, with the
detected skeleton drawn over the video, a live joint angle readout and a Strict/Normal/Easy sensitivity
setting;
GPS walk tracking (foreground service, offline route preview, distance/pace/kcal, history).

Watch data (Redmi Watch 5 Lite): read-only from Health Connect, where Mi Fitness writes steps, heart
rate, sleep, active calories and distance. Shown on the Today screen.

Toolchain: AGP 9.5, Gradle 9.9, Kotlin 2.4, compileSdk 37.1.

Food diary with photo analysis and calorie balance (see below).

Posture and skin check (Progress tab): cautious photo observations from Gemini with a "see a doctor"
flag, a saved history and a first-vs-latest photo comparison. Wellness only, never a diagnosis; skin
photos are kept only if the user opts in.

Adaptive plan: the daily plan gets lighter for newcomers and after a quiet week, and a bit tougher after
several consistent days (`PlanAdapter`, based on the days before today so the plan stays stable all day).
Weekly report (Progress tab): active days and minutes (WHO goal 150/week), comparison with the previous
week, steps and sleep from the watch, average calories eaten, and simple tips.

Backup (Settings): export everything to one JSON file via the system file picker and restore from it
after confirmation (`backup/BackupCodec`, replaces the data in a single transaction). Photos and the
Gemini key are never included.

Exercise illustrations: 13 of 14 exercises have an animated stick figure (two key poses, drawn in code
by a small forward/inverse-kinematics engine in `figure/`). `FigureRenderTest` renders every pose to
`app/build/figures/sheet.png` and checks the figures stay inside the scene. The eye-rest exercise
still uses its emoji.

Not yet: video analysis.

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

## Food photo analysis

Settings → "AI food analysis": paste your own Gemini API key (create one in Google AI Studio). The
app sends a downscaled photo straight to the Gemini API (`gemini-3.8-flash`, see `GeminiApi.MODEL`)
and asks for a JSON list of foods with portion weight, kcal and macros. You review and correct the
estimate before it is saved; photos are never stored. The key lives only in the app's private
storage on the phone and is never part of the APK or the repository. Without a key, food can still be
added manually: typing a name suggests foods from a built-in offline table (~120 items, kcal and macros
per 100 g, `FoodDb`), and calories are calculated from the entered weight. The kcal field stays
editable, and with a key an unknown food can be looked up by the AI (per 100 g). The calorie balance is resting energy (Mifflin-St Jeor) + active energy from the
watch (or tracked walks) versus eaten calories.
