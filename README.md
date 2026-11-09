# Refract

Glass-style home screen widgets for Android, built natively with `AppWidgetProvider` and
`RemoteViews`. No background services, no accounts, no ads.

## Widgets

| Group | Widgets |
| --- | --- |
| Clocks | Digital Clock, Mini Clock, Analog Clock, Date |
| Weather | Weather, Weather Card, Forecast (city picker, °C / °F) |
| Calendar | Week, Month, Calendar (week / month switch), Countdown |
| Controls | Controls (Wi-Fi panel, Bluetooth pop-up, sound, 3-step brightness, camera), Focus, Dock, Search, Music Player, Music Card, Quick Panel, Scratchpad (a plain white text pad; save the note as a .txt file), Battery, System Monitor (nine looks: Gauges, Segments, Trace, Horizon, Heat log, Thermal, Per-core bars, Terminal, System info), Files (dark, your own pinned folders, a Recent view, search, needs file access) |

Open the app to pick a glass style (Frosted, Smoked, Clear), a preview backdrop and the
"today" accent colour, then add widgets straight from the library.

## Battery

* Clocks and dates use system `TextClock` / `AnalogClock`, so Android draws them with no
  app wake-ups.
* Calendar and Countdown refresh once, at midnight.
* System Monitor refreshes every 5 s while the screen is on and does nothing with the screen off. Weather and Battery share one 15-minute background job, and it is cancelled when the last
  such widget is removed.
* Files wakes only when Android reports a change in its file index (a finished download, a
  deleted file) and reloads just the widgets whose folder changed. There is no polling, and the
  watcher is cancelled when the last Files widget is removed.
* The Bluetooth pop-up listens for changes only while it is on screen.
* `updatePeriodMillis` is 0 everywhere.

## Build

Open in Android Studio, or `./gradlew assembleDebug`. Every push to `main` is built by
GitHub Actions and published as a release with the APK. Builds are signed with a fixed
debug key, so each one installs over the previous.

Requires Android 8.0 (API 26) or newer.

## Credits and licences

Refract is released under the MIT licence (see [LICENSE](LICENSE)).

* Icons: some are [Material Icons](https://github.com/google/material-design-icons) by Google, Apache-2.0.
* Weather and city search: [Open-Meteo.com](https://open-meteo.com/), CC BY 4.0. The free API is for non-commercial use.
* Libraries: AndroidX, Material Components, WorkManager and Kotlin, all Apache-2.0.
* Full details in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Not affiliated with Google, Apple or any other company mentioned here.
