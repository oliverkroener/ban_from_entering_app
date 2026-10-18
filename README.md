# Ban From Entering

Native Android and iOS apps for keeping a worldwide list of places you are **banned from entering**
(store bans, stadium bans, restraining-order zones, house bans …). They use GPS in the background
to **warn you before you get too close**.

| | Android | iOS |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 | Swift, SwiftUI |
| Storage | Room (SQLite) | SwiftData |
| Location | Fused Location Provider in a foreground service | CoreLocation background updates + `CLMonitor` + significant-change |
| Map | Google Maps (maps-compose) | MapKit |
| Address search | Platform `Geocoder` | `MKLocalSearch` |
| Min OS | Android 8.0 (API 26) | iOS 17 |

All data stays on the device. There is no server and no account.

> [!WARNING]
> **This app helps you, but you are still responsible.** GPS can be off by tens to hundreds of meters.
> Phones can delay or stop background apps (battery savers, low-power mode, force-quit). Permissions can be
> revoked, and a ban's legal boundary is rarely a perfect circle. A missing or late warning is no excuse for
> entering a place you are banned from. Know your bans, and set a generous radius and warning distance.
> The software comes "as is", without any warranty. See [LICENSE](LICENSE).

## Features

- Add, edit and delete bans with: name, address search (worldwide), a center picked on the map or from your
  current location, the banned radius (25 m – 5 km), an optional end date, an active/inactive switch and notes
  (case number, issuing authority …).
- A map showing every banned area (red) and its warning ring (orange).
- Background GPS monitoring that sends a high-priority notification:
  - **Approaching** when you enter the warning zone (radius + your warning distance, default 500 m)
  - **BANNED AREA** when you are inside the radius
- Expired and inactive bans are ignored automatically.

## How the proximity engine works

The same algorithm is implemented on both platforms and covered by the same unit tests:
`android/.../geo/Proximity.kt` and `ios/BanFromEntering/Location/Proximity.swift`.

1. **Distance:** each GPS fix is checked against *every* active ban using the haversine distance, which works worldwide.
   OS geofences are capped (100 on Android, 20 on iOS), so they are not the main mechanism.
2. **Uncertainty works against you:** the reported GPS accuracy (capped at 100 m) is subtracted from
   the distance. A poor fix therefore warns earlier, never later.
3. **Only escalations alert:** outside → warning → inside. Staying in a zone doesn't repeat the alert.
4. **Hysteresis (50 m):** you have to be 50 m past a zone's edge before it counts as left, so GPS jitter
   at the boundary doesn't cause a flood of alerts.
5. **Adaptive sampling** saves battery. The sampling rate depends on the distance to the nearest warning zone:

   | Nearest warning zone | Android interval / min. distance | iOS accuracy / distance filter |
   |---|---|---|
   | < 5 km | 10 s / 10 m, high accuracy | best / 10 m |
   | 5 – 30 km | 30 s / 50 m, balanced | 100 m / 50 m |
   | > 30 km or none | 120 s / 250 m, balanced | 1 km / 250 m |

   These intervals are set so that even at motorway speed you can't reach the next zone between two fixes.

**iOS extras:** the 20 nearest bans are also registered as `CLMonitor` circles, which can relaunch the app
after it has been terminated. Significant-location-change monitoring refreshes this set of 20 as you travel.

## Project layout

```
android/                         Gradle project (open in Android Studio)
  app/src/main/java/com/banfromentering/app/
    data/        Ban entity, Room DAO/database, settings
    geo/         Proximity engine, address search
    tracking/    Foreground location service, notifications, boot receiver, permissions
    ui/          Compose screens: list, edit, map, settings
  app/src/test/  Proximity engine unit tests
ios/
  project.yml                    XcodeGen spec (generates BanFromEntering.xcodeproj)
  BanFromEntering/
    App/         App entry, AppDelegate (background relaunch, notification presentation)
    Models/      SwiftData model, settings
    Location/    Proximity engine, LocationMonitor, notifications, address search
    Views/       SwiftUI screens: list, edit, map, settings
  BanFromEnteringTests/          Proximity engine unit tests
```

## Android: build and run

Requirements: JDK 17+ and the Android SDK (API 36).

```bash
cd android
echo "sdk.dir=$HOME/Android/Sdk" >> local.properties          # path to your SDK
echo "MAPS_API_KEY=your-google-maps-key" >> local.properties   # see below
./gradlew assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # proximity engine tests
./gradlew installDebug             # install on a connected device/emulator
```

**Google Maps key:** in the Google Cloud console, enable *Maps SDK for Android* and create an API key
restricted to package `com.banfromentering.app` and your signing SHA-1. Without a key the map stays blank,
but everything else still works, including address search and GPS warnings.

Permissions the app asks for: precise location, notifications (Android 13+), and optionally
"Allow all the time", which is only needed to resume monitoring automatically after a reboot. For the most
reliable monitoring, exclude the app from battery optimisation. There's a shortcut in Settings.

## iOS: build and run

Requirements: macOS with Xcode 16+ and [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
cd ios
brew install xcodegen
xcodegen generate                  # creates BanFromEntering.xcodeproj and Info.plist
open BanFromEntering.xcodeproj
```

Choose your team under *Signing & Capabilities* (or set `DEVELOPMENT_TEAM` in `project.yml`), then run on a
device. Background location can't be tested meaningfully in the simulator, but *Features → Location*
can simulate movement. When asked, grant location **Always** and allow notifications.

## Ideas for next steps

- Export/import bans (JSON/CSV) and encrypted iCloud / Google Drive backup
- Polygon bans for areas that aren't circles (e.g. a whole stadium district)
- Reminders before a ban expires
- Time-sensitive (iOS) / full-screen-intent (Android) alerts for the "inside" event
- Localisation (German, …)

## License

[MIT](LICENSE) © 2026 Oliver Kroener
