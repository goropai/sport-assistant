# Sport Assistant

An Android app for people who sit at a desk all day and want to stay healthy rather than compete:
a gentle daily workout plan that adapts to how you actually train, rep counting with the phone
camera, GPS walks, a food diary with photo analysis and a calorie balance, watch data, reminders
that keep you from sitting for hours, and a weekly report.

- Kotlin, Jetpack Compose, Material 3. `minSdk 26`, `targetSdk 35`, `compileSdk 37.1`.
- UI languages: Ukrainian and English (follows the system language).
- Built for a 40+ desk-based lifestyle; **wellness only, not a medical device**. Nothing in the app
  diagnoses or treats anything.

## Features

### Training
- **Daily plan** from 14 exercises with step-by-step explanations. Exercises that match your
  complaints (neck, back, shoulders, eyes, legs) come first, and the set rotates daily.
- **Adaptive difficulty** (`PlanAdapter`): lighter for newcomers and after a quiet week, a little
  tougher after several consistent days. It looks at the days *before* today, so the plan does not
  change after you finish a workout.
- **Animated illustrations**: 13 of the 14 exercises have a stick figure that moves between two key
  poses, drawn in code by a small forward/inverse-kinematics engine (`figure/`). Lists show a still
  thumbnail. The eye-rest exercise has no figure and shows a category icon.
- **Points, streaks and achievements.**

### Rep counting
- **Camera** (ML Kit Pose, on-device): squats, push-ups, wall push-ups and glute bridge. The detected
  skeleton is drawn over the video, the tracked joint shows its live angle, and sensitivity
  (Strict / Normal / Easy) is adjustable.
- **Nose touch**: push-ups counted by touching a phone lying on the floor with your nose; every touch
  of the screen is one repetition (`TouchRepCounter`, bursts closer than 0.7 s are ignored).

### Walks
- GPS tracking in a foreground service (platform `LocationManager`, no Google Play services needed),
  with distance, pace and calories. The route is drawn offline (no map tiles). Walks under 100 m are
  discarded; a walk counts toward your streak and points.

### Food diary
- Add food by **photo** (Gemini), from the **gallery**, or **manually**.
- Manual entry: type a name and pick from a built-in offline table of ~120 foods (`FoodDb`, kcal and
  macros per 100 g); calories are calculated from the weight and stay editable. For an unknown food
  the AI can look up values per 100 g. Several foods can be added at once with **+**.
- Every entry belongs to **breakfast, lunch, dinner or a snack**. The default is the meal expected at
  the current hour if it is not logged yet today, otherwise a snack (`MealPicker`): 05-10 breakfast,
  11-15 lunch, 17-22 dinner.
- The diary is grouped by meal with a total for each. Tap an entry to edit it, and add more foods to
  the same meal from the edit dialog; the foods you eat most are offered as one-tap chips.
- **Calorie balance**: eaten versus spent, where spent = resting energy (Mifflin-St Jeor) + active
  energy from the watch (or from tracked walks when there is no watch data).

### Health and progress
- **Watch data** (tested target: Redmi Watch 5 Lite) read-only from Health Connect, where Mi Fitness
  writes steps, heart rate, sleep, active calories and distance. The app never talks to the watch
  directly.
- **Weekly report** (Progress tab): active days and minutes against the WHO's 150 minutes per week,
  comparison with the previous week, steps and sleep, average calories eaten, and short tips.
- **Posture and skin check** (Progress tab): cautious photo observations from Gemini with a "consider
  seeing a doctor" flag, a saved history and a first-versus-latest photo comparison. The prompts
  forbid diagnosing; skin photos are kept only if you opt in.

### Reminders
Movement and water at an interval you choose, inside the hours you choose (optionally weekdays only),
and brushing teeth at two times you choose. Each reminder is a one-off WorkManager job that schedules
the next one from a pure, tested rule (`reminders/Schedule`). Changes apply immediately, and a
reminder the device slept through is skipped instead of arriving late.

### Data
- Everything is stored on the phone (Room and DataStore). Android's own cloud backup is switched off.
- **Backup** (Settings): export everything to one JSON file through the system file picker and restore
  it later after confirmation. Photos and the Gemini key are never included.

## Setup in the app

- **Gemini key** (for food photos, nutrition lookup and the posture/skin check): Settings, "AI food
  analysis", paste your own key from Google AI Studio. It is stored only in the app's private
  storage and is never part of the APK or this repository. Photos are sent to Google for analysis and
  are not stored by the app. Without a key, manual food entry still works.
