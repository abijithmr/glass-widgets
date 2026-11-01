# Glass Widgets

Glass-style home screen widgets for Android, built natively with `AppWidgetProvider` and
`RemoteViews`. No background services, no accounts, no ads.

## Widgets

| Group | Widgets |
| --- | --- |
| Clocks | Digital Clock, Mini Clock, Analog Clock, Date |
| Weather | Weather, Weather Card (city picker, °C / °F) |
| Calendar | Week, Month, Countdown |
| Controls | Controls (Wi-Fi panel, Bluetooth pop-up, sound, 3-step brightness, camera), Focus, Dock, Search, Music Player, Music Card, Control Centre, Battery, System Monitor |

Open the app to pick a glass style (Frosted, Smoked, Clear), a preview backdrop and the
"today" accent colour, then add widgets straight from the library.

## Battery

* Clocks and dates use system `TextClock` / `AnalogClock`, so Android draws them with no
  app wake-ups.
* Calendar and Countdown refresh once, at midnight.
* Weather and Battery share one 15-minute background job, and it is cancelled when the last
  such widget is removed.
* The Bluetooth pop-up listens for changes only while it is on screen.
* `updatePeriodMillis` is 0 everywhere.

## Build

Open in Android Studio, or `./gradlew assembleDebug`. Every push to `main` is built by
GitHub Actions and published as a release with the APK. Builds are signed with a fixed
debug key, so each one installs over the previous.

Requires Android 8.0 (API 26) or newer.
