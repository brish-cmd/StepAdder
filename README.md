# StepAdder

Adds steps to Health Connect in increments, so Google Health picks them up.
Built for a Pixel 9 Pro XL (Android 14+, where Health Connect is part of the OS).

## Privacy
- **One permission:** Health Connect "Write steps". It can't read any health data.
- **No internet permission** (explicitly stripped in the manifest), no account, no login, no analytics.
- Stores nothing on disk. Backup and device transfer are disabled.

## How steps are recorded
Each write is split into one-minute records (about 95–118 steps/min, a normal walking pace)
ending at the time you choose. Every record uses:
- `recordingMethod = RECORDING_METHOD_AUTOMATICALLY_RECORDED` (not manual entry)
- `device = Device(TYPE_PHONE, <your phone's manufacturer>, <model>)`

The data source will still show as **StepAdder**. Health Connect sets which app wrote a record,
and no app can change that.

## Build & install
1. Open the `StepAdder` folder in Android Studio (Ladybug or newer). Let Gradle sync.
2. On the phone, turn on Developer options → USB debugging and plug it in.
3. Press **Run**. Or build a release APK with `./gradlew assembleRelease`
   (output: `app/build/outputs/apk/release/app-release.apk`; it's signed with your local debug key).

## First run
1. Tap **Allow writing steps** → turn on **Steps** → Allow.
2. Tap +100 / +500 / +1k / +5k (or type an amount), choose when the walk ended, and tap **Write**.
3. **Undo last write** removes exactly what was just added (until you close the app).

## Getting the steps into Google Health's total
Health Connect doesn't double-count overlapping sources. If StepAdder's time window overlaps
steps your phone already counted, the higher-priority app wins for that period. To make added steps count:
- **Settings → Security & privacy → Privacy controls → Health Connect → Data and access → Activity → Steps → Data sources and priority** and move StepAdder above the phone/Pixel source, **or**
- pick an end time for a period when your phone wasn't counting (e.g. it was on a desk).

Also check that Google Health is allowed to **read Steps** in Health Connect (Google Health → Connections → Health Connect).