- **Watch**: install Mi Fitness, let it sync to Health Connect, then press the connect button on the
  Today screen and allow the five read permissions.
- **Notifications** are requested at the end of the first questionnaire; **camera** and **location**
  when the related feature is first used.
- Reminders can be delayed by aggressive battery savers (Xiaomi/HyperOS in particular): set the app's
  battery mode to "No restrictions" if they arrive late.

## Build and test

Requirements: JDK 17 or newer (21 is used here), the Android SDK, and a `local.properties` file with
`sdk.dir=...` (not committed). The Gradle wrapper downloads Gradle 9.9.

```
.\gradlew.bat :app:assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat :app:testDebugUnitTest    # unit tests
```

Toolchain: AGP 9.5, Gradle 9.9, Kotlin 2.4, Compose BOM 2026.11, Room 2.8, CameraX 1.7, Health Connect
client 1.1, ML Kit pose-detection 18.0.0-beta5.

The unit tests cover the pure logic: rep counters and the overlay transform, GPS maths, calories and
the food table, plan adaptation, the weekly report, reminder scheduling, the backup format, the
Gemini request/response handling, and the stick-figure poses. `FigureRenderTest` also writes every
pose to `app/build/figures/sheet.png` for a visual check, and `NoEmojiTest` fails the build if an emoji
appears in code or strings (the UI uses Material icons from `ui/AppIcons.kt`).

**What the tests do not cover:** the camera, GPS, Health Connect, notifications and the Compose UI
itself run on a device and were not exercised by automated tests. Treat the thresholds for camera rep
counting (`pose/TrackerSpec.kt`) as starting values to tune on real use.

## Install on a phone over USB

Enable Developer options, then USB debugging (on Xiaomi also "Install via USB" and set the USB mode to
"File transfer"), accept the prompt on the phone, check `adb devices`, then:

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Releases and in-app updates

Settings, "Check for updates" reads a small `version.json` over HTTPS. This project publishes it as an
asset of each GitHub release, at
`https://github.com/goropai/sport-assistant/releases/latest/download/version.json`:

```json
{"versionCode": 18, "versionName": "0.14.1",
 "apkUrl": "https://github.com/goropai/sport-assistant/releases/latest/download/sport-assistant.apk",
 "sha256": "<hex>", "notes": "What's new"}
```

The app downloads the APK, verifies its SHA-256, and hands it to the system installer.

To publish a release: raise `versionCode` and `versionName` in `app/build.gradle.kts`, commit, then

```
.\tools\release.ps1 -Notes "What's new"
```

The script builds the APK, writes `version.json` with the hash, and creates the GitHub release (needs
`gh auth login` once). The update URL is baked in through `updateUrl` in `gradle.properties`; point it
at your own host if you fork the project.

Notes:
- Android installs an update only if it is signed with the same key as the installed app, and the
  release builds are signed with the local **debug keystore**. Keep a copy of
  `%USERPROFILE%\.android\debug.keystore`; without it future versions cannot be installed over the
  current one.
- GitHub can keep serving the previous release as "latest" for a few minutes after publishing, so a
  check made right away may say you are up to date.

## Project layout

```
app/src/main/java/com/sportassistant/
  ai/         Gemini client, food and posture/skin prompts and parsers
  backup/     JSON backup format
  data/       Room database, profile store, exercises, plan adaptation, food table and history,
              meals, weekly report, calorie maths
  figure/     stick-figure engine and the exercise poses
  health/     Health Connect reader
  pose/       rep counters, camera pose analysis, overlay transform
  reminders/  scheduling rules and the WorkManager worker
  track/      GPS recording service and walk maths
  update/     self-updater
  ui/         Compose screens and components
tools/release.ps1   build and publish a release
```

## Known limitations

- **No video analysis** and no muscle assessment from video; the posture and skin check works on
  single photos and only describes what is visible.
- Calories from photos are estimates (roughly 20-30% off); always check the numbers before saving.
- Nose-touch counting depends on the touchscreen registering a nose, which screen protectors can
  prevent. The camera counter is the fallback.
- The GPS route has no map background.
- Internet access (update check, Gemini) is currently granted through a transitive dependency's
  manifest rather than declared by the app itself.

## Privacy and safety

Data stays on the device. The only things that leave it are the photos and texts you explicitly send
to Gemini with your own key, and the update check to GitHub. The app gives general wellness
information, not medical advice; if something worries you, or you have a chronic condition, see a doctor.
