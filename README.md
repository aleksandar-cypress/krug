# Krug

Android aplikacija za deljenje lokacije sa porodicom i prijateljima. Firebase
backend, Mapbox za mapu, Compose UI, srpski + engleski.

## Status

Feature-complete, priprema za Play Store internal beta. Vidi
**[STATUS.md](STATUS.md)** za detaljan rolling log po sesijama.

## Stack

- Kotlin 2.0, Jetpack Compose, Hilt, Navigation Compose
- Firebase: Auth (Google), Firestore, Realtime Database, FCM, Crashlytics
- Mapbox Maps SDK 11.x
- Min SDK 26 (Android 8.0), Target 36

## Prvi setup (jednom)

### 1. Firebase Console

Pun korak-po-korak je u **[`docs/firebase-setup.md`](docs/firebase-setup.md)**.

Kratko: napraviš Firebase projekat, registruješ **dva** Android app-a
(`org.krug.app.debug` i `org.krug.app`), dodaš debug SHA-1, uključiš Google
sign-in + Firestore + Realtime Database, smestiš `google-services.json` u
`app/`.

### 2. Mapbox tokeni

U `~/.gradle/gradle.properties` dodaj:

```properties
MAPBOX_DOWNLOADS_TOKEN=sk.********   # secret, za maven download
MAPBOX_PUBLIC_TOKEN=pk.********      # public, ide u manifest
```

Tokene generišeš na https://account.mapbox.com/access-tokens/

### 3. Release signing (opciono, za AAB)

U `local.properties` (gitignored) dodaj:

```properties
KRUG_KEYSTORE_PATH=release-keystore.jks
KRUG_KEYSTORE_PASSWORD=...
KRUG_KEY_ALIAS=krug-release
KRUG_KEY_PASSWORD=...
```

Bez ovih, `:app:bundleRelease` gracefully fallback-uje na unsigned AAB.

### 4. Build

```bash
./gradlew assembleDebug
./gradlew installDebug

# release AAB za Play Store
./gradlew :app:bundleRelease
```

## Folder struktura

```
app/src/main/java/org/krug/app/
├── KrugApplication.kt
├── MainActivity.kt
├── navigation/                 # typed safe routes + NavHost
├── ui/theme/                   # Color / Type / Theme
├── core/
│   ├── auth/                   # Firebase Auth wrappers
│   ├── circle/                 # Circle model + repository
│   ├── location/               # FGS + location updates
│   ├── permissions/            # runtime permission flow
│   ├── prefs/                  # DataStore preferences
│   ├── sos/                    # SOS trigger + notifier
│   ├── user/                   # user model + repository
│   └── util/                   # Time / Geo / formatters (unit-tested)
└── feature/
    ├── splash/
    ├── auth/
    ├── onboarding/
    ├── map/                    # start destination, map + member detail
    ├── circle/                 # list / detail / create / join
    └── settings/
```

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

Pokriva pure formatter-e (`TimeBucket`, `DistanceBucket`,
`StringFormat`, `DeviceNames`). UI testovi nisu trenutno na agendi.

## Roadmap

- [x] Faza 0: skeleton, navigation, theme
- [x] Faza 1: Firebase Auth (Google)
- [x] Faza 2: Mapbox MapView + sopstvena lokacija
- [x] Faza 3: Circles (create, invite, join)
- [x] Faza 4: Live location sharing (FGS + RTDB)
- [x] Faza 5: Privacy + battery modes
- [x] Faza 6: SOS + push notifications
- [ ] Faza 7: Play Store internal beta (u toku)
- [ ] Faza 8: Public launch + premium tier (history 30d, places, SOS push)

## Privacy

Aplikacija koristi background lokaciju za live sharing u krugovima. Politika
privatnosti: [docs/privacy.html](docs/privacy.html). Play Store location
declaration ide u Console pri uploadu.
