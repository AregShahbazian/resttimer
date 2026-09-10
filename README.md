# Rest Timer

A single-screen countdown for rests between sets. Pick a preset (30 s – 3 min)
or nudge the length in 15-second steps, tap **Start**, and get a buzz and a
beep when it hits zero. Nothing else: no accounts, no history, no ads, no
network.

Kotlin + Jetpack Compose, one `MainActivity.kt`, `VIBRATE` is the only
permission.

## Build

```
./gradlew assembleDebug        # sideloadable debug APK
./gradlew assembleRelease      # signed release APK
```

`local.properties` needs `sdk.dir=<path to your Android SDK>` (Android Studio
writes it for you).

Release signing reads `key.properties` (gitignored, along with `*.jks` /
`*.keystore` — see the template `key.properties.example`). Without it, release
builds fall back to debug signing so a fresh clone still builds.

## Design notes

- **Deadline-based countdown.** The timer stores an `elapsedRealtime()`
  deadline and recomputes the remainder each tick, so `delay()` overshoot never
  accumulates into drift.
- **Screen stays on only while counting.** `FLAG_KEEP_SCREEN_ON` is set for the
  duration of a running set and cleared otherwise.
- **State survives rotation** via `rememberSaveable`; the last-used length is
  remembered in `SharedPreferences` across launches.
- **Alert without a media file.** `ToneGenerator` beeps and a short
  `VibrationEffect` waveform do the job — no bundled audio, no notification
  channel.

## License

MIT — see [LICENSE](LICENSE).
