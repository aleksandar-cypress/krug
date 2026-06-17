# Krug — Family Circle

Android aplikacija za deljenje lokacije sa porodicom i prijateljima.
Life360-stil UX, Firebase free tier backend.

## Status

MVP skeleton: navigacija + theme + placeholder screens. Compile-ready posle setup-a ispod.

## Stack

- Kotlin 2.0, Jetpack Compose, Hilt, Navigation Compose
- Firebase: Auth, Firestore, Realtime Database, FCM
- Mapbox Maps SDK 11.x
- Min SDK 26 (Android 8.0), Target 36

## Prvi setup (jednom)

### 1. Gradle wrapper

```bash
cd ~/Desktop/sajts/krug
gradle wrapper --gradle-version 8.10.2
```

(potreban `gradle` instaliran preko `brew install gradle` ili Homebrew/SDKMAN; alternativno otvori projekat u Android Studio i pusti ga da generiše wrapper).

### 2. Firebase Console

Pun korak-po-korak je u **[`docs/firebase-setup.md`](docs/firebase-setup.md)**.

Kratko: napraviš Firebase projekat sa privatnim Gmail-om, registruješ **dva**
Android app-a (`org.krug.app.debug` i `org.krug.app`), dodaš debug SHA-1, uključiš
Google sign-in + Firestore + Realtime Database, smestiš `google-services.json` u
`app/`.

### 3. Mapbox tokeni

U `~/.gradle/gradle.properties` dodaj:

```properties
MAPBOX_DOWNLOADS_TOKEN=sk.********   # secret, za maven download
MAPBOX_PUBLIC_TOKEN=pk.********      # public, ide u manifest
```

Tokene generišeš na https://account.mapbox.com/access-tokens/

### 4. Build

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Folder struktura

```
app/src/main/java/org/krug/app/
├── KrugApplication.kt
├── MainActivity.kt
├── navigation/
│   ├── Routes.kt              # typed safe routes
│   └── KrugNavHost.kt
├── ui/theme/                  # Color / Type / Theme
└── feature/
    ├── splash/
    ├── auth/
    ├── onboarding/
    ├── map/                   # start destination
    ├── circle/                # TODO
    └── settings/              # TODO
```

## Roadmap (kratko)

- [x] Faza 0: skeleton, navigation, theme
- [ ] Faza 1: Firebase Auth (Google + Email)
- [ ] Faza 2: Mapbox MapView + sopstvena lokacija
- [ ] Faza 3: Circles (create, invite, join)
- [ ] Faza 4: Live location sharing (FGS + RTDB)
- [ ] Faza 5: Privacy + battery modes
- [ ] Faza 6: Places / geofencing
- [ ] Faza 7: Beta + Play Store

## Privacy

Aplikacija koristi background lokaciju. Play Store traži pisanu justifikaciju u
Console-u — videti `docs/play-store-location-declaration.md` (TODO) pre objave.
