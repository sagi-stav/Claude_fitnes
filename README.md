# One More Plate

A personal Android workout app. You build workouts from exercises, log each set, tell the app
how the set felt, and it decides when to add weight, hold or back off.

## Modules

- `engine/` – pure Kotlin progression engine. It keeps an estimate of your strength per exercise
  (as an estimated one-rep max), updates it from every set, separates long-term strength from the
  form of the day, and prescribes a load that exists on the equipment. `Simulator.kt` in the tests
  is a synthetic lifter used to check the engine over months of training.
- `app/` – Jetpack Compose app. All data is stored on the phone in one JSON file; nothing is sent
  anywhere.
- `tools/build_exercises.py` – rebuilds the bundled exercise catalogue and photos from
  [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (public domain).

## Build

Needs JDK 17+ and the Android SDK (platform 35).

    ./gradlew :engine:test :app:testDebugUnitTest   # engine scenarios + app flow
    ./gradlew :app:assembleDebug                    # app/build/outputs/apk/debug/app-debug.apk

Add `-Proborazzi.test.record=true` to the test command to render every screen to
`app/build/shots/`.
