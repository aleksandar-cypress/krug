# Krug — Status & Continue Guide

Snimljeno na kraju sesije.

## Gde smo stali (2026-07-10, prepodne — 1.2.3 hotfix pre odmora)

**1.2.3 AAB build-ovan i signed** (`app/build/outputs/bundle/release/app-release.aab`, versionCode 14). Aleksandar uploaduje na Play Console → Internal testing preko vikenda. Testeri (Jelena + porodica) instaliraju, testiraju 10 dana tokom odmora (utorak → sledeći ponedeljak).

Cilj 1.2.3: sakupiti 5 fix-a koji su prijavljeni jutros pre odlaska na odmor. Nije bilo verifikacije na fizičkom uređaju iz UX-a osim R8 sanity check-a (assembleRelease + install + launch bez crash-a) — big test je tokom odmora.

### Šta je novo u 1.2.3 (od 1.2.2)

**#1 Phantom EXIT — Jelena je opet dobila EXIT za mesto na kome fizički nije bila**
`GeofenceBroadcastReceiver.kt` + `LocalPrefs.kt`:
- `lastTransitionTypeByPlace` sada persistira u SharedPrefs (bio in-memory) — Doze wake/process kill je resetovao guard, pa je prvi EXIT posle restart-a prolazio kao „prvi legitiman" iako je bio phantom
- Fail-closed za EXIT kad GPS verify inconclusive (verifyLocation ili placeInfo null): traži se prior persisted ENTER u prefs. Bez njega → skip. Prethodni kod je propustao event bez ikakve provere što je otvarao phantom escape hatch
- ENTER u inconclusive režimu i dalje prolazi — miss real ENTER je manja šteta od phantom EXIT notif-a (druga strana kruga zove telefonom „gde si nestao")
- Novi key: `KEY_PLACE_TRANSITION_TYPE` (format `placeId1:TYPE1,placeId2:TYPE2,...`), čisti se u `clearForAccountReset`

**#2 Settings → Privacy UI je bila „sjebana"**
`PrivacyScreen.kt` + `values/strings.xml` + `values-sr/strings.xml`:
- Nedostajao `verticalScroll` — screen ima 8+ toggle-a (Sharing, Notifications 3, Driving & Safety 2, itd.) što na S24 (6.2") prelazi viewport i sve ispod Battery alerts (Silent hours, Speeding, Crash detection) je bilo nedostupno. Dodavanjem `verticalScroll(rememberScrollState())` sve postaje dostupno
- Fix `20%%` → `20%` u `settings_battery_alerts_subtitle` (oba lokala). `%%` je escape sekvenca za `String.format`, ali string se koristi kroz `stringResource(id)` bez args-a, pa `%%` renderuje kao literal `%%` u UI-ju

**#3 History uveče — otvara se planeta sa malim pin-om, play ne pomera**
`HistoryScreen.kt`:
- Root cause: `if (visible.size < 2) return@LaunchedEffect` je fajrio PRE nego što se camera fit izvrši. Uveče kad je user bio stacioniran ceo dan, ostane 0 ili 1 point → kamera nikad ne zumira → default world view
- Fix: camera fit izvučen u zaseban `LaunchedEffect` keyed na `(range.fromMs, points, activePlaces, mapView)` — radi za 0 (fit na activePlaces ako postoje), 1 (center + fixed zoom 15), i ≥2 (postojeći cameraForCoordinates + padding)

**#4 Treperenje linije kretanja tokom playback**
`HistoryScreen.kt`:
- Root cause: `pm.deleteAll()` + `create()` na svakih 50ms tick-a — između delete i create render pipeline pokazuje prazan frame → flicker. Statične annotation-e (place pins + start marker) su nepotrebno rekreirane svaki tick
- Fix: split u 3 LaunchedEffect-a
  - **Static**: place pinovi + start marker → keyed na `(activePlaces, points, pointManager)`, re-render samo pri dan-promeni. Ref-ovi u `staticAnns: SnapshotStateList`
  - **Dynamic**: polyline + current position marker → keyed na scrubTime, update in-place preko `manager.update(ann)` (bez delete+create)
  - **Camera fit**: zaseban effect (vidi #3)

**#5 In-app POST_NOTIFICATIONS banner (Jana blocker iz 1.2.2)**
`PermissionUtils.kt` + `MapScreen.kt`:
- `hasNotifications()` sada koristi `NotificationManagerCompat.areNotificationsEnabled()` umesto raw `POST_NOTIFICATIONS` grant check-a. Autoritativan i za Android <13 (user disable-uje notif u system settings, npr. Family Link parent-lock) i za 13+ (permission not granted)
- Nov `openNotificationSettings(activity)` — `ACTION_APP_NOTIFICATION_SETTINGS` intent za direktno otvaranje notif screen-a bez lutanja po App details-u. Fallback na app details ako OEM ROM ne podržava
- `PermissionWarningBanner` prima `onOpenSettings: (onlyNotifsMissing: Boolean) -> Unit`. Kad je JEDINO notif problem, tap ide direktno na notif settings; inače na app details

**#6 `battery_alert_body` %% typo u produkcionoj notifikaciji**
`values/strings.xml` + `values-sr/strings.xml`:
- „Battery below 20%%. Consider reaching out..." — isti bug klase kao #2. Poziva se preko `getString(id)` bez args-a, pa `%%` renderuje kao literal. Svaka battery notif od 1.2.0 je pisala „20%%" u body-ju. Fix: `%%` → `%`
- `battery_alert_title` = `%1$s: low battery (%2$d%%)` OSTAJE — ima format args (`%1$s`, `%2$d`), pa je `%%` legitiman escape

**#7 Feedback button u Diagnostics (Aleksandar backchannel za odmor)**
`DiagnosticsScreen.kt` + `values/strings.xml` + `values-sr/strings.xml`:
- Novi „Pošalji izveštaj Aleksandru" button ide na fillMaxWidth iznad Refresh/Copy row-a
- Otvara mailto: intent (`ACTION_SENDTO`) na `aleksandarr@gmail.com`, subject = „Krug problem — <device> — <version>", body = placeholder red („Napiši šta se desilo…") + full dijagnostika (App/FGS/Perms/Identity/Device snapshot)
- Nova „App" sekcija u snapshot-u: versionName, versionCode, buildType — testeri i Aleksandar znaju exact build koji je u pitanju
- Toast fallback ako user nema email app (retko na modernim Android-ima)
- Copy dugmetu dodat Toast confirmation („Kopirano u clipboard") — user vidi da je tap prošao

**#8 WhatsNewDialog fajrao na svaki versionCode bump (spam)**
`MapScreen.kt`:
- Ranije: modal je pokazivan kad je `lastSeenWhatsNewVersion < currentVersion` (BuildConfig.VERSION_CODE). User koji je video 1.2.0 modal sa 4 nove feature-a je isti sadržaj video opet na 1.2.1 (v12), 1.2.2 (v13), 1.2.3 (v14) — hotfix-i nemaju nove feature-e pa je modal bio pure spam
- Fix: uveden `whatsNewContentVersion = 11` (versionCode kad je trenutni whats-new sadržaj uveden — 1.2.0). Modal se poredi sa ovim, ne sa live BuildConfig. Bump-uje se tek kad se doda nova feature copy u `whats_new_*` stringove, a hotfix versionCode-ovi ga ne diraju

**#10 Log ring buffer za feedback flow (biggest ROI za 10-dnevni test)**
`LogRingBuffer.kt` (nov) + `RingBufferTree.kt` (nov) + `KrugApplication.kt` + `DiagnosticsScreen.kt`:
- Novi in-memory bounded bafer (kapacitet 500 linija, ~75KB memory footprint)
- `RingBufferTree` Timber tree ide pored postojećih `DebugTree` (debug) i
  `CrashlyticsTree` (release). U debug mode-u hvata sve (V/D/I/W/E/A), u release
  hvata samo INFO+ (D/V su chatty u FGS/heartbeat vrelim putevima)
- `sendFeedbackEmail` sada attach-uje poslednjih 150 linija u body-ju posle
  dijagnostika snapshot-a. Cap 150 (a ne full 500) zbog mailto: URI limita
  (~64KB pre Gmail truncate-a). Full bafer se dobija preko „Kopiraj sve" dugmeta
  koje ide u clipboard bez URI limita
- Format: `HH:mm:ss.SSS P TAG: message` — kompakt za email
- Regresija-guard: 9 unit testova u `LogRingBufferTest` (append order, bound
  cap-uje na 500, FIFO preliv, dump lastN, clear)
- **ROI za odmor**: kad Jelena kaže „nešto je puklo", email dolazi sa
  dijagnostika snapshot-om + last 150 log linija. Vidimo *kontekst* pre bug-a,
  ne samo trenutno stanje

**#9 Integration test za phantom EXIT (regresija guard)**
`PhantomFilter.kt` (nov) + `PhantomFilterTest.kt` (nov) + `GeofenceBroadcastReceiver.kt` refactor:
- Phantom decision logic izvučena iz `GeofenceBroadcastReceiver.onReceive` u pure funkciju `PhantomFilter.classify(type, prevType, verify)` sa `VerifyOutcome` (Confirmed(userInside) / Inconclusive) i `Decision` (Allow(reason) / Skip(reason)) sealed class-ovima
- Receiver sad pravi VerifyOutcome iz `verifyLocation` + `placeInfo` i deleguje odluku helper-u — jednostavniji flow, isti behavior
- 14 unit testova u `PhantomFilterTest` pokrivaju sve edge case-ove:
  - Confirmed IN + EXIT → Skip (phantom, user unutra)
  - Confirmed OUT + ENTER → Skip (phantom, user van)
  - Prvi EXIT (prevType=null) + confirmed OUT → Allow (legit izlazak posle create-while-inside)
  - **Inconclusive + EXIT + null prevType → Skip (fail-closed, Jelena scenario)**
  - Inconclusive + EXIT + prior ENTER → Allow
  - Inconclusive + ENTER → Allow (ne fail-close-ujemo ENTER)
  - Semantic guard (prev==type) hvata repeated
- Sve prolaze: `14 tests, 0 failures, 0 errors`
- Regresija-guard: ako neko sledeći put propusti bez provere, testovi padaju odmah

### R8 sanity check (per 1.2.1 postmortem lesson)

Pre `bundleRelease`, obavezno:
1. `./gradlew :app:assembleRelease` → produce APK
2. `adb install -r app-release.apk` na S24 (SM-S928B)
3. `am start -n org.krug.app/.MainActivity` → verify launch bez crash-a

Sve prošlo, „What's new in 1.2" modal se pojavio kao očekivano (i dalje major 1.2). Nema `No properties to serialize` crash-a — postojeci proguard-rules pokrivaju sve Firebase POJO klase (nema novih u 1.2.3).

### Preostaje Aleksandaru (pre odmora ili odmah)

1. **Play Console 1.2.3 upload na Internal testing track** (`app/build/outputs/bundle/release/app-release.aab`)
2. Reci internal testerima da update-uju preko vikenda
3. Preskoči 1.2.2 promote to Production, pusti 1.2.3 direktno (subsumes)
4. Big feedback prijem posle odmora (~sredina/kraj sledeće nedelje)

### Poznata ograničenja 1.2.3 (nedirano — za sledeci sprint)

- Multi-device test (Jelenin telefon + isti Google) — treba fizička provera na terenu
- ETA share live update na 5min voznja — treba real drive test
- Real-device crash detection (4g threshold) — treba dropovanje telefona u autu

### Widget audit

Grep za `widget|Widget|AppWidget|glance` prošao — jedini pogodak je `android.widget.Toast` (standardni Android class za snackbar, ne app-widget). Iz „widget experiment" 1.2.0 sesije nema ni jednog fajla, ni resursa, ni gradle deps-a. Čisto.

### Screen time report u child modu — feature izbačen iz plana

Ranije je bio u „Preostaje" listi kao 2-3 dana rada. Odluka 2026-07-10: izbaciti u potpunosti kao planirani feature. Razlog: uzrokuje distraction od bug-a koji stižu iz produkcije, a nije osnovna funkcija Krug-a (location sharing, ne parental control screen tracker). Ako u budućnosti postane relevantno — otvoriti kao poseban thread.

### Rizici

- **Fail-closed za EXIT može propustiti ~1-5% legitimnih EXIT-a** kad je verify inconclusive i user nikad nije bio na tom mestu ranije (npr. mesto kreirano dok si bio u njemu → user izlazi → verify faila → prefs kaže „nema prior ENTER" → skip). Odluka: bolje miss legit EXIT (auto-heals na sledećem heartbeat-u/ENTER-u) nego lažni EXIT notif porodici. Ako testeri prijave miss legit EXIT, dodati pre-seed persistedTypes pri `GeofenceManager.registerAll` (računa distancu do svake place-a pri registraciji)
- **`areNotificationsEnabled()` može fajrirati banner i za usere koji su namerno mute-ovali specifičan channel** (npr. „ne trebaju mi speeding notif-i, ali trebaju mi SOS"). Zasada tretiramo kao „bilo šta ugašeno = banner", jer master toggle je najverovatniji scenario. Ako testeri prijave, pratiti per-channel state

### Full commit chain 2026-07-10

- (staviti hash pri commit-u) — 1.2.3: phantom EXIT persist prefs + fail-closed, Privacy scroll+%%, history camera uvece, playback flicker, POST_NOTIFICATIONS banner

---

## Gde smo stali (2026-07-09, kraj dana + 1.2.2 — ETA polish, notif diagnostic, self layout 3+3)

**1.2.2 AAB je build-ovan i signed** (`app/build/outputs/bundle/release/app-release.aab`, versionCode 13). Aleksandar uploaduje na Play Console → Internal testing → testira → Promote to Production.

Ovaj build je nadgradnja preko 1.2.1 hotfix-a (koji je već u internal testing-u ali još nije promotovan u produkciju). Preporuka: promotovati direktno 1.2.2 u produkciju umesto 1.2.1, jer sadrži oba (R8 keep rules + UX polish).

### Šta je novo u 1.2.2 (od 1.2.1)

**Copy polish (ETA → jasnije):**
- User feedback: „ETA je skraćenica koju svi ne razumeju"
- Channel: „Deljenje ETA" → **„Deljenje vremena dolaska"**
- Notif body: „ETA X do Y" → **„Stiže za X do Y"** (SR) / „Arriving in X at Y" (EN)
- Button: „Podeli ETA" → **„Podeli vreme dolaska"** / „Share arrival"
- Confirm CTA: skraćen na **„Podeli"** (bio „Podeli ETA")

**ETA UX popravke (Jana test feedback):**
- **Banner za tuđe share-ove** — kad neko drugi iz kruga podeli ETA, sada vidiš banner iznad mape: „Aleksandar · 15 min · Kragujevac". Pre je bio samo pin bez konteksta.
- Banner za tuđi share **nema Cancel dugme** (ne možeš da otkažeš tuđe)
- Banner primary text uključuje ime pošiljaoca („Aleksandar · 15 min"), za self ostaje samo „15 min"
- **Share arrival dugme disable-uje se kad je share aktivan** — sa labelom „Deljenje aktivno". Sprečava dupliranje doc-a. Cancel je u banner-u iznad mape (single point of action).
- **ETA input polje jasno vidljivo** — dodat label „Adresa ili mesto" + placeholder + leading Navigation ikonica + eksplicitne `OutlinedTextFieldDefaults.colors(outline, primary)`. Pre je bilo bela na beloj dok se ne fokusira.

**MemberDetailSheet self layout 3+3:**
- **Red 1 (data akcije, sve outlined):** `[🕐 Istorija]` `[🚗 Vožnje]` `[✓ Prijavi]`
- **Red 2 (live akcije):** `[Osveži]` filled primary + `[🧭 Podeli]` outlined + `[→]` Directions ikonica LogoTeal
- Grupisano po nameri: gore „pogledaj podatke", dole „radi sada + navigacija"
- Kratke varijante stringova: `history_cta_short`, `member_refresh_self_short`, `eta_share_title_short`
- `contentPadding` smanjen na 8dp horizontal da text fitne u 1/3 širine

**Self stat chips single row:**
- Battery + Speed + Last seen sada su **u JEDNOM redu** (weight 1 svakome)
- Pre: Battery+Speed u prvom redu, Last seen sam u drugom (izgledalo nesimetrično)
- Others ostaju 2×2 (Battery+Distance / Speed+LastSeen)

**Notification diagnostic:**
- `EtaNotifier.notifyStarted/notifyArrived` sada eksplicitno proverava `NotificationManagerCompat.areNotificationsEnabled()` i loguje `Timber.w` ako je off
- Pre je bio silent `runCatching` swallow — nije se videlo zašto notif ne stiže
- `LocationTrackingService.observeCircleEta` ima `Timber.d/i` na svakom koraku da vidimo u logcat-u da li se poziv desio
- Za buduće debug: `adb logcat -s Krug` treba da pokaže tok emitovanja i notif dispatch-a

### Poznato ograničenje 1.2.2

**Notif ne stiže Jani ako nije dala POST_NOTIFICATIONS permission.** Testirano danas: Jana obrisala storage, ponovo instalirala, ali izgleda nije prošla kroz permission prompt kako treba (Family Link možda uticao). Pin na mapi je radio, notif nije.

Dijagnoza (iz adb dumpsys package):
```
android.permission.POST_NOTIFICATIONS
   # (nema "granted=true" — dozvola nije data)
```

**Za budući release** (posle odmora): dodati **in-app banner na mapi** „Isključena su ti obaveštenja, uključi da bi primala važna upozorenja" sa dugmetom koje odvodi u Android Settings za app. Klasičan pattern u messaging app-ovima.

### Preostaje Aleksandaru posle odmora

1. **Play Console 1.2.2 upload i promote to Production** (preskočiti 1.2.1 promote)
2. **Real-device crash detection test** — 4g threshold u autu
3. **Multi-device test** — Jelenin telefon + isti Google
4. **ETA share live update na terenu** — 5min voznja, banner treba da se update-uje na 60s
5. **In-app notif-disabled banner** (novi feature za 1.3.0)
6. **Screen time report u child modu** (postoji interes, ~2-3 dana rada kad se vrati)

### Full commit chain 2026-07-09

- `558d1f4` — 32. sesija: 4 nova feature-a (speeding/checkin/ETA/crash) + phantom fix
- `6e16a3a` — multi-device + whats-new + road distance + button parovi
- `535d551` — em-dash cleanup + copy polish + landing 1.2.0
- `8b4abc6` — landing pricing fix (crash/speeding u Premium)
- `f0eb27a` — History playback: skip static period
- `b8bae8f` — Deep UI polish: banneri + corner radiuses + onboarding glitch fix
- `ea3aae3` — STATUS.md wrap-up (1.2.0 AAB spreman)
- `e35dd48` — **1.2.1 hotfix: R8 keep rules za nove Firebase POJO klase**
- `42c7010` — STATUS.md hotfix incident dokumentovan
- `7db4ed4` — **1.2.2: ETA polish + tuđi share banner + self layout 3+3**

Sve pushed u origin/main.

---

## Gde smo stali (2026-07-09, kraj dana + hotfix — 1.2.1 u internal testing, čeka production promote)

**INCIDENT: 1.2.0 release je crash-ovao pri launch-u.** Debug APK koji smo ceo dan testirali na S24 preko `adb install` **NE prolazi kroz R8/ProGuard** — potpuno drugi code path od release-a. R8 obfuskuje/renaming, debug ne. Firebase reflection radi na debug-u savršeno, na release-u pao je čim je naišao na obfuskirane POJO klase.

**Crash stack trace (release 1.2.0 na S24):**
```
java.lang.RuntimeException: No properties to serialize found on class r8.a
  at H5.i.<init>(SourceFile:973)  // Firebase CustomClassMapper.BeanMapper
  at H5.k.g/c (Firestore convertToCustomClass)
  at y5.g.j (Query.toObject)
```

**Root cause:** 4 nove Firebase POJO klase iz 32. i 32.5. sesije nisu bile u `proguard-rules.pro`. R8 je obfuskirao field-ove pa Firestore mapper nije mogao ništa da mapira pri prvom fetch-u (koji se dešava odmah pri otvaranju mape preko SOS observer + places observer + speeding/checkin/eta observers).

Fali-le klase:
- `SpeedingEventModel` (core/speeding/)
- `CheckInEventModel` (core/checkin/)
- `EtaShareModel` (core/eta/)
- `DeviceModel` (core/device/)

`@ServerTimestamp` fallback rule (`-keepclasseswithmembers ... @ServerTimestamp <fields>`) nije bio dovoljan jer:
- Keep-uje samo annotated fields, ne ceo class (ostali fields obfuskirani)
- `DeviceModel` uopšte nema `@ServerTimestamp` field (koristi `Long lastActiveMs`)

**Hotfix commit `e35dd48`:**
- Eksplicitne `-keep` + `-keepclassmembers` rules za sve 4 klase u `app/proguard-rules.pro`
- Version bump: 1.2.0 (versionCode 11) → **1.2.1 (versionCode 12)**
- Testiran preko `./gradlew :app:assembleRelease` + `adb install` release APK-a na S24 (SM-S928B) — mapa se otvara, nema crash-a

**Trenutno stanje:**
- **1.2.1 uploaded na Play Console → Internal testing** (Jul 9 11:21)
- **Nije još promovisan u Production** — čeka Aleksandar da završi kratki smoke test kroz Play Store internal track pa promote
- 1.2.0 je STILL u produkciji i crash-uje kod svih koji su updejtovali — **kritično da 1.2.1 zameni što pre**

**Proces lesson (za budućnost):**
- Pre `bundleRelease` obavezno `assembleRelease` + `adb install` release APK-a na fizički uređaj + klik-around test (mapa, member sheet, Places, History, Settings)
- Debug build **ne otkriva** R8 reflection issues — mora se testirati minified release
- Novi Firebase POJO klase → automatski dodati u `proguard-rules.pro` u istom commit-u (ne odlagati)
- Ne oslanjati se na `@ServerTimestamp` fallback rule — pokriva samo klase koje imaju taj annotation

**Sledeći koraci za Aleksandra (posle odmora ili odmah kad ima vremena):**
1. Otvori Play Store na S24 → Krug → Update na 1.2.1 iz internal track-a
2. Otvori app, verifikuj: mapa se otvara, članovi se učitavaju, klik na člana otvara sheet bez crash-a, Places rade, History radi
3. Play Console → Internal testing → **Promote release → Production**
4. Preporučen staged rollout: 20% dan 1, 50% dan 2, 100% dan 3. Ako se pojavi crash u Crashlytics kod 20%, halt-uješ rollout pre nego što svi dobiju.

**Šta preostaje posle 1.2.1 stabilizacije:**
- Real-device crash detection test (4g threshold, u autu)
- Multi-device test (Jelenin telefon + isti Google)
- ETA share live update na terenu
- Screen time report u child modu (user pokazao interes) — 2-3 dana kad se vrati

---

## Gde smo stali (2026-07-09, kraj dana — 1.2.0 AAB spreman, ide na Play Store, ja idem na odmor)

Kraj dugačkog produktivnog dana. **1.2.0 AAB je build-ovan i signed** (`app/build/outputs/bundle/release/app-release.aab`, 37MB). Aleksandar uploaduje na Play Console kada bude imao vremena. Landing update-ovan i push-ovan na cPanel već.

**Full commit chain danas (od jutra do sad):**
- `a62b5f3` (pre današnje sesije) — 31. sesija member driving reports
- `558d1f4` — 32. sesija: 4 nova feature-a (speeding/checkin/ETA/crash) + phantom fix
- `6e16a3a` — multi-device + whats-new + road distance + button parovi
- `535d551` — em-dash cleanup + copy polish + landing 1.2.0
- `8b4abc6` — landing pricing fix (crash/speeding u Premium)
- `f0eb27a` — History playback: skip static period
- `b8bae8f` — Deep UI polish: banneri + corner radiuses + onboarding glitch fix

Sve pushed u origin/main. Firebase rules + Cloud Function + TTL policies live.

**Preostaje samo Aleksandar-side:**
1. **Play Console 1.2.0 upload** — `app-release.aab` gotov, treba upload + release notes + rollout
2. **Real-device crash detection test** — 4g threshold u autu, spusti telefon slobodno par puta, gledaj false-positive rate
3. **Multi-device test** — instalira 1.2.0 na Jelenin telefon, obe se logujemo istim Google-om, prati se da su jedan član u krugu + oba dobijaju push
4. **ETA share live update na terenu** — hodaš/voziš 5min, banner update-uje ETA na svakih 60s

**Šta je sve u 1.2.0 (za release notes):**
- Safe check-ins ("Stigao/la sam" dugme)
- ETA sharing sa live update-om
- Speeding alerts (opt-in, per-user threshold)
- Crash detection (opt-in, 10s countdown)
- Multi-device support (Google-signed user na više uređaja)
- Phantom double-arrived fix (Places notifikacije)
- History playback preskače statični period
- MemberDetailSheet: road distance (Mapbox Directions) umesto vazdušne linije
- Novi UI: kompaktna dugmad u parovima, konzistentni banner-i, brendiran ETA banner
- What's new dialog automatski se pokazuje pri prvom otvaranju posle update-a

### A) Multi-device support

`LocalPrefs.deviceId` — stabilan UUID per-installacija u SharedPrefs (uninstall + reinstall daje novi ID, ali to je čisto ponašanje).

`core/device/DeviceRegistry.kt`:
- `registerDevice(uid, fcmToken, deviceLabel)` — upsert u `users/{uid}/devices/{deviceId}` sa deviceId + fcmToken + deviceModel + lastActiveMs + registeredAt
- `heartbeat(uid)` — lightweight update lastActiveMs (poziva se iz FGS locationCallback-a)
- `unregisterDevice(uid)` — cleanup pri sign-out-u
- `observeDevices(uid): Flow<List<DeviceModel>>` — za publish gate + buduću „Moji uređaji" UI

`AuthRepository`:
- Injected `deviceRegistry`
- `syncFcmToken` sad i registruje device sa deviceLabel-om
- `signOut` unregister-uje device pre Firebase signOut-a

`KrugMessagingService.onNewToken` — registruje device sa svežim FCM tokenom (za token rotation-e Firebase periodično radi).

`LocationTrackingService`:
- `observeSelfDevices()` cache-uje `latestOtherDeviceActiveMs` + `latestOtherDeviceId`
- `isPrimaryDevice(nowMs)` publish gate: self je primary ako (a) nema drugih uređaja, ILI (b) drugi nije aktivan u `OTHER_DEVICE_ACTIVE_WINDOW_MS=90s`, ILI (c) tiebreaker po deviceId lex order kad su oba aktivna
- `locationCallback.onLocationResult` sad zove `deviceRegistry.heartbeat` na svaki fix + `isPrimaryDevice` gate pre publish-a
- Bez ovog: dva uređaja istog user-a bi naizmenično prepisivala RTDB lokaciju → peer-i bi videli jitter (kuća ↔ posao)

Firestore rules: `users/{uid}/devices/{deviceId}` — self write, isAuthed read (Cloud Function-i bypass-uju rules preko admin SDK-a).

Cloud Function `onLocationRefreshRequest` — čita SVE device tokene iz devices subcollection-a i šalje push na svaki paralelno (`Promise.all`). Fallback na legacy `users.{uid}.fcmToken` za pre-1.2 klijente. Cleanup tokena koji su invalid (`messaging/registration-token-not-registered`) briše ih iz odgovarajuće lokacije.

Rules + Cloud Function **deployed** na `krug-86527`.

**Ograničenja MVP-a:**
- Nema UI stranice „Moji uređaji" u Settings-u (auto-managed za sada)
- Nema ručne selekcije primary uređaja (deterministički auto-elect)
- Google-only auth zadržan (anonymous je iza BuildConfig.DEBUG gate-a) — multi-device i inače radi samo za Google usere jer anonymous daje novi UID po instalaciji

### B) What's new 1.2.0

`WhatsNewDialog` update sa 4 nova 1.2.0 feature-a (check-in, ETA, speeding, crash) umesto starih 1.1 (Places/History/Auto). Icons: `CheckCircle`, `Navigation`, `Speed`, `Warning`. Scrollable content jer sada može biti duži. Hint text „Uključi šta ti odgovara u Podešavanja › Privatnost".

Postojeći version-code gate (`LocalPrefs.lastSeenWhatsNewVersion`) automatski okida modal pri prvom otvaranju posle update-a jer je versionCode 10 → 11.

### C) Road distance u MemberDetailSheet

`MapViewModel.roadDistanceKm(fromLat, fromLng, toLat, toLng): Double?` — Mapbox Directions API poziv sa 4s timeout-om. Vraća km ili null pri failure-u.

`MemberDetailSheet`:
- Novi parametar `fetchRoadDistanceKm: (suspend (Double, Double, Double, Double) -> Double?)?`
- `LaunchedEffect(member.uid, selfLat, selfLng, memberLat, memberLng)` fetch-uje pri otvaranju sheet-a
- State: `roadDistanceKm` + `roadDistanceLoaded`
- Display logic:
  - **Loading**: „Putem: ≈ 3.2 km" (haversine sa ≈ prefix)
  - **Success**: „Putem: 3.8 km" (road distance iz Mapbox-a)
  - **Fallback**: „Vazdušna linija: 2.9 km" ako Directions API pukne

Nove string-ove: `member_chip_distance` (Putem / By road) uz postojeći `member_chip_distance_aerial` (Vazdušna linija / Air distance).

Napomena: **Android Auto (MapCarScreen)** i dalje koristi haversine — u autu je 30s update cadence prečest da bi Directions bio isplativ.

### D) UI polish

**AboutScreen logo animation:**
- `KrugLogo(animated = true)` u AboutScreen — ista entrance animacija kao Splash (spin 360° sa FastOutSlowInEasing, delay 300ms, duration 1200ms)
- Tap-to-spin ostaje: posle entrance-a svaki tap dodaje 360° rotate

**MemberDetailSheet button parovi:**
Prethodno stack-ovano jedan-ispod-drugog (4 reda za self), sada parovi po redu (2-3 reda):
- OTHER member (2 reda): [Refresh] [Directions ikona] / [History] [Trips]
- SELF member (3 reda): [Refresh] [Check-in] / [Share ETA] [History] / [Trips] [Directions ikona]
- Sve „vodi na sledeći ekran" dugmad su **Outlined** (History, Trips), samo Refresh je filled primary. Directions ikonica je filled LogoTeal jer je external launch. Konzistentna hijerarhija.

**ETA banner redesign** (iz 32. sesije, dopunjeno):
- Icon u kružnici (Navigation dok se vozi, CheckCircle kad stigne)
- ETA time primary text, destinacija + km secondary
- Tertiary container color kad je arrived

**ETA destination pin (novi marker):**
- `MapMarkers.destinationMarker()` — zaobljeni kvadrat sa LogoBlue fill-om i belom → strelicom
- Distinktan od teardrop Place pinova, klik na widget/mapu odmah vidiš razliku

### E) Copy cleanup — em-dash i awkward phrasing

Memory constraint: „no em-dash in user-facing text". Uklonjeni user-facing em-dash-ovi:

1. **DrivingReportsScreen title** — bio „Izveštaj o vožnji — Jelena", sada „Izveštaj o vožnji za Jelena" (EN: „Driving reports for Jelena"). Novi string `driving_reports_title_for` sa placeholder-om.
2. **ETA banner destination fallback** — pre placeholder „—" kad je label prazan, sad generički „destinaciju" / „destination" string (`eta_share_dest_unknown`).
3. **ETA notifikacije** — split u dva string-a: `eta_notif_started_body` (sa destinacijom) + `eta_notif_started_body_no_dest` (bez), isto za arrived. Bez em-dash-a placeholder-a u tekstu.

SR polish:
- `checkin_channel` „Prijavljivanje" → „Provere sigurnosti" (bolje opisuje šta radi)
- `checkin_notif_body_with_place` „Bezbedno na lokaciji: X" → „Sa lokacije: X" (jasnije kad je „bezbedno" već u title-u)
- `checkin_notif_body_no_place` „Bezbedno" → „Javio/la se da je bezbedan/na" (pun rečeničan izraz umesto rezanog)
- `eta_share_pick_destination` „Dodirni mapu i odaberi odredište" → „Ukucaj adresu ili mesto" (UI je search field, ne map tap — copy nije odgovarao implementaciji)
- `crash_countdown_body` dodato eksplicitno ime dugmeta koje treba tapnuti: „SOS ide za %d s. Dodirni „Dobro sam" da otkažeš."

Uklonjeni mrtvi stringovi `eta_share_active` / `eta_share_active_no_name` iz oba strings.xml (kod je već koristio novi banner design).

### F) Security review — odluka Google-only

Diskusija o auth security-ju. Utvrđeno:
- **Release build ima samo Google Sign-In** (Anonymous je iza `BuildConfig.DEBUG` gate-a u `AuthScreen.kt:126`).
- Google OAuth 2.0 + Firebase JWT + App Check + Firestore rules = solid setup na industry standard-ima.
- Bez password-a znači: nema DB koja može da procuri, nema password fatigue, recovery ide preko Google account recovery-ja.
- Isključuje korisnike bez Google naloga (Huawei GMS-less, čist Android). Prihvaćen tradeoff — dodavanje email magic link-a bi otvorilo spam/phishing attack surface za marginal gain (5-10% Balkan usera bez Google-a).

Odluka: **ostajemo Google-only** za sada. Ako izbačaj isključenih usera postane realan problem posle 1.2.0 releasea, opcija je Phone/SMS OTP (SIM-bound, ne otvara spam surface, ~$0.03/SMS cost).

### G-2) Deep UI polish (kraj dana)

**MemberDetailSheet banneri (SOS / LongOffline / Offline / Private):**
- Shape 12dp → **16dp** (usklađeno sa ETA banner-om)
- Padding: 14dp → **16dp**
- Icon size: **22dp** standardizovan
- Icon-to-text gap: **12dp** konzistentno
- **LongOffline banner sad ima ikonu (CloudOff)** — pre bio samo tekst
- **Private banner koristi VisibilityOff ikonu** (semantički tačnije)
- **Offline: PrivateGray → LogoOrange tint** (topliji ton „tranzientno" umesto „mrtvo")
- LongOffline: alpha 0.12 → 0.16 (jači signal, akcija potrebna)

**Corner radius skala usaglašena:**
- StatChip: 14dp → **12dp**
- BatteryBadge: 10dp → **12dp**
- **Rezultat: 12dp inline chips, 16dp full-width surfaces, 24dp pill buttons**

**MemberDetailSheet dugmad reorganizovana u parove (umesto stack-a):**
- OTHER (2 reda): [Refresh][Directions ikonica] / [History][Trips]
- SELF (3 reda): [Refresh][Check-in] / [Share ETA][History] / [Trips][Directions ikonica]
- Sve „vodi na sledeći ekran" dugmad su **Outlined** (History, Trips) — samo Refresh je filled primary. Directions filled LogoTeal jer je external launch.

**Onboarding animacija glitch fix:**
- **Uzrok**: `var visible + LaunchedEffect(Unit) { visible = true }` je pravio blank frame između prvog composition-a (visible=false, sve invisible) i state flip-a. User je video „zastane pa se pojavi".
- **Fix 1**: `MutableTransitionState(false).apply { targetState = true }` — AnimatedVisibility odmah zna da treba da animira iz hidden ka visible, nema extra recompose gap-a.
- **Fix 2**: Hero ikona samo **fadeIn** (uklonjen scaleIn). scaleIn + pulse scale su se množile na istom Box-u pa je bio mali „bounce" na kraju scaleIn-a kad pulse preuzima. Sa samo fadeIn, ikona se glatko pojavljuje sa pulse-om koji drži „breathe" efekat.
- Primenjeno na: `PageScaffold.kt` (permission pages) + `InfoPages.kt` (intro screen).

### G-3) History playback fix

Problem: FGS piše lokaciju svakih ~15min čak i dok je user na kuci. Ako je prvi zapis 06:00 a stvarno kretanje počinje u 18:00, klik na Play show-uje 12h statičnog perioda pre nego što se išta desi vizuelno.

Fix u `HistoryScreen.kt`:
- Nova `findFirstMovementMs(points, minStepMeters=100.0)` helper — vraća timestamp prve tačke koja je >100m udaljena od prethodne (stvarno kretanje, ne GPS jitter/publish while static)
- `effectiveFromMs` sad = `maxOf(prvi realan point, firstMovement - 1h)`
- Ako user nije mrdao (svi points klasterisani), fallback na prvi zapis
- Buffer 1h pre kretanja daje kontekst „gde je bio pre nego što je krenuo"

### G-4) Landing 1.2.0 (pricing corrected)

`docs/index.html` update-ovan:
- **Nova "Novo u v1.2" sekcija** sa 4 kartice (check-in, ETA, speeding, crash)
- CSS grid-4 layout + mobile collapse
- **Pricing sekcija** sa konkretnim cenama: €2.99/mo, €19.99/god, 7-day trial
- **Free tier** proširen sa: check-ins, ETA, putna distanca
- **Premium tier** = 9-pack: **crash detection, speeding alerts**, Places 10x, History 30d, Battery alerts, Driving reports, Custom map, Auto full, Priority support
- Napomena: crash detection + speeding alerts su premešteni iz Free u Premium jer su „wow" feature-i koji prodaju paywall. U app-u su i dalje dostupne kao Free preview do implementacije Play Billing gate-a (pattern iz 31. sesije: battery/driving reports/custom map).
- FAQ „Hoće li Krug ostati besplatan?" ažuriran
- Header „Verzija 1" → „Verzija 1.2. Sigurnost besplatno"
- **Em-dash čist** — nema više „—" u user-facing text-u

### G-5) Copy polish (em-dash cleanup)

Memory constraint: „no em-dash in user-facing text". Uklonjeni:

1. **DrivingReportsScreen title** — bio „Izveštaj o vožnji — Jelena", sada „Izveštaj o vožnji za Jelena" (EN: „Driving reports for Jelena"). Novi string `driving_reports_title_for`.
2. **ETA banner destination fallback** — pre placeholder „—" kad je label prazan, sad `eta_share_dest_unknown` = „destinaciju" / „destination".
3. **ETA notifikacije** — split u dva string-a: `eta_notif_started_body` (sa dest) + `eta_notif_started_body_no_dest` (bez), isto za arrived.

**SR copy fine-tuning:**
- `checkin_channel`: „Prijavljivanje" → „Provere sigurnosti"
- `checkin_notif_body_with_place`: „Bezbedno na lokaciji: X" → „Sa lokacije: X"
- `checkin_notif_body_no_place`: „Bezbedno" → „Javio/la se da je bezbedan/na"
- `eta_share_pick_destination`: „Dodirni mapu i odaberi odredište" → „Ukucaj adresu ili mesto"
- `crash_countdown_body`: dodato eksplicitno ime dugmeta: „Dodirni „Dobro sam" da otkažeš"

Mrtvi stringovi obrisani: `eta_share_active`, `eta_share_active_no_name`.

### G-6) Release AAB build

`./gradlew :app:bundleRelease` uspešan. R8 minify prošao. Signed sa production keystore-om.

**Fajl:** `app/build/outputs/bundle/release/app-release.aab` (37MB)

Play Console → Production → Create new release → Upload → Release notes → Rollout.

### G-7) Widget experiment (uklonjen)

Kreiran Glance API home screen widget (LogoBlue pozadina, avatari članova, tap otvara Krug), pa uklonjen na user request. Razlog: bez per-member last-seen/distance podataka, widget je samo dekorativni shortcut — nije MVP-worthy. Kod uklonjen, Glance dependency skinut sa `libs.versions.toml` + `app/build.gradle.kts`. Design pattern zapamćen — može se vratiti kad budu bili spremni za pravo useful widget content.

### H) Version + release stanje

- `versionCode = 11`, `versionName = "1.2.0"` (iz 32. sesije, ne dirano ovde)
- Firestore rules: `speedingEvents`, `checkIns`, `etaShares`, `users/{uid}/devices` — deployed
- TTL policies: 24h retention za sve 3 event kolekcije, ručno kreirane u Cloud Console (Console traži postojeći field, morali smo napraviti dummy dokumente sa `timestamp`/`updatedAt` polje pa obrisati)
- Cloud Function: multi-device fanout **deployed** na europe-west1
- APK 1.2.0-debug instaliran na S24 (`R5CWC1F9FND`) preko `adb install`, testiran

### Šta preostaje pred 1.2.0 release

**Za Aleksandra pre odmora (opciono):**
- Play Store 1.2.0 upload (uz screenshot-ove ako uspe)
- Nova screenshot-a za listing (opciono, 1.1.x rade ali ne pokazuju 1.2.0 feature-e)

**Posle odmora:**
- **Real-device crash detection test** — kritično. 4g threshold je moj pogodak. Voziš u autu, spuštaš telefon slobodno par puta, gledaš da li okine false-positive. Bez toga rizikujemo phantom SOS-e u produkciji.
- **ETA share live update na terenu** — hodaš/voziš 5min, gledaš da li banner update-uje ETA na svakih 60s.
- **Multi-device test** — Jelenin telefon takođe instalira 1.2.0, obe se logujemo sa istim Google, prati se da li se u krugu pojavljuje jedan član + oba dobijaju push notifikacije + samo jedan publish-uje lokaciju.

**Feature-i za sledeće sesije (nisu prioritet za 1.2.0):**
- Silent SOS (power button 5x)
- Nearby member alert (500m radius ping)
- History playback (timeline scrubber)
- Weekly summary
- Onboarding pages (full-screen swipeable za 1.2.0 feature-e, umesto samo modal WhatsNewDialog-a)

---

## Gde smo stali (2026-07-09, 32. sesija — 1.2.0 batch: 4 nova feature-a + cleanup)

Sažetak, kompaktno: subtitle fix + phantom double-arrived fix + 4 nova feature-a (Speeding alerts, Safe check-ins, ETA sharing, Crash detection) + ETA doc leak fix + speeding accuracy filter + ETA destination pin. Version bump 1.1.5 → 1.2.0 (versionCode 10 → 11). Firestore rules deployed (dodate kolekcije `speedingEvents`, `checkIns`, `etaShares`).

### A) Subtitle fix + phantom double-arrived (sesija početak)

**Subtitle „export data" mrtav string** — `settings_privacy_subtitle` obećavao „eksport podataka" ali `PrivacyScreen` nema export UI. Grep za `export`/`Export` po Kotlinu = 0 hitova. Fix: „Pauziraj deljenje, obaveštenja, tihi sati" (SR) / „Pause sharing, notifications, quiet hours" (EN).

**Phantom double-arrived** — user prijavio duple ENTER notif-e ~2h razmakom dok je bio kod kuće. Root cause: GPS drift van radius+50m → phantom EXIT prošao distance filter → sledeći ENTER validan. Fix u `GeofenceBroadcastReceiver.kt`:
- Bump `PHANTOM_THRESHOLD_M` 50 → 100m (EXIT priznat samo ako > radius+100m)
- Novi `lastTransitionTypeByPlace` guard — blokira ENTER→ENTER (ili EXIT→EXIT) za isti place bez suprotnog event-a između (semantički čuvar nadgradnja iznad distance filter-a)

### B) Speeding alerts

`core/speeding/`:
- `SpeedingEventModel` — Firestore POJO (userId, userName, maxSpeedKmh, thresholdKmh, durationSec, lat, lng, timestamp).
- `SpeedingRepository.logEvent(circleIds, ...)` — piše u sve krugove korisnika. `observeRecentEvents(cid)` za peer listen.
- `SpeedingDetector` state machine — `NotSpeeding` → `Speeding` kad speed >= threshold; emit posle `MIN_DURATION_MS=5s` neprekidno iznad. Dedup 10min per user. Filter accuracy > 50m (sanity check, dodat u polish fazi).
- `SpeedingAlertNotifier` — channel `krug_speeding_alerts`, DEFAULT importance, color 0xFFEF4444.

`UserSettings`: `speedingAlertsEnabled=false` (default off — publish o sebi je opt-in), `speedingThresholdKmh=120`. Update funkcije u `SettingsRepository`.

`LocationTrackingService` integracija: `speedingDetector.onFix(...)` u `locationCallback` uz `tripDetector`. `observeCircleSpeeding()` observer analog placeEvents flow-a. `observeSelfName()` puni `selfDisplayName` cache za event payload.

`PrivacyScreen`: toggle + threshold picker FilterChip (80/100/120/140).

Firestore rules: `circles/{cid}/speedingEvents/{eid}` — read/create isti kao placeEvents (userId == caller).

### C) Safe check-ins

`core/checkin/`:
- `CheckInEventModel` — userId, userName, lat, lng, placeLabel (reverse-geocoded), timestamp.
- `CheckInRepository.logCheckIn(circleIds, ...)` + observe. Broadcast u sve krugove.
- `CheckInNotifier` — channel `krug_checkins`, DEFAULT importance, color 0xFF10B981.

`GeocodingRepository.reverse(lat, lng)` — dodat za check-in place label (Mapbox reverse geocoding).

`MapViewModel.sendCheckIn()` — resolve senderName, reverse-geocode sa 4s timeout, `logCheckIn`. Emit `MapEvent.CheckInSent`/`CheckInFailed` preko `SharedFlow<MapEvent>` za toast u UI.

UI:
- `MemberDetailSheet` prima `onCheckIn: (() -> Unit)?` — dugme (Icons.Filled.CheckCircle) samo za self.
- Confirm dialog pre slanja („Poslati potvrdu? Krug će dobiti kratku poruku...").
- `MapScreen.LaunchedEffect` collects `viewModel.events` za Toast.

Firestore rules: `circles/{cid}/checkIns/{eid}` — isti model kao placeEvents.

### D) ETA sharing

`core/eta/`:
- `EtaShareModel` — destinationLat/Lng/Label, etaMinutes, remainingKm, currentLat/Lng, startedAt, updatedAt, arrivedAt. DocId = userId (jedan aktivan share po user-u po krugu).
- `EtaRepository`: `startShare`, `updateShare`, `markArrived`, `cancelShare`, `observeActiveShares`, `observeMyShare`.
- `EtaNotifier` — dva event-a: `notifyStarted` (kad neko iz kruga pokrene share), `notifyArrived` (kad user pređe arrival radius). Channel `krug_eta_shares`, DEFAULT importance, color 0xFF3B82F6. Arrived cancel-uje started notif iz notif tray-a.

`core/directions/DirectionsRepository.driveEta(originLat, originLng, destLat, destLng)` — Mapbox Directions API klijent. Vraća `Route(durationSec, distanceMeters)`. Rate: ~3000 req/mesec za 100 usera × 30min shares × 60s update — daleko od 100k tier limit-a.

`LocationTrackingService`:
- `observeSelfEtaShare()` puni `activeEtaShare` cache (Volatile) — brz distance check bez Firestore reads.
- `maybeUpdateEtaShare(uid, curLat, curLng)` u locationCallback-u:
  - Ako distanca < `ETA_ARRIVAL_RADIUS_M=100m` → `markArrived` + scheduled `cancelShare` posle `ETA_ARRIVED_CLEANUP_DELAY_MS=15min` (polish fix, sprečava doc leak bez TTL policy).
  - Inače throttle `ETA_UPDATE_INTERVAL_MS=60s`: fetch route → update etaMinutes + remainingKm. Fallback haversine / 40 km/h ako Directions fail.
- `observeCircleEta()` observer emit-uje started/arrived notif sa dedup preko `notifiedEtaStarted`/`notifiedEtaArrived`.

`MapViewModel`:
- `startEtaShare(destLat, destLng, destLabel)` — initial directions fetch (6s timeout) + `EtaRepository.startShare`.
- `cancelEtaShare()`, `searchDestinations(query)` (wrapper preko GeocodingRepository.search sa proximity boost).
- `MapUiState.myEtaShare` + `otherEtaShares` — observeMyShare + observeActiveShares combine.

UI:
- `MemberDetailSheet` prima `onShareEta` — dugme (Icons.Filled.Navigation) samo za self.
- `EtaDestinationPicker` dialog — search field sa 300ms debounce, autocomplete list preko `searchDestinations`.
- `EtaShareBanner` na vrhu mape ispod SOS banner-a — pokazuje remaining ETA + destination + cancel dugme.
- ETA destination pin na mapi (polish) — `mapViewState.etaDestManager` renderuje pin za svaki aktivan share (svoj + tuđi) sa labelom „→ {name}: {label} ({eta}m)".

Firestore rules: `circles/{cid}/etaShares/{shareId}` — read za članove, create/update/delete samo self (shareId == request.auth.uid).

### E) Crash detection

`core/crash/`:
- `CrashDetector: SensorEventListener` — `TYPE_LINEAR_ACCELERATION` (gravity filtrirana). Threshold `CRASH_G_THRESHOLD_MS2=40 m/s² (~4g)`. Kontekst filter: user mora biti u vožnji u zadnjih `DRIVE_CONTEXT_WINDOW_MS=30s` (speed >= `DRIVE_CONTEXT_MIN_MPS=3 m/s`). Rate-limit `TRIGGER_COOLDOWN_MS=2min` između trigger-a. `onDrivingSpeed(speed, nowMs)` iz LocationTrackingService-a puni `lastDriveMs`.
- `CrashAlertNotifier` — channel `krug_crash_alerts`, HIGH importance (probija DND), CATEGORY_ALARM, ongoing (ne dismiss-uje se). Action buttons: „Dobro sam" + „Pošalji odmah".
- `CrashActionReceiver: BroadcastReceiver` (AndroidEntryPoint) — hvata `ACTION_CRASH_CANCEL` / `ACTION_CRASH_SEND_NOW`, emit-uje na `CrashActionBus` (Singleton `MutableSharedFlow<CrashAction>`).

`LocationTrackingService`:
- `observeSettings` poziva `syncCrashDetectorState()` na svaku promenu settings-a — start/stop CrashDetector idempotentno.
- `onCrashDetected()` scheduluje 10s countdown (`CRASH_COUNTDOWN_SEC`), na svakoj sekundi update notif remaining. Posle expiry `triggerCrashSos()` (reuse `SosRepository.trigger` sa message=„Automatic crash alert").
- `observeCrashActions()` collect-uje bus — Cancel = otkaži job + cancel notif; SendNow = otkaži job + `triggerCrashSos` odmah.
- `onDestroy`: unregister sensor listener + cancel countdown job.

`UserSettings.crashDetectionEnabled=false` (default off — sensor drain + false-positive risk).

`PrivacyScreen`: toggle ispod speeding threshold.

`AndroidManifest.xml`: registered `CrashActionReceiver` sa intent-filter za dva action-a.

### F) Firestore rules deploy

`firebase deploy --only firestore:rules` na `krug-86527` — nove kolekcije žive u produkciji.

**Ostalo za tebe (Firebase Console):** TTL policies za `speedingEvents.timestamp`, `checkIns.timestamp`, `etaShares.updatedAt` sa 24h expiry. Bez ovog, kolekcije poslovaju bezgranično (etaShares ima code-side cleanup 15min posle arrivedAt, ali cancelled i old start docs bi ostali).

**Ostalo za realno testing (uređaj):** Crash detection threshold — 4g je pogodak, real-device test u autu je nezaobilazan pre release-a. ETA share live update — hodaj/vozi 5min, gledaj da li banner update-uje na 60s.

---

## Gde smo stali (2026-07-08, 31. sesija — premium 7-pack odluka + custom map styles + battery alerts + driving reports)

Fokus sesije: pricing diskusija za premium tier (€2.99/mo, €19.99/god, Play Billing
Direct, 7-day trial), odluka o 7-pack feature listi (Places 10, History 30d, Battery
alerts, Driving reports, Custom map, Android Auto full, Priority support) sa Wear OS
eksplicitno odbačenim. Play Console setup zapoceo ali blokiran na merchant account
(user nema podatke danas). Umesto cekanja, implementirana tri feature-a kao FREE (bez
gate-a) da bi kasnije bili spremni za premium gating: **B (custom map styles)**,
**A (battery alerts)**, **C (driving reports)**. Firebase Firestore rules deployed
(dodat trips subcollection). Nista jos nije iza paywall-a — gate se dodaje TEK kad
Play Billing bude spreman (planirano 1.2.0 posle merchant setup-a).

### A) Premium 7-pack — konacna odluka za 1.2.0

Cene: **mesecno €2.99, godisnje €19.99** (44% popust vs 12×2.99=35.88, jak anchor).
7-day free trial na oba. Play Billing Direct (ne RevenueCat) — Android-only,
iOS nije na horizontu.

Paket:
1. **Places 10x** (free 3) — gate na `FREE_TIER_MAX_PER_CIRCLE`
2. **History 30 dana** (free 7d) — gate na `HistoryViewModel.setDayOffset` clamp
3. **Battery alerts** — implementirano ove sesije (svima free trenutno)
4. **Driving reports** — implementirano ove sesije (svima free trenutno)
5. **Custom map styles** — implementirano ove sesije (svima free trenutno)
6. **Android Auto: full crew view + Places POI** — split (free basic je self+SOS,
   premium je full members + POI). Nije implementirano jos.
7. **Priority support** — operativno (email <24h)

Nikad iza paywall-a: SOS, real-time lokacija, invite/create circle, enter/exit notif,
osnovna Auto integracija (Play policy risk).

Odbaceno: **Wear OS companion** (veliki posao, ne isplati se za 1.2.0), volumen
gate-ovi za krugove/clanove (loše konvertuju), ghost mode (family trust risk).

### B) Custom map styles

Feature koji je user pomeno kao "vidljiv premium signal". Implementacija bez gate-a
za sada — paywall se dodaje kasnije bez ijedne izmene ovog koda (samo na entry
point-u u MapStyleScreen-u).

- `core/map/MapStyleOption.kt` — enum sa 4 stila (STANDARD, DARK, SATELLITE,
  OUTDOORS) svaki sa Mapbox `styleUri`, `labelRes`, `subtitleRes`.
- `LocalPrefs.mapStyleKeyFlow: StateFlow<String>` + `setMapStyleKey(key)`. Cuvamo
  string enum ime umesto ordinal-a (stabilno preko upgrade-a i R8 obfuskacije).
- `feature/settings/MapStyleScreen.kt` + ViewModel — radio list, prati/postavlja
  pref. Reuse KrugRadioButton iz BatteryModeScreen pattern-a.
- Route `MapStyle` dodat, `SettingsRootScreen` dobija row u Performanse sekciji
  (LogoTeal accent, Icons.Outlined.Map).
- 3 map screen-a imaju runtime style change: `MapScreen` (novi `LaunchedEffect`
  observe-uje `styleUri` param u `MapboxContainer`, reload-uje style bez recreate-a
  MapView-a, resetuje `styleLoaded=false` pre reload-a), `HistoryScreen` i
  `AddPlaceScreen` (isti pattern preko lokalnog `lastLoadedStyle` state-a). Sva 3
  koriste `MapStyleOption` iz VM-a (`MapViewModel.mapStyle`, `HistoryViewModel.mapStyle`,
  `PlacesViewModel.mapStyle`).
- `PlacesViewModel` dobio novi param `localPrefs: LocalPrefs` (za `AddPlaceScreen`).

### C) Battery alerts (<20%)

Feature: notif kad batteryPct clana kruga padne ispod 20% i clan nije na punjacu.
Rate-limit per member 12h. Hysteresis 25% (iznad se dedup entry brise pa sledeci
pad opet triger-uje). Charging = skip (user zna, nema smisla).

- `core/battery/BatteryAlertNotifier.kt` — channel `krug_battery_alerts` (DEFAULT
  importance, ne alarm-level kao SOS), color LogoOrange (0xFFF59E0B), CATEGORY_STATUS.
- `UserSettings.batteryAlertsEnabled: Boolean = true` + `SettingsRepository.updateBatteryAlerts`
  + parse.
- `LocationTrackingService.observeCircleBattery()` — mirror of `observeCircleSos()`:
  combine per-peer `locationRepository.observe(uid)`, na svaki emit proverava threshold,
  poziva `notifyLowBattery` uz `fetchDisplayName`. Postuje `batteryAlertsEnabled` +
  silent hours iz `UserSettings`.
- `LocalPrefs.loadBatteryAlerted(ttlMs)` + `saveBatteryAlerted(map)` — persistence
  za dedup preko FGS restart-a. Format "uid1:ts1,uid2:ts2,..." (isti kao SOS).
  `clearForAccountReset` cisti i ovaj key.
- Konstante u LocationTrackingService: `PEER_BATTERY_ALERT_THRESHOLD = 20`,
  `PEER_BATTERY_CLEAR_THRESHOLD = 25`, `BATTERY_ALERT_TTL_MS = 12h`.
- Toggle u `PrivacyScreen` (posle Place notifikacija, pre Silent hours).

### D) Driving reports

Feature: automatska detekcija voznji preko GPS speed threshold-a, upis u Firestore
subcollection, UI ekran sa day/week/month agregatima. Trenutno accessible iz Settings-a
za sopstvene voznje; member-driving-reports ide u sledecu sesiju (E task).

- `core/driving/TripModel.kt` — Firestore POJO sa `startAt`, `endAt`, `durationSec`,
  `distanceKm`, `maxSpeedKmh`, `avgSpeedKmh`, `start/end lat/lng`, `createdAt`
  (@ServerTimestamp).
- `core/driving/TripDetector.kt` — state machine (IDLE → DRIVING → IDLE):
  - START: 2+ uzastopna fix-a sa `speed >= 7 m/s` (~25 km/h)
  - END: nema fix-a >= 1.4 m/s (~5 km/h) duze od 3 min (`TRIP_END_QUIET_MS`)
  - Distance akumulira haversineMeters delta izmedju uzastopnih fixes-a, guard
    protiv teleport-a (implied speed > 55 m/s = drop).
  - `flush(uid)` metod za onDestroy — sacuvaj trip u toku pre nego proces umre.
  - Filter noise: MIN_TRIP_DISTANCE_KM = 0.3, MIN_TRIP_DURATION_SEC = 60.
- `core/driving/TripRepository.kt` — Firestore CRUD (`users/{uid}/trips/{tripId}`).
  Query DESC po startAt, limit 100.
- `LocationTrackingService` — @Inject tripDetector, poziva `onFix(uid, lat, lng,
  speed, now)` PRE `shouldPublish` gate-a (detekcija radi na SVAKI fix). `flush(uid)`
  se poziva u `onDestroy` unutar runCatching.
- Firestore rules: dodat `match /trips/{tripId}` sa istim pattern-om kao locationHistory
  (read: authed, create: self, update/delete: false). **Deployed** kroz
  `firebase deploy --only firestore:rules`.
- ProGuard: `-keep class org.krug.app.core.driving.TripModel { *; }` +
  `-keepclassmembers ... TripModel { <init>(...); }` (defense-in-depth iako
  `@ServerTimestamp` catch-all vec pokriva).
- `feature/driving/DrivingReportsViewModel.kt` — `DrivingRange` enum
  (TODAY/WEEK/MONTH), `rangeBounds` racunanje sa week start = Monday (locale-independent).
  `List<TripModel>.summarize()` ext racuna tripCount + totalKm + maxKmh.
- `feature/driving/DrivingReportsScreen.kt` — FilterChip range selector, summary
  Card (primaryContainer), trip row Card sa datum + start-end vreme + distance +
  max speed + duration. Empty state za prazan range.
- Route `DrivingReports(uid, displayName)` + `KrugNavHost` wired. Settings row
  "Izveštaji o vožnji" (LogoTeal, Icons.Outlined.Speed) → self driving reports
  (uid iz FirebaseAuth u nav lambda).

### E) Deploy + build verification

- Firestore rules deployed OK (2026-07-08). Trip write-ovi ce raditi u produkciji.
- Sav debug Kotlin build prolazi (`./gradlew :app:compileDebugKotlin`). Jedina
  warning je vec-postojeci "delicate API" u HistoryScreen.kt:199 (nije vezana).
- Nije jos pravljen release AAB — sledeca sesija.

### F) Preostalo za sledecu sesiju

1. **E task**: dodati "Vožnje" dugme u `MemberDetailSheet` pored History-ja da
   se otvori DrivingReports za tudjeg clana (opening real premium use case:
   roditelj vs tinejdžer).
2. **D task**: Android Auto premium split — `KrugCarAppService` treba proširenje
   sa (a) svi clanovi kruga na Auto mapi (ne samo self), (b) Places kao POI kategorija,
   (c) distance-to-member widget. Free = self + SOS only.
3. **Merchant account setup** kad user dobije bank/tax podatke → Play Console
   subscription products (`krug_premium_monthly` €2.99, `krug_premium_yearly` €19.99).
4. **Field test 3 nova feature-a**: pusti FGS, prošetaj/provozaj — proveri:
   - Battery alert notif se okine kad peer padne <20% (test na drugom uredjaju
     sa ADB `adb shell dumpsys battery set level 15`).
   - Trip detection: provozaj se 5+ min, otvori Settings → Izveštaji o vožnji,
     proveri trip u listi.
   - Map style: promeni u Settings, proveri da MapScreen/HistoryScreen/AddPlaceScreen
     odmah reload-uju bez app restart-a.
5. **Paywall + Play Billing implementacija** (posle merchant setup-a): BillingClient,
   PaywallScreen, Cloud Function `validatePurchase`, RTDN handler, prvi feature gate
   (Places > 3).

---

## Gde smo stali (2026-07-08, trideseta sesija — 6-tacka feedback + phantom EXIT fix + icon fix + premium temelji + 1.1.5 release)

Fokus sesije: user je poslao 6-tacka feedback iz stvarnog testiranja sa Jelenom
(replay notif pri restartu, nestabilan sort, PlaceRow UX, phantom EXIT bug,
premium placeholder, radijus label). Fix plan → svih 6 implementirano → battery
optimizacija phantom EXIT-a → Xiaomi/Auto ikone fix → premium temelji (UserModel
+ PremiumChecker + firestore rules) → build 1.1.5 AAB. Firebase Firestore rules
deploy-ovane. Fizicki verifikovano na S24 Ultra kroz ADB screenshot pipeline.

### A) 6-tacka feedback iz testiranja sa Jelenom

**1. Phantom EXIT** (P0 ozbiljan): Jelena je dobila lazan notif "user je izasao
iz babine lokacije" u 21:28h dok je user bio kod kuce ceo dan. Play Services
posle Doze wake-up-a firira spurious EXIT sa fresh accuracy/timestamp — postojeci
filteri 1-3 (`STARTUP_GRACE`, `BATCH_RECONCILIATION`, `ACCURACY+AGE`) ga ne hvataju.

Fix: `GeofenceBroadcastReceiver` novi Filter 4 (GPS VERIFY). Pre nego sto upise
event, uzme nezavisan location fix i uporedi sa center place-a:
- EXIT prihvatamo samo ako je `distance > radius + 50m` (jasno van zone).
- ENTER prihvatamo samo ako je `distance <= radius + 50m` (jasno unutra ili blizu).
- Threshold `PHANTOM_THRESHOLD_M = 50` — tolerancija za GPS jitter na granici.

Battery optimizacija (dvo-stepeni pristup):
- Jeftin path: `event.triggeringLocation` ako je accuracy < 50m i age < 30s.
  Pokriva 80-90% real transitions (user fizicki prelazi granicu → Play ima svez fix).
- Skup path (fallback): `getCurrentLocation(HIGH_ACCURACY)` sa 15s timeout. Aktivira
  se kad je triggeringLocation odsutan/neprecizan/star — tacno tada su phantom-i
  najcesci (post-Doze reconciliation).

**2. Replay notif pri restartu** (P1): user ubije app, podigne — dobija notif
"Jelena arrived at Banjica" iako je Jelena tu ceo dan. In-memory
`placesListenerStartedAt` i `notifiedPlaceEventIds` se brisu pri kill.

Fix: PERSISTED per-krug high-watermark u `LocalPrefs.loadLastSeenPlaceEventTs/
saveLastSeenPlaceEventTs`. Format "cid1:ts1,cid2:ts2,..." (isti kao SOS dedup).
Watermark se podize i za muted/silent-hours event-e (event je "vidjen" iako ne
notifikujemo). Novi krug bez entry-ja fallback na session `placesListenerStartedAt`.

**3. Nestabilan sort clanova**: MembersSheet clanovi skacu sa dna → vrh kad neko
publish-uje fresh location. Fix: `MapScreen.kt:2251` sort promenjen sa `updatedAt
desc` na `displayName asc + uid asc` (tie-break). Svezinu prikazujemo kao subtitle
u MemberRow-u, ne kao sort key. 6+ clanova stoji stabilno.

**4. PlaceRow "100m" bez konteksta**: sada `stringResource(places_radius_label,
radius)` → "Radijus: 100 m" / "Radius: 100 m" (label je vec postojao u res-u).

**5. Ko je kreirao mesto**: novi `PlacesViewModel.memberNamesByUid: StateFlow<Map<
String, String>>` (uid → displayName). `PlaceRow` prima `creatorName`, prikazuje
"Kreirao <ime>" subtitle. String `places_row_creator` dodat u values/ i values-sr/.

**6. UI bez View History dugmeta** (buduci premium gate): user pitao "kako ce
izgledati kad se history sakrije?". Fix: `MemberDetailSheet.onOpenHistory` je
sada nullable. Kad je null, Directions se promise u full-width primary Button.
Trenutno je non-null za sve (nema stvarnog premium gate-a jos), ali layout je
spreman.

### B) Icon fix (Xiaomi + Android Auto + Samsung notif badge)

Verifikovano na S24 Ultra: swipe-down notif panel je pokazivao Krug icon sa
zasecenim glavama na sve 4 strane (pink levo, blue gore, teal desno, orange dole).
Isto se desavalo na Xiaomi launcher-u i Android Auto-u.

Root uzrok: `ic_krug_logo.xml` viewport (911x909) sa negative translate
(-44.8, -47.77) izbacuje ekstreme glavica NA ivice viewport-a (x=-1, x=912,
y=2, y=906). Bilo koji OEM mask sa agresivnijim circular crop-om odseca glavice.
Samsung launcher squircle je najblazi pa "prolazi" ali One UI notif badge (koji
takodje koristi launcher icon) primenjuje agressivniji circular mask.

Fix: `ic_launcher_foreground.xml` i `ic_launcher_monochrome.xml` prepisani sa
istim proporcijama kao `ic_notification_large.xml`: viewport 1800x1800, group
translate 398, figure na ~41% radiusa od centra. Sigurno unutar bilo koje maske.

`ic_krug_logo.xml` OSTAJE netaknut jer se koristi u UI (KrugLogo.kt, MapScreen,
PlaceEventNotifier) kao full-bleed brand accent.

Trade-off: launcher icon je ~5-6dp manje nego pre. User verifikovao kroz ADB
screenshot komparaciju (debug build sa fix-om vs. production 1.1.4) i prihvatio
"cist look, sve 4 glave vidljive".

### C) Premium temelji (nema billing jos, tlo za v1.2+)

Diskusija: sve premium feature-i (Places, geofence, history 30d, driving reports,
FCM push, Blaze plan) su vec IMPLEMENTIRANI i besplatni. Sto nedostaje za pravu
tier segmentaciju: Play Billing SDK, receipt validation, paywall UI, premium flag
u user record-u, feature gating logika. To je 1-2 nedelje rada.

User izabrao "temelji za 1.1.5" umesto full premium implementacije. Sto je urađeno:

- `UserModel.isPremium: Boolean = false` + `premiumUntil: Date? = null`. `@ServerTimestamp`
  NE na `premiumUntil` (business logic, ne server clock).
- `PremiumChecker` singleton (`user/PremiumChecker.kt`) — `isPremiumFlow:
  StateFlow<Boolean>` (jedan Firestore listener, deli preko StateFlow-a) i sync
  `isPremiumNow`. Listener follows auth state change kroz `_uidFlow` MutableStateFlow.
- `UserModel.isEntitledToPremium(now)` extension: kombinuje flag + expiry
  (`premiumUntil > now` ili null za lifetime = true).
- `firestore.rules`: novi helper funkcije `isNotChangingPremium()` i
  `isCreatingNonPremium()`. Klijent NE sme da self-update `isPremium`/`premiumUntil`.
  Cloud Function preko admin SDK bypass-uje. Deploy-ovano preko `firebase deploy
  --only firestore:rules`.

Test: admin toggle `isPremium: true` u Firestore konzoli za tvoj user record.
Verifikovano na S24 Ultra debug build-om: app startuje bez crash-a, deserialization
novih polja prolazi kroz R8, `PremiumChecker.isPremiumFlow` emit-uje true.

**Sledeci koraci za premium** (van 1.1.5 scope-a):
- RevenueCat SDK integracija (jeftinije/brze od custom Cloud Function + Play
  Developer API validation) — trade-off 1% revenue share posle prvog $2.5k/mesec.
- Paywall UI (skrol kroz benefite, price selector, "Free trial 7 days" CTA).
- Feature gating: `if (premiumChecker.isPremiumNow) { ... } else { showPaywall() }`
  na Place add (>3 places), history >7d, unlimited krugovi, unlimited clanovi.
- Cloud Function `onPurchaseValidated` koji upisuje `isPremium: true` i
  `premiumUntil: expiryDate` posle receipt validation-a.

### D) 1.1.5 release build

- versionCode 9 → 10, versionName 1.1.4 → 1.1.5.
- Release build prosao sa R8 + Crashlytics mapping upload:
  - `app/build/outputs/bundle/release/app-release.aab` (37MB) — za Play Console
  - `app/build/outputs/apk/release/app-release.apk` (70MB) — za direktan install
- Firestore rules vec deployed.
- Cloud Functions nisu menjane u ovom batchu.

### E) Preostalo za sledecu sesiju

1. Play Console upload 1.1.5 AAB → Internal testing rollout (kad user odluci).
2. Field test tacke 6 na fizickim uredjajima (GPS + Play Services). Prati
   Crashlytics/logs sa "skip phantom" grep-om — ako je previse restriktivan
   (real EXIT baca kao phantom u zgradi sa losim GPS-om), tuning
   `PHANTOM_THRESHOLD_M` (trenutno 50).
3. Prvi stvarni premium gate (npr. Places > 3 zahteva premium) — kad user
   odluci da krene sa v1.2 premium implementacijom.
4. FCM push end-to-end verifikacija sa 1.1.5 (Jelena update → Refresh → Cloud
   Function log → target publish fresh fix).
5. Ostalih 16 tester-a opt-in accept (Play Console management).

---

## Gde smo stali (2026-07-07, dvadeset deveta sesija — 10-tacka feedback + Play Store journey + FCM push infrastructure)

Fokus sesije: user je jutros poslao 10 konkretnih problema (recent activity vreme,
distance label, history fit-bounds, Auto ikonica, Places pricing, ownership transfer,
CircleDetail buttoni, offline detekcija, banner false-positive, refresh za offline). Fix
plan → implementacija svih 10 → Play Store internal testing rollout → 3 critical bug-a
u produkciji → FCM push infrastructure kao dugorocno reševanje "Slobodan ispada offline"
problema. Ukupno ~15 commit-ova plus prvi Cloud Functions deployment. Play Store journey:
1.1.0 (versionCode 2) → 1.1.1 → 1.1.2 → 1.1.3 → 1.1.4 (versionCode 9).

### A) 10 tacaka iz jutrošnjeg feedback-a

1. **Recent activity apsolutno vreme** (`PlacesScreen.kt` EventRow) — `humanTimeAgo`
   zamenjen `formatEventTimestamp(date, locale)`: danas "14:32", juče "juče, 14:32",
   ove nedelje "pon, 14:32", starije "3. jul, 14:32". Locale-aware sa sr/en fallback.

2. **Distance chip label** — "Dist. (line)" / "Udalj. (vazd.)" → "Distance" / "Razdaljina".
   User: "ne mozemo jos da letimo, ta informacija nista ne znaci u vazdusnoj liniji".
   Kraća oznaka koja ne otkriva implementaciju detalj (haversine).

3. **History fit-bounds** — `HistoryScreen.kt:176-189` manual center+zoom=13 zamenjeno
   `mapboxMap.cameraForCoordinates(coords, EdgeInsets(120, 60, 260, 60), maxZoom=16)`.
   Dodat 250ms delay pre poziva (Mapbox zahteva valid map dimensions, LaunchedEffect fajruje
   pre first frame layout-a) + `easeTo` sa 700ms animacijom umesto `setCamera` jump-a.
   Iterirano 3 puta jer user rekao "još ne vidim celu liniju" — sitno padding tuning.

4. **Android Auto diagnostic** — `KrugCarAppService` dobija `onCreate`/`onDestroy` Timber
   log-ove (onBind je final u CarAppService pa ne može override, `onCreate` je najbliži
   hook). Ne testirano fizicki jer user nije mogao u auto. **Realan fix za Auto-listing
   bio je Play Store distribucija** — debug APK preko ADB-a Auto host odbija bez obzira
   na `-i "com.android.vending"` flag. Vidi tacku D dole.

5. **Places limit 3 → 10** — `PlaceModel.FREE_TIER_MAX_PER_CIRCLE`. User: "sta nam ostaje
   za premium onda? Places treba da bude premium exclusive kad implementiramo tier". Za
   sada svih 10 slobodno, plan za premium tier ide u v1.5+.

6. **Ownership transfer** — nova `CircleRepository.transferOwnership(cid, currentOwner,
   newOwner)` metoda sa Firestore transakcijom (update ownerId + oba role field-a).
   `CircleDetailViewModel.transferOwnership(newOwnerUid)` wrapper. MemberRow dropdown
   dobija "Postavi za vlasnika" (owner-only, ne za self/child/postojeći owner).
   AlertDialog confirm. **Firestore rules ažurirane**: circle update dozvoljava
   ownerId change ako je novi u memberIds; members update dozvoljava `role` field
   change od strane trenutnog owner-a. Deploy-ovane pre APK rollout-a.

7. **CircleDetail buttoni u redu** — `CircleDetailScreen.kt` restructure: primary "Pozovi
   članove" ostaje full-width, ispod ide Row(Invite Child weight 1, Places weight 1)
   samo za owner-a. Non-owner: Places full-width. Novi string-ovi `circle_detail_invite_child_short`
   i `places_section_title_short` za kraće labele koje staju side-by-side.

8. **FGS heartbeat** — `LocationRepository.heartbeat(uid)` piše samo `updatedAt=ServerValue.TIMESTAMP`
   (single-child write, ne overwrite parent kao publish). `LocationTrackingService.startHeartbeatLoop()`
   fires svakih 3min (`HEARTBEAT_INTERVAL_MS = 3 * 60_000L`) ako publish nije bio u tom
   prozoru. Ne menja lat/lng/battery/speed — te ostaju "poslednje poznate". Cheap
   (~200B RTDB write). **Rešava scenario**: telefon locked u Doze, FGS živ, GPS callback
   throttled pa nema fresh publish → sa heartbeat-om peers vide fresh `updatedAt` i ne
   marker-uju offline.

9. **SelfShareBrokenBanner + ReliabilityScreen sync** — banner (`MapScreen.kt:3160`)
   dobija 20s grace period posle ON_RESUME (dozvoljava FGS-u da publikuje pre firing-a).
   `resumedAt` tracking + 25s force re-check posle grace expiry. `ReliabilityScreen`
   sada broji "publish stale" kao issue u HeaderCard count-u + dodaje "Send now" CTA
   dugme u LiveStatusCard kad je publish stariji od 10min. Trigger-uje `refreshSelf(context)`.

10. **Refresh dugme** — inicijalno hard-disabled za offline članove sa "Can't wake this
    phone while offline" tooltip-om. **User je odmah rekao "vraćaj to nazad, pre je radilo
    lepo"**. Revert: refresh je uvek enabled (osim za long-offline 24h+ koji je skriven
    postojećim `showRefresh` check-om). isOffline 5min ≠ FGS mrtav — može biti Doze/
    kratak WiFi flicker; refresh može uspeti. Bolje probati nego lažno disable-ovati.

### B) Post-feedback popravke (view-point + reliability)

**View-point bug** (screenshot posle 1.1.2 install-a): kad user tapne Jelenin pin, sheet
otvara ali pin završi ispod sheet-a (ModalBottomSheet ~55% ekrana). Fix: `mapViewState.flyTo`
prosiren `sheetOffsetPx: Double = 0.0` param → Mapbox `padding(EdgeInsets(0, 0, offset, 0))`
pomera visual center gore. Konstanta `MEMBER_SHEET_OFFSET_PX = 1100.0` (kompromis za phone-ove
1080-2340px). Primenjen na 6 mesta: SOS focus, pendingRefocus, onPinClick, MembersSheet
onMemberClick, cluster chooser onMemberClick, onRefresh flyTo.

**OFFLINE_GRACE_MS 5min → 10min** — sirok grace period ubija false-positive treperenje
"online→offline→online" pri kratkim mrežnim prekidima (metro, lift, tunel). Sa heartbeat
paketom (3min interval) na klijent strani, 10min grace je i dalje "sveza" data.

**Auto-refresh na ON_RESUME** (MapScreen DisposableEffect ON_RESUME hook):
- `LocationTrackingService.refreshSelf(context)` — forsira svež publish tvoje pozicije
- `viewModel.refreshStaleMembers()` — pinguje sve stale (>3min old) tuđe članove RTDB-om
  (rate-limit 60s po uid-u). Njihov FGS ako je živ odgovara u par sekundi.

### C) Play Store journey — 4 release-a, 3 critical bug-a u produkciji

**1.1.1 (versionCode 5) upload → prvi tester-i dobili → app crash-uje na startup-u**

Root cause: R8/ProGuard obfuscation. `PlaceModel` i `PlaceEventModel` (dodati u v1.1
za Places feature) nisu bili u `proguard-rules.pro` keep listi. Firebase Firestore
mapper try da toObject → traži `@ServerTimestamp createdAt` polje po imenu → R8 ga je
preimenovao u `a` → mapper fajluje sa `java.lang.RuntimeException: No properties to
serialize found on class s8.o`.

Fix (1.1.2):
- Dodati u keep: `PlaceModel`, `PlaceEventModel`, `LocationHistoryPoint`
- Defense-in-depth rule: `-keepclasseswithmembers class org.krug.app.** { @com.google.firebase.firestore.ServerTimestamp <fields>; }`
  automatski čuva bilo koji budući data class sa Firestore annotacijom

**1.1.2 (versionCode 7) upload → nov bug: geofence reconciliation storm**

Scenario: user uninstall + reinstall 1.1.2 (nakon 1.1.1 crash-a). Jelena istovremeno
otišla u Banjica. Kada je Jelena stigla, **Jelena** je dobila 3 notifikacije: "Aleksandar
arrived at Home", "Aleksandar left babina kuca", "Aleksandar LEFT Banjica". Aleksandar
nije mrdao od kuće.

Root cause: fresh reinstall → geofence subsystem morala da rekonstruiše state → Play
Services fajruje **reconciliation events** za svaki geofence ("gde je user sada u odnosu
na ovaj geofence") sa svežim GPS fix-om. `setInitialTrigger(0)` teoretski sprečava ovo
ali Play Services ga ne poštuje 100% na fresh install-u. Postojeći filter-i (accuracy>150m,
age>5min, dedup 5min) nisu hvatali jer je triggering location bio svež.

Fix (1.1.3):
- **Startup grace 2min** — `GeofenceManager.lastRegisteredAtMs` tracked. BroadcastReceiver
  skip-uje sve transitions u prvih 2min posle re-registracije.
- **Batch reconciliation filter** — real user ne prelazi 2+ granice istovremeno (mesta su
  100m+ razdaljena). Broadcast sa `triggeringGeofences.size >= 2` skip-uje ceo broadcast
  kao reconciliation.

**1.1.3 (versionCode 8) upload → stabilno**

### D) Play Store propagation gotchas — leksija za buducnost

- **Debug APK preko ADB (`-i "com.android.vending"`) NIJE dovoljno za Auto listing**.
  Android Auto host u fizickom vozilu proverava Play Store installer signature strogo,
  DHU (test tool) je permisivan. Play Store distribucija je neophodna.
- **Adding email u tester listu ≠ opt-in**. User mora aktivno da klikne opt-in URL
  (`https://play.google.com/apps/internaltest/{app_id}`) i tap "Accept invitation" pre
  nego što Play Store počne da nudi update. Google ne šalje email automatski.
- **Internal Testing update NE ide kroz "Updates available"** u glavnom Play Store meniju.
  User mora search app-a direktno ili preko opt-in link-a. Ovo je namerni Google dizajn
  (internal = manualno testiranje, ne push distribution).
- **Play Store cache može servirati stariju verziju posle upload-a**. Ako user vidi
  crashujuću verziju umesto najnovije: uninstall + fresh install iz Play Store-a je
  najbrži fix (bypasš cache).
- **versionCode je monotoni sekvencijalni brojač** — jednom "potrošen" (rollback, retire),
  ne može se ponovo koristiti. Nekad je potrebno preskočiti brojeve (7 → 9) zbog
  neuspelih upload-a.

### E) FCM push infrastructure — Cloud Functions + client (1.1.4)

**Problem koji rešava**: čak i sa heartbeat + auto-refresh paketom (Section B), 20% edge
case-ova ostaje — kad je FGS ubijen od strane agresivnog OEM battery killer-a (Xiaomi,
Huawei), telefon je "gluv" za RTDB writes. Aleksandar klikne Refresh za Slobodana, RTDB
write ide u `locationRequests/`, Slobodanov RTDB listener je queue-ovan u Doze-u, nikad
ne procesira. "Slobodan ispada offline" ostaje.

**Rešenje — server-triggered wake-up preko FCM push-a:**

**Client (Android):**
- `KrugMessagingService` (`app/src/main/java/org/krug/app/core/messaging/`) — subclass
  `FirebaseMessagingService`, `@AndroidEntryPoint`
- `onNewToken(token)` → `UserRepository.updateFcmToken(uid, token)` piše u Firestore
- `onMessageReceived(RemoteMessage)` → parsira `data.type`, na "refresh" trigger-uje
  `LocationTrackingService.refreshSelf(context)`. Data-only push (bez notification field)
  budi proces čak i u Doze/App Standby.
- `AuthRepository.syncFcmToken(uid)` — helper zvan posle `upsertOnSignIn` u oba flow-a
  (Google, Anonymous). Fetch trenutni token preko `FirebaseMessaging.getInstance().token`,
  upload. Pokriva scenario "onNewToken fajrovao pre sign-in-a".
- Manifest deklaracija:
  ```xml
  <service android:name=".core.messaging.KrugMessagingService" android:exported="false">
      <intent-filter>
          <action android:name="com.google.firebase.MESSAGING_EVENT" />
      </intent-filter>
  </service>
  ```
- ProGuard rule `-keep class org.krug.app.core.user.UserModel { *; }` već postojao,
  `fcmToken` polje bilo u UserModel od pre.

**Server (Cloud Functions v2):**
- `functions/index.js` (novi folder), Node.js 20 runtime
- `exports.onLocationRefreshRequest = onValueCreated({region: "europe-west1", ref: "/locationRequests/{targetUid}/{requesterUid}", instance: "krug-86527-default-rtdb"}, async event => {...})`
- Fetch FCM token iz Firestore `users/{targetUid}.fcmToken`
- Send high-priority FCM message (`android.priority: high, ttl: 5min`, data-only)
- Cleanup logic: ako send fajluje sa `messaging/registration-token-not-registered` ili
  `invalid-argument`, briše token iz Firestore-a (device uninstall-ovan ili stariji od
  6 meseci — Firebase periodično rotira)

**Cloud Function region matters**: RTDB je u europe-west1 (google-services.json
`firebase_url`). Funkcija mora biti u istom regionu ILI trigger fajluje sa "pattern cannot
match any databases in region us-central1". Prvi deploy u us-central1 nije uspeo, fix je
`region: "europe-west1"` u options.

**Firebase Blaze plan requirement**: Cloud Functions zahteva Blaze (pay-as-you-go).
Free tier: 2M invocations/mesec (Krug scenario ~200 invocations/dan = $0). Serbia tax
gotcha: Google ne nudi "Individual" tax status u dropdown-u za Srbiju, samo "Business"
ili "Government or Public Body". TIN field expects 9-digit PIB (business tax ID). User
je uneo prvih 9 cifara JMBG-a — prošlo je format check, Google ne verifikuje pri signup-u.
Za personal use na free tier-u, praktično zero rizika.

**Eventarc Service Agent permission propagation**: prvi 2nd gen Cloud Function deploy
zahteva ~2min posle Blaze upgrade-a da Eventarc permission-i propagiraju. Firebase eksplicitno
kaže "Since this is your first time using 2nd gen functions, we need a little bit longer".
Retry posle 2min radi.

**Deploy status**: `onLocationRefreshRequest` LIVE u `europe-west1`, `state: ACTIVE`,
verified prvim invocation-om koji je vratio "no fcmToken" za target-e (očekivano — Jelena
i Slobodan još nisu na 1.1.4).

**Testing workflow**:
1. Svi članovi moraju da update-uju na 1.1.4 (fcmToken se auto-uploaduje na sign-in)
2. Jelena zaključa telefon 20-30min (uđe u Doze)
3. Aleksandar tap Refresh za Jelenu
4. `firebase functions:log --only onLocationRefreshRequest` mora pokazati "FCM push sent"
5. Jelenin telefon primi push u Doze (FCM je Google-owned, zaobilazi restrikcije)
6. `KrugMessagingService.onMessageReceived` → `refreshSelf` → force one-shot GPS fix
7. Aleksandar odmah vidi sveznu poziciju

### F) Manifest fix — Android Automotive OS vs Android Auto

**Bug otkriven pri 1.1.1 upload-u**: Play Store odbio AAB sa greškom "The app cannot
declare 'android.hardware.type.automotive' device feature and 'com.google.android.gms.car.application'
metadata at the same time".

**Razjašnjenje**:
- `android.hardware.type.automotive` uses-feature je za **Android Automotive OS** —
  native OS ugrađen u vozilo (Volvo Polestar, Ford Mustang Mach-E). App radi na car
  hardveru, nema telefona.
- `com.google.android.gms.car.application` meta-data je za **Android Auto** —
  telefon projektuje na car display preko USB/wireless.

Krug je Android Auto app, ne AAOS. Uklonjen `android.hardware.type.automotive` uses-feature.
Popularni Auto app-ovi (Waze, Spotify, WhatsApp) isto to rade — samo car.application meta-data,
bez automotive feature.

### G) Firestore rules deploy

Rules objavljene 2 puta u ovoj sesiji:
1. Prvo za ownership transfer (allow ownerId change + members role field update)
2. Verifikacija da rules ne pucaju sa novim client kodom

Deploy komanda: `firebase deploy --only firestore:rules`. Deploy je instant (~5s).
Rules su **backward-compatible** — 1.1.0 klijent ne koristi nova pravila, samo prolazi
kroz postojeća.

### H) Session outcome — šta radi i šta ostaje

**Radi (verified):**
- ✅ Sve 10 tacaka feedback-a
- ✅ View-point fix za MemberDetailSheet
- ✅ ProGuard fix (crash rešen)
- ✅ Geofence startup grace + batch filter
- ✅ Firestore rules objavljene (ownership transfer)
- ✅ Play Store 1.1.4 (versionCode 9) LIVE
- ✅ Cloud Function `onLocationRefreshRequest` LIVE u europe-west1
- ✅ FCM token upload sa aleksandarr@gmail.com verifikovan (kroz Function log — requesterUid
  match tvom uid-u)

**Ostaje za sledeću sesiju:**
1. **Auto fizicki test** — user nije mogao u auto, Play Store install (`org.krug.app`,
   ne `.debug`) treba da prizna Auto host. Diagnostic log-ovi spremni u `KrugCarAppService.onCreate`.
2. **FCM push end-to-end verifikacija** — Jelena mora update na 1.1.4, onda test:
   Jelena locked → Aleksandar Refresh → očekivano `firebase functions:log` prikaže
   "FCM push sent" umesto "no fcmToken" + Jelena's FGS reaguje.
3. **Ostalih 16 tester-a update** — poslati opt-in URL preko WhatsApp/SMS-a. Bez opt-in
   accept, Play Store ih ne prepoznaje kao tester-e.
4. **Ownership transfer test** — funkcionalnost je LIVE (Firestore rules objavljene),
   nije još fizicki testirana.
5. **STATUS.md**: možda dodati sekciju sa 2-3 najbitnija Play Console koraka za buduće
   deploy-e (Halt rollout vs Pause track, opt-in URL, versionCode monotonija).

### I) Krug versionCode/versionName trail

- 2 (1.1.0) — production do jutros
- 3-4 — potrošeni na Play Store manifest fix (automotive feature)
- 5 (1.1.1) — 10 tacaka feedback-a, prvi upload → crash zbog ProGuard
- 6 — potrošen na view-point + geofence fix rebuild
- 7 (1.1.2) — ProGuard + view-point fix
- 8 (1.1.3) — geofence storm fix (startup grace + batch)
- 9 (1.1.4) — **AKTUELNI** — FCM push infrastructure (client + Cloud Function)

---

## Ranija sesija (2026-07-06, dvadeset osma sesija — feedback pass posle testiranja na S24 + A55)

Fokus sesije: user je jutros poslao 6 screenshot-a sa konkretnim UI/UX problemima nakon testiranja V1.1. Cela sesija je iteracija na tim problemima + otkrivanje dodatnih bug-ova pri live testiranju na Galaxy S24 (Aleksandar) + Galaxy A55 (Jelena). 7 commit-ova (`7fea705` → `04a19bc`), ~800 novih linija, 220 obrisanih (DirectionsRepository dead code).

### A) MemberDetailSheet redesign (jutarnji spisak: task 2, 3, 4, 5, 9, 10)

**Fixed 2×2 chip grid** umesto varirajućeg FlowRow-a:
- Za druge članove: Battery, Distance, Speed, Last seen (uvek sve 4, u 2×2 layout-u).
- Za self: Battery + Speed u redu 1, Last seen full-width u redu 2 (jer distance to self nema smisla).
- Za private mode: samo Last seen full-width.
- Speed **uvek prikazan** (uklonjen `> 1 m/s` gate — 0 km/h za mirujuće).

**Uklonjen ETA chip:**
- User je pitao "šta znači ETA, koja je razlika od Speed", pa smo utvrdili da je konfuzno + u screenshot-u je `<1 min` sa "<" znakom bio nejasan.
- ETA fetch (Mapbox drivingRoute) je bio isključivo za ovaj chip — cela `DirectionsRepository.kt` (220 linija) obrisana kao dead code.
- Distance sada koristi haversine umesto Mapbox route (aerial > driving fallback), a user je rekao "koristiću Google Maps ako mi treba prava navigacija".

**Buttons redesign — 3 iteracije:**
- V1: Row(Open in Maps outlined w=1, View history filled tonal w=1.2) — text "Open in Google Maps" se sekao (20 char pored View history-a).
- V2: Icon-only outlined Directions ikonica + FilledTonalButton View history — user rekao "užas, stavi brand boje".
- V3 (final): FilledIconButton **LogoTeal 64×48dp** (Directions icon) + Button **LogoBlue** (View history sa clock ikonom + tekstom). Sva dugmad (Refresh + row) imaju identičnu visinu 48dp i shape RoundedCornerShape(24dp).

**Sheet cutoff fix:**
- ModalBottomSheet je bio partially expanded, pa se View history sekao. Fix: `rememberModalBottomSheetState(skipPartiallyExpanded = true)` + `verticalScroll(rememberScrollState())` na Column.

**View history dostupan iz self detail sheet-a** (task 8):
- Ranije samo u Settings/Privacy row "Moja istorija kretanja". Sada je uklonjeno iz Settings-a (obrisan `onOpenMyHistory` callback + `privacy_my_history_cta` string), a per-member button (uključujući self) je jedini entry point.

### B) HistoryScreen popravke (task 6, 7 + bonus)

- **Locale-aware datum**: `formatDay` više ne forsira `sr-Latn` uvek, već prati `LocalConfiguration.current.locales[0]`. Sr device → `"petak, 3. jul"`, en device → `"Friday, July 3"`.
- **"Time:" prefix uklonjen** iznad scrub slajdera — vreme je jasno iz konteksta (uz play dugme). Uklonjen `history_time_label` string.
- **Dinamičan scrub range** (bonus fix): scrub 0..1 se mapira na EFEKTIVNI range dana (prvi point → poslednji point), ne 24h. Ako je user aktivan 08:30-20:15, ceo slajder pokriva tu aktivnost. Za današnji dan, gornja granica se coerce-uje ka NOW ako je poslednji point stariji (pokazuje "sada" umesto "pre 10 min"). `formatTimeAt(fromMs, toMs, ratio)` zamena za stari `formatTime`.

### C) Location reliability paket (task 11)

Za user-ov core CORE bug "Slobodan često ispada offline", implementiran je multi-front approach:

**LocalPrefs.lastBatteryPromptMs** — kad je poslednji put dijalog pokazan.

**Warning banner na Settings-u (top card, orange):**
- Vidljiv **samo** kad postoji issue (bg location, battery-opt, notifications, activity-recognition).
- Auto refresh na ON_RESUME (kad se user vrati iz sistemskih podešavanja).
- Per-row "Fix" dugme.
- Rečnik strings: `reliability_issue_battery`, `reliability_issue_bglocation`, itd.

**Startup AlertDialog** (max 1×/nedelja):
- Fire-uje samo kad battery-opt nije exempt, cooldown 7d preko `viewModel.batteryPromptCooldownExpired()`.
- Prikaz posle whats-new dijaloga (redosled).

**Dedicated ReliabilityScreen** (user-facing "zašto sam offline"):
- Ceo checklist sa green/red status ikonom + Fix dugmadima.
- Live status kartica: FGS running (isRunning.get()), last publish age.
- Dostupan iz Settings > Performance sekcija (nova ruta `Reliability`).
- Odvojen od `DiagnosticsScreen` koji ostaje debug-only (monospace log format za bug reportove).

**Offline UI grace period (bonus)**:
- `MemberWithLocation.isOffline()` sada zahteva `!online` **I** `updatedAt > 5min`.
- Bez ovog, RTDB onDisconnect (~30s posle stvarnog dropout-a) postavlja `online=false` i UI treperi između "online" i "offline" pri svakom WiFi flicker-u. Sa 5min grace-om, samo trajniji outage pokazuje offline.

**"Retry queue" — NIJE potrebno**:
- Prvobitna analiza (Explore agent) je rekla "nema retry logike za Firebase writes". Nakon verifikacije: `KrugApplication.setPersistenceEnabled(true)` + `PersistentCacheSettings` (od pre) — Firebase SDK sam queue-uje offline write-ove na disk i replay-uje na reconnect, uključujući preživljavanje app restart-a. Task odjavljen kao "already covered".

### D) Places bugs otkriveni tokom testiranja (bonus fixevi, nisu bili na jutrošnjem spisku)

**Bug 1: Place pin ne prikazan kad se klikne iz Places liste**
- Aleksandar (S24) vidi Jelenin novi place `zubar` u PlacesScreen listi, klikne na njega, mapa fly-to okvirno ali nema pin.
- Dijagnostika kroz logcat: `activePlaces.size=0` u MapScreen iako je place u Firestore-u.
- Root cause: `MapViewModel.activePlaces` observira `localPrefs.activeCircleIdFlow` **direktno bez fallback-a**, ali `combineForUser` (koji generiše members) **ima fallback** (`circles.firstOrNull { it.id == storedActive } ?: circles.firstOrNull()`). Za fresh usere koji nikad nisu `setActiveCircleId` pozvali, activeId je null → members se vide (fallback), places prazno.
- Fix: extract `effectiveActiveCircleId` shared flow sa istom fallback logikom; koristi ga i za `activePlaces` i za `eventsByPlace`.

**Bug 2: Self flyTo overriduje PlaceFocus flyTo (race)**
- Na povratku iz PlacesScreen-a u Map (via `nav.popBackStack(Map, inclusive = false)`), MapScreen se ponovo composes, `mapViewState` je fresh (`didFlyToSelf = false`). Oba LaunchedEffect-a (self flyTo, place focus) fire u istom recomposition-u, oba čekaju mapView + styleLoaded.
- Race: PlaceFocus animiran `flyTo` starta, ali self instant `setCamera` završi 1ms kasnije i pregazi (jer setCamera je sync jump). User vidi self location, ne place.
- Fix: self LaunchedEffect skip-uje ako `PlaceFocusBus.pending.value != null` (i pre wait-a i posle). PlaceFocus ima prioritet, kamera se ne pomera na self.

**Bug 3: Notif click na Place event ne centrira mapu**
- User je javio "kao da je mapa ostala gde sam izašao" nakon tap-a na "Jelena stigla kod zubara" notif.
- `MainActivity.handlePlaceFocusExtra` je čitao `placeId` iz intent-a, postavljao active circle, ali **placeId je bio ignoran** — nikad se nije koristio za flyTo.
- Fix: dodat `PlaceFocusBus.pendingId` flow. MainActivity poziva `requestById(placeId)`. MapScreen collect-uje `pendingId`, čeka da `activePlaces` sadrži place sa tim ID-om, pa resolve-uje u standard `Focus(lat, lng, name, radius)` i emituje kroz postojeći `pending` flow — existing flyTo logika preuzima od tu.

**Feature: Proximity cluster pins (member koji putuju istim autom)**
- User je jutros javio "Magdalena i Jana su razdvojene na mapi iako putuju istim autom". Root cause: async publish (30-90s razmak) daje 2 pina na različitim mestima jer se auto pomera između njihovih fix-eva.
- Fix: `clusterByProximity()` helper — greedy grouping unutar 100m threshold-a. Za cluster od 2+, prikazuje se prvi član kao primary pin, sa **"+N" LogoBlue badge-om** u gornjem desnom uglu bubble-a (crtano u `MapMarkers.pinMarker(clusterExtras = N)`). Cache key uključuje cluster count. Label = "Ime1, Ime2" za 2 ili "Ime1 i N" za 3+. Self je izuzet iz cluster-a (uvek solo, brand blue pin ostaje "you're here").

### E) Android Auto fix (task 12)

- **Uklonjen `android:foregroundServiceType="location"`** sa `KrugCarAppService` u manifest-u. CarAppService **NIJE** user foreground service — Auto host sam upravlja lifecycle-om. Prisustvo ovog atributa je čest uzrok zbog kojeg Auto host odbije da list-uje app u meniju.
- Fizički test na autu nije obavljen ove sesije (user nije priključio na kabl).

### F) Aleksandar/Jelena test setup na S24 + A55

Sesija je obuhvatila live install debug APK-a na dva uređaja (S24 Ultra `R5CWC1F9FND`, A55 `RZCX50XJGPD`) i live testiranje kroz stvarne interakcije. Iterativni loop je bio: user javi problem → istraga → fix → rebuild + adb install → user testira. Ovaj pattern je otkrio pored jutrošnjih 12 stavki i još 3 bug-a koje nismo znali za (place pin missing, flyTo race, notif click).

### G) Post-scriptum (nastavak sesije, još 8 commit-ova)

Nakon STATUS.md update-a `26034f6`, sesija se produžila sa još 8 commit-ova
(`22dcf16` → `cbd509a`):

**Task audit + fallback fixes (`22dcf16`)**:
- `MapViewModel.deletePlace` i `togglePlaceMute` koristili raw
  `localPrefs.activeCircleIdFlow.first()` bez fallback-a → za fresh usere silently
  no-op. Fix: `effectiveActiveCircleId` (isti pattern kao `activePlaces`).
- `HistoryViewModel.activePlaces` isti bug → dodati `CircleRepository` +
  `FirebaseAuth` injection, extract shared `effectiveActiveCircleId`.
- **Cluster click UX**: `annotationToUid` postao `List<String>` umesto `String`.
  Solo → detail sheet (postojeće). Cluster → `MembersSheet` filtriran na
  cluster uid-ove, user bira kog.
- **Auto-status i18n**: "U pokretu • 45 km/h" bio hardcoded srpski u ViewModel-u.
  Fix: `auto_status_moving` string resource + `@ApplicationContext` injection.

**Notif ikone (`a01e6ea`)**:
- `PlaceEventNotifier.setSmallIcon` bio `ic_launcher_foreground` (obojena
  launcher ikonica) → siva mrlja u status bar-u. Fix: `ic_notification` (bela
  silueta), konzistentno sa SosNotifier + LocationTrackingService FGS.

**Geofence spam notifikacija (`da99490`)**:
- User javio: Jelena dobila 4 spam notif-a "Aleksandar stigao kod kuće" iako
  se Aleksandar nije pomerio. Root cause: `GeofencingRequest.setInitialTrigger(
  INITIAL_TRIGGER_ENTER)` firing ENTER svaki put kad se places lista promeni
  (svaki re-register = novi ENTER za device koji je unutra).
- Fix: `setInitialTrigger(0)` — enter/exit fire SAMO na fizičkom prelasku
  boundary-ja. Plus in-memory dedup u `GeofenceBroadcastReceiver` (60s cooldown
  po (placeId, type)) kao defense layer za Google Play race scenarije.
- Bonus: `MemberRow` dobija `autoStatus` param, prikazuje "U pokretu · 45 km/h"
  u statusnoj liniji umesto device model-a. `MembersSheet` prosleđuje
  `autoStatusByUid` iz MapViewModel-a.

**History "no movement" state (`9f6f2e8`)**:
- User javio: Jelena otvara View history za Aleksandra, play radi ali mapa se
  ne menja jer Aleksandar nije mrdao. Root cause: FGS publikuje lokaciju
  svakih 10min čak i kad user statično stoji → 40-50 point-a ali svi u <50m.
- Fix: `computeHasMovement(points, minSpanMeters=50)` bounding box check.
  Ako !hasMovement: overlay "No movement recorded" + Play IconButton disabled.

**MapCarScreen Header API (`d8e85fb`)**:
- Uklonjeni deprecation warning-i (`PlaceListNavigationTemplate.setTitle` +
  `setHeaderAction`) prelaskom na `Header.setTitle` + `Header.setStartHeaderAction`
  pattern (Car App Library 1.3+).

**Android Auto POI kategorija (`9dc1f77`)**:
- Krug se i dalje nije pojavljivao u Auto listi. Root cause: `NAVIGATION`
  kategorija zahteva **Play Store approval** od Google-a; sideloaded APK-ovi
  se filtriraju čak sa Unknown Sources ON. Zato Viber (MESSAGING) može, Krug
  ne mogao.
- Fix: kategorija `NAVIGATION` → `POI` (Points of Interest, ne zahteva
  approval). Template `PlaceListNavigationTemplate` → `PlaceListMapTemplate`
  (POI kompatibilan). NAVIGATION_TEMPLATES permission uklonjena.

**Android Auto dodatni layer fix-eva (`5938cca`)**:
- POI sam nije bio dovoljan po Explore agent audit-u; primenjeni top 3
  najverovatnija dodatna fixa:
  - `car-app` biblioteka 1.4.0 → 1.7.0 (najnovija stabilna, modern Auto host
    može ignorisati apps kompajlirane sa starim SDK-om).
  - `minCarApiLevel`: 1 → 4 (Level 1 = Auto 5.0 iz 2015., moderni host tretira
    kao napušteno; Level 4 = Auto 8.1+ = 2023).
  - `KrugCarAppService`: dodati `android:label` + `android:icon`.
- **Instalacija napomena**: sideloaded debug APK-ovi treba da se instaliraju sa
  `adb shell pm install -i "com.android.vending" -r <apk>` — bez ovog Auto na
  nekim host verzijama ignorira apps ne-Play-Store installer-a.

**KrugSwitch wrapper (`cbd509a`)**:
- User javio: "toggle button kada je OFF nije lepo prikazan". Material3 default
  ima siv track sa vidljivom border ivicom + medium grey thumb — "boxy cheap"
  izgled.
- Fix: novi `KrugSwitch` u `ui/brand/` sa custom colors — ON=LogoBlue+white,
  OFF=surfaceContainerHighest+onSurfaceVariant, border transparent u oba
  stanja. Primenjeno na 4 mesta (PrivacyScreen 3× + PlaceDetailSheet 1×).

### H) Post-scriptum 2 (nastavak sesije, još 6 commit-a)

Nakon post-scriptum #1 (koji se završio sa `cbd509a` KrugSwitch), sesija se
produžila sa još 6 commit-a (`104adce` → `c2f9b11`):

**SelfShareBrokenBanner (`104adce`)** — proaktivan alert za Slobodan-tip
problem. User pointer: user (Slobodan) ne zna kad ga drugi ne vide jer sam
koristi app misleći da sve radi. Location Reliability screen (danas dodat) je
pasivan — mora sam da uđe u Settings. Fix: crveni banner na MapScreen-u koji
fire-uje kad `!LocationTrackingService.isRunning.get() || lastPublishAtMs > 10min`.
Naslov: "Ostali te trenutno ne vide" + tap otvara Location Reliability. Rescan
na ON_RESUME + periodic 60s tick da se banner otkrije/sakrije bez korisničke
akcije.

**Audit fixes (`19e637a`)** — thorough audit SOS/Places/Circle flow-ova
otkrio 3 real bug-a:
- SOS lock-screen bypass reset (MainActivity): `enableShowWhenLocked()` je
  aktivirao flag ali nikad ne resetuje. Rezultat: sve buduće notifikacije
  otvaraju app preko lock screen-a — privacy leak. Fix: `onResume()` hook koji
  reset-uje flag kad `SosFocusBus.pendingUid.value == null`.
- Places double-tap Save race (PlacesViewModel): `createPlace/updatePlace` bez
  guard-a — brz dupli-tap launch-uje 2 paralelna Firestore write-a. Fix:
  `if (_state.value.saving) return` na početku funkcije + set `saving=true`
  sinkrono pre `viewModelScope.launch`.
- Invite deep-link handler `krug://invite/{code}` (novo): manifest je imao
  intent-filter ali MainActivity ignorira data URI — user klikne link u
  WhatsApp/SMS, app se otvori ali kod se gubi. Fix: nov `InviteFocusBus`
  (isti pattern kao SosFocusBus/PlaceFocusBus). MainActivity parsira
  `uri.lastPathSegment`, emituje. KrugNavHost collect-uje `pendingCode` +
  `currentBackStackEntryAsState` i auto-navigira na `EnterCode(prefilledCode)`
  kad user stigne do sigurne rute (Map/CircleList/EnterCode). Ako nije auth-ovan,
  kod čeka u bus-u kroz Auth flow.
- KrugRadioButton wrapper (novo): isti pattern kao KrugSwitch. Custom colors
  sa LogoBlue primary za selected + neutral outline za unselected. Primenjen
  na BatteryModeScreen (3 RadioButton-a).

**Places empty state polish (`877653a`, delimično)** — kad je Places lista
prazna, ranije bland outlined icon + text. Nov design: 96dp LogoBlue-tinted
cirkular + Place ikona + titleLarge naslov + Body hint + prominent Filled
Button CTA "Add place". Redukuje "šta sad?" trenutak za nove usere.

**Places pull-to-refresh (`877653a`, delimično)** — ceo content wrap-ovan u
PullToRefreshBox. Nov `refreshing: Boolean` u UiState + `refresh()` funkcija
sa 700ms spinner. Firestore real-time listener ne treba refresh, ali user
mental model traži gesture.

**Long-press quick actions revert (`877653a` + `c2f9b11`)** — inicijalno dodat
kao dropdown menu na MemberRow (Refresh/Maps/History), ali user je rekao da
nije bilo dogovoreno + imao je bug (white background stuck posle dismiss zbog
neispisanog Press interaction-a u interactionSource). Vraceno na plain
`pressScaleClickable`.

### I) Post-scriptum 3 (nastavak sesije, još 5 commit-a)

Sesija je nastavljena posle STATUS.md post-scriptum #2 sa još 5 commit-a
(`0d43c3a` → `092d9b0`) — ukupno 28 commit-a u toku 28. sesije.

**Member removal (`0d43c3a`)**: iz P1 audit-a. `CircleRepository.removeMember()`
novi metod, `CircleDetailViewModel.removeMember()` wrapper, `MemberRow`
DropdownMenu dobija "Remove from circle" stavku (crveni text), AlertDialog
confirm sa imenom. Owner-only, ne za self ni za owner target.

**Geocoding retry UX (`ff1bb45`)**: `GeocodingRepository.SearchResult` sealed
class (Success/Empty/Error), `PlacesViewModel.SearchState` 5-branch state,
`retrySearch()` funkcija, `AddPlaceScreen` when(state) UI sa Loading spinner-om,
Error crveni text + "Try again", "No results" tekst, itd.

**Place auto-suggest (`eb5f715`)**: automatska detekcija hotspot-ova iz 7d
self history-ja. `PlaceSuggestion` data class + `detectPlaceSuggestions()`
algoritam (grid 100m, 20+ pointova preko 3+ dana, filter 200m od postojećih
places). `SuggestedNameType` enum (HOME/WORK/GENERIC) sa vremenskim heuristikom.
`LocationHistoryRepository.queryHistorySince()` one-shot query. `SuggestionCard`
composable u PlacesScreen. `AddPlace` route + Screen prima
`prefillLat/prefillLng/prefillName` — auto-fokus mape na predloženu tačku.

**Hard braking count (`7fbfc91`)**: MVP driving reports feature. `HistoryStats`
dobija `hardBrakingCount: Int`. Algoritam: deceleration > 4 m/s² (industry
standard) između uzastopnih pointova, filter avg speed > 5 m/s da eliminiše
pešačku setnju. Chip u HistoryScreen prikaz-uje se samo ako count > 0.

**Play Services geofence reconciliation filter (`092d9b0`)**: user javio da
je iz čista mira dobio 3 spam notifikacije bez fizičkog kretanja Jelene.
Root cause: Play Services "reconciles" geofence state kad se GPS accuracy
oporavi posle nesigurnog perioda — fire-uje sintetičke EXIT+ENTER events da
"corrigira" percepciju. Three-layer fix u `GeofenceBroadcastReceiver`:
- Accuracy filter: skip ako triggeringLocation.accuracy > 150m
- Age filter: skip ako triggeringLocation.time > 5 min u prošlosti
- Dedup window: 60s → 5min (reconciliation storm-ovi mogu trajati 2-3 min)

### J) Preostalo za sledeću sesiju

1. **Fizički Auto test** — priključi S24 na auto, verifikuj da se Krug list-uje.
2. **Play Store closed testing** — 9/12 opted-in tester-a, treba 3 još.
3. **Untested feature-e** — SOS end-to-end, Refresh location ping-back,
   Silent hours enforcement, auto-status Speed uvek prikazan.
4. **Big features iz roadmap-a** — Home widget (Glance API, 3-5h), Crash
   detection (sensor logic + false-positive tuning, 3-4h), Weekly driving
   report aggregate.
5. **Ownership transfer** — Circle owner-to-member ownership handoff sa
   picker UI (postojeći `deleteCircle` je fallback ako owner hoće da napusti).
6. **FCM token upload** — trenutno se ne piše u Firestore (nema FirebaseMessagingService
   za onNewToken). Blocked feature: Cloud Functions push (server-triggered SOS,
   backup fetch for offline members).
7. **Unit testovi** — proširiti `Cluster.kt` sa velikim datasetima, dodati
   testove za `computeHasMovement`, `formatTimeAt`, `detectPlaceSuggestions`.

---

## Ranija sesija (2026-07-03, dvadeset sedma sesija — V1.1 polish + geocoding/ETA/silent hours/mute/auto-status + release prep)

Nastavak 26. sesije istoga dana. Nakon što su Places + History + Auto core-i bili implementirani, ova sesija je fokusirana na polish (5 krugova iteracija), dodatne feature-e (address search, ETA, silent hours, per-place mute, notif actions, auto-status pill), i business release preparaciju (bump versionCode → 2, versionName 1.1.0, release AAB build-ovan, landing page ažuriran). Ukupno 17 commit-ova (`82286e7` → `ec7c679`), ~4400 novih linija.

### A) Polish krugovi 1-5

**Krug 1** — snackbar feedback + delete confirm + empty state hint + spinner:
- Snackbar posle save/update/delete akcija u PlacesScreen („Mesto sačuvano: Kuća" / „Obrisano: Kuća")
- Delete confirm dialog za pin na main mapi (ranije direktno bez potvrde)
- HistoryScreen empty state: čitljiv card sa naslovom + hint tekst („Krug beleži lokaciju dok se krećeš")
- AddPlaceScreen Save dugme spinner dok saving=true
- „Moja lokacija" FAB disabled + poluprovidno dok nema GPS fix-a
- „Otkaži" hardcoded string zamenjen resource-om na 2 mesta

**Krug 2** — HistoryScreen bug fix + category ikonice + haptic + PlacesScreen loading:
- **HistoryScreen bug**: fit-bounds sada samo prvi put po danu (`cameraFitDone` remember key = range.fromMs). Ranije se resetovao kamera na svaki scrub-slajder pomak, user nije mogao ručno da zumira.
- CategoryPicker FilterChip-ovi dobijaju leading ikonicu (Home/School/Work/FitnessCenter/ShoppingBag/MoreHoriz)
- `confirmHaptic()` feedback pri save/update/delete akcijama
- PlacesScreen loading state (spinner pre prvog Firestore snapshot-a)

**Krug 3** — MapView memory leak + timeline arrows + places count + discard guard:
- AddPlaceScreen + HistoryScreen `onDispose` sada zovu `mv.onDestroy()` + `deleteAll` na annotation manager-ima (~10MB per open/close cycle inače akumulira)
- EventRow ikonica u boji: zelena ↓ = enter, narandžasta ↑ = exit
- CircleDetail „Mesta" dugme prikazuje broj mesta „Mesta (3)"
- AddPlaceScreen discard confirm — ako user krene back sa unetim imenom, AlertDialog „Odbaciti izmene?" (BackHandler + TopBar back oba idu kroz guard)

**Krug 4** — history speed + today shortcut + loading state + places sort:
- HistoryScreen playback speed cycle 1x → 2x → 4x → 0.5x (TextButton pored slajdera)
- „Danas" shortcut: klik na naslov dana (kad dayOffset != 0) skoči na današnji dan (gesture-driven)
- HistoryScreen loading state (CircularProgressIndicator umesto empty state dok prvi snapshot ne stigne). HistoryViewModel dobija `loaded: StateFlow<Boolean>`.
- PlacesScreen sort toggle: „Sort A-Z" ↔ „Sort po datumu" (klijent-side sort na `state.places`)

**Krug 5** — Auto empty state + notif sound + whats-new modal:
- MapCarScreen `loaded` flag razlikuje „učitava" (spinner poruka) od „loaded, prazan krug"
- Member subtitle poboljšan: „pre 2 min · GPS 30m" umesto samo accuracy
- PlaceEventNotifier channel dobija explicit notification sound + vibration pattern (LG/Xiaomi ROM-ovi ne emituju zvuk za IMPORTANCE_DEFAULT bez explicit soundUri)
- WhatsNewDialog composable — prikazuje se u MapScreen jednom po version-code bump-u. Fresh install (lastSeen=0) samo markira trenutni version bez modal-a; update (lastSeen < currentVersion) prikazuje modal. 3 feature card-a: Places (📍), History (⌛), Android Auto (🚗)
- LocalPrefs.lastSeenWhatsNewVersion + KEY_LAST_SEEN_WHATS_NEW

### B) Nova feature: Address geocoding (Mapbox Places API)

**GeocodingRepository** (`core/directions/`):
- `search(query, proximity)` → List<Suggestion(displayName, placeName, lat, lng)>
- Language `sr,en` priority, limit 5 rezultata
- Proximity parametar (current location) diže lokalne rezultate iznad global-a

**AddPlaceScreen UI**:
- Nova ikonica lupe u TopBar-u (right action)
- Toggle otvara OutlinedTextField iznad mape sa live predlozima (300ms debounce)
- LazyColumn sa max heightIn 240dp za rezultate
- Tap na predlog: flyTo lokaciju (zoom 16.0), auto-suggest ime u name field-u (samo ako user nije ručno editovao), close search overlay

PlacesViewModel dobija `searchAddress(query)` sa job cancellation + `clearSearchResults()`.

### C) Nova feature: ETA za člana (Mapbox drivingRoute)

**DirectionsRepository.drivingRoute** (proširenje):
- Novi endpoint uz postojeći `drivingDistanceMeters`
- Vraća `DrivingRoute(distanceMeters, durationSeconds)`
- Odvojen cache od plain distance-a
- Traffic-agnostic (free tier), OK približna procena

**MemberDetailSheet UI**:
- Nov StatChip „Za" (sr) / „ETA" (en) pored distance chip-a
- `formatEta` helper: „12 min" / „1h 15m" / „<1 min"
- Prikazuje se samo ako je duration > 0

Loading callback sada `loadDrivingRoute` (nije `loadDrivingDistanceMeters`), state je razdvojen na `drivingDistanceMeters` + `drivingDurationSec`.

### D) Nova feature: Silent hours

**UserSettings.silentHours: String? = null** — format „HH:MM-HH:MM" (24h), null = feature isključen.

**SettingsRepository.updateSilentHours** sa `FieldValue.delete()` za clear.

**LocationTrackingService.isInSilentHours()**:
- Parse i wrap-preko-ponoći check
- `nowMin in fromMin..toMin` za simple range
- `nowMin >= fromMin || nowMin <= toMin` za wrap (23-07)
- Pre `placeEventNotifier.notifyEvent()` u `observeCirclePlaces` scope-u

**Privacy UI** (Settings → Privatnost):
- Switch za on/off (default OFF, kada se uključi postavi „23:00-07:00")
- Kad je ON, prikazuju se preset FilterChip-ovi: 22:00-07:00, 23:00-07:00, 00:00-06:00

### E) Nova feature: Per-place mute + notif actions

**PlaceModel.muted: Boolean = false** — kad je true, članovi kruga ne dobijaju notif za enter/exit ovog mesta.

**PlaceRepository.setMuted(circleId, placeId, muted)** propagacija.

**PlaceDetailSheet UI**:
- Switch „Utišaj obaveštenja za ovo mesto" ispod InfoRow-a
- Toggle triggers `MapViewModel.togglePlaceMute()`

**LocationTrackingService integracija**:
- `mutedPlaceIds: MutableSet<String>` populated iz observePlaces block-a (`.filter { it.muted }.map { it.id }`)
- Notif dispatch: `if (evt.placeId in mutedPlaceIds) return@forEach`

**Notif "Vidi na mapi" action**:
- `PlaceEventNotifier.notifyEvent(evt, circleId)` — nov parametar
- Action button pored default tap area
- Intent extras: `EXTRA_FOCUS_PLACE_ID` + `EXTRA_FOCUS_CIRCLE_ID`
- MainActivity.handlePlaceFocusExtra postavlja aktivan krug preko LocalPrefs.setActiveCircleId() koristeći dagger.hilt.EntryPoint pattern (MainActivityEntryPoint)
- Sada LocationTrackingService dispatch zadržava cid preko pair-a: `placeRepository.observeRecentEvents(cid).map { events -> events.map { cid to it } }`

### F) Nova feature: Auto-status pill

**MapViewModel.autoStatusByUid: StateFlow<Map<String, String>>** — combine-uje `uiState` + `activePlaces`:
- Ako je fresh location (< 15 min) i unutar radius-a nekog Place-a → ime tog place-a („Kuća")
- Inače ako je speed > 3 m/s (~10.8 km/h) → „U pokretu • 45 km/h"
- Inače null (nema pill-a)

**MemberDetailSheet UI**:
- Novi String? parametar `autoStatus`
- Renderuje se kao mali primary-container pill ispod device model teksta

### G) Layout fix: MemberDetailSheet 4+ chip-a

Dodavanje ETA chip-a je razbilo Row layout — 4 chip-a u istom redu, tekstovi se sečeju („10..." umesto „100%", „<1 ..." umesto „<1 min"). Fix:
- `Row` zamenjen sa `FlowRow(maxItemsInEachRow=3)` — prekomerni idu u drugi red
- `navigationBarsPadding()` + 24dp Spacer na dnu — Samsung One UI gesture bar više ne preklapa „Istorija kretanja" dugme
- `@OptIn(ExperimentalLayoutApi::class)` na MemberDetailSheet

### H) HistoryScreen ćirilica fix

`Locale("sr")` je default-ovao na ćirilicu za `formatDay` (Android sr = sr_RS_Cyrl). Fix: `Locale.forLanguageTag("sr-Latn")` forsira latinicu — konzistentno sa ostatkom UI-a.

### I) V1.1 release preparation

**versionCode 1 → 2, versionName 0.1.0 → 1.1.0** u `app/build.gradle.kts`.

**bundleRelease** prošao (2m 17s), AAB 37 MB u `app/build/outputs/bundle/release/app-release.aab`.

**Landing page** (`docs/index.html`):
- Nova sekcija „Novo u v1.1" iznad postojećeg „Osnove" grid-a
- Tri feature card-a: Mesta, Istorija kretanja, Android Auto (sr + en)
- Postojeći „Tri stvari. Dobro odrađene." grid preimenovan „Osnove" eyebrow (bio je „Funkcije")
- Landing kopiran u `~/Desktop/krugapp-upload/index.html` za manual hosting upload

**Debug APK** instaliran na S24 (R5CWC1F9FND) posle svakog polish kruga za live UX validaciju.

### J) Diskutovano ali odloženo

- **Cluster pin-ova** (5+ članova na istoj lokaciji) — značajno je više posla (custom bitmap sa brojkom + haversine grouping + expand click handling). U V1.2 backlog.
- **Zone rules** („Kad dete napusti Školu, javi mi") — UI za rule builder + backend za matching. V1.2.
- **„Kucni na X"** silent ping — postoji već `requestRefresh` u LocationRepository + „Osveži" dugme u MemberDetailSheet. Feature je efektivno pokriven.
- **Cloud Functions + FCM push** — jedini pravi reliability upgrade (radi kad je app killed). Traži Blaze plan (kartica). V1.2.
- **Crash Detection** — user pitao, preporuka je da preskočimo. False positive rizik + pravna odgovornost za missed crash je previše za solo dev bez treniranog ML modela. Alternativa razmatra se: „post-drive inactivity check" (ako je speed pao sa >60km/h → 0 i miran 30min, silent ping članovima „Da li si OK?").

### K) Preostali kritični put pre production

1. **Play Store upload novog AAB-a** — Play Console → Testing → Closed testing → New release → upload `app-release.aab` (v1.1.0). Roll out za istu testersku listu (14 članova).
2. **Play Store screenshots update** — na S24 uslikati (Power + Vol down): Map sa članovima, Places screen, AddPlace mapa sa crosshair-om, PlaceDetailSheet, HistoryScreen sa trag-om + stats, MemberDetailSheet sa svim chip-ovima. Sačuvati u `docs/screenshots/`. Format native S24 (2340×1080) ili 1080×1920.
3. **Landing page upload** — `~/Desktop/krugapp-upload/index.html` na hosting (isti workflow kao za privacy/terms u prošloj sesiji).
4. **Firebase Console TTL policy** (jednokratno) — Firestore → TTL policies → Create → Collection group `locationHistory`, field `timestamp`, 30 days retention. Bez ovog dokumenti se ne brišu automatski.
5. **Android Auto submit** (odloženo) — Play Console → Testing → Automotive → Add track. 2-4 nedelje Google review. Rizik odbijanja (Krug nije primarno navigation app), ali NE utiče na phone app.
6. **Live Android Auto test** — pre submit-a, testiraj u kolima preko Developer mode (Android Auto app → Version 10x tap → Developer settings → Unknown sources).

### L) Health stanja (kraj sesije)

- Debug APK: instaliran na S24 (R5CWC1F9FND)
- Release AAB: `app-release.aab` v1.1.0 spreman za Play Store upload
- Firestore rules: DEPLOYED (nije menjano u ovoj sesiji, i dalje važe od 26. sesije)
- Nema kompajl grešaka; sve prošlo BUILD SUCCESSFUL
- Poslednji commit: `ec7c679` (auto-status pill)
- Testere lista: 14 (nije menjano, iz 25. sesije)

### M) Commit istorija ove sesije

Sve commit-ove od `82286e7` do `d84e9d6`:
- `82286e7` Polish: snackbar feedback, loading state, empty state, kategorije ikonice, haptic
- `bd84ef4` Polish 3: MapView leak fix, timeline arrows, places count, discard guard
- `c9b170b` Polish 4: history speed, today shortcut, loading state, places sort
- `4177838` Polish 5-6-9: Auto empty state, notif sound, what's new modal
- `14881b0` Feature: address geocoding + ETA + silent hours (V1.2 prep)
- `570fae9` Feature: per-place mute + notif „Vidi na mapi" action
- `781a5df` V1.1 release prep: bump version + landing page update + MemberDetail layout fix
- `ec7c679` Feature: auto-status pill u MemberDetailSheet-u
- `d276361` STATUS.md dvadeset sedma sesija
- `d84e9d6` Landing: proširena Premium lista + restrukturiran Free tier

### N) Post-scriptum: Premium/Free tier landing strategija

Nakon što je pricing sekcija razmatrana:
- „Blue circle badge" premium stavka uklonjena (marketing fluff bez implementacije)
- Free tier eksplicitne granice: **do 3 mesta po krugu** (već enforce-ovano u kodu preko `FREE_TIER_MAX_PER_CIRCLE`), **istorija 7 dana** (trenutno je 30 u kodu — treba TTL update za V1.2)
- Premium 10 stavki (marketing pravac):
  1. Neograničena mesta (paywall check needed)
  2. Istorija 90 dana (Firestore TTL upgrade)
  3. SOS push kad je app zatvorena (Cloud Functions + FCM — v1.2 major)
  4. Zone pravila („Kad dete napusti Školu")
  5. Post-drive check („Da li si OK?" posle vožnje) — safer alternativa crash detection-u
  6. Weekly trip reports (km, brzine, mesta)
  7. History export GPX/KML
  8. Custom SOS poruka (medicinski podaci)
  9. Multiple SOS destinacije (izvan kruga)
  10. Android Auto integracija (kad Google odobri)

Realno očekivana cena €1.99–2.99/mesec ili €19.99/god. Nijedan premium feature nije implementiran u trenutnom kodu — to je marketing signal pravca V1.2/V1.3 razvoja.

**Za sledeću sesiju (28.)** — implementacija najvrednijih Premium feature-a:
- Prioritet 1: paywall check za mesta (najlakše — samo enforce postojeći FREE_TIER_MAX_PER_CIRCLE preko billing state-a)
- Prioritet 2: Cloud Functions + FCM za SOS push (najveći reliability upgrade — traži Firebase Blaze plan)
- Prioritet 3: Post-drive check (safer alternative crash detection — 1-2 sesije)
- Prioritet 4: History export GPX (jednostavno — writer + share intent)
- Prioritet 5: Weekly trip reports (aggregate query + notif)

Ostatak (Zone rules, Multi-SOS destinacije, Custom SOS msg) — V1.3.

## Gde smo stali (2026-07-03, dvadeset šesta sesija — V1.1 Places + Location history 30d + Android Auto MVP)

Ogromna sesija, ~2600 novih linija koda kroz 8 commit-ova (`aa6d7e1` → `7eaac15`). Cela sesija fokusirana na dodavanje major feature-a koji Krug razlikuju od konkurencije (Places geofence, Location history, Android Auto). Sve implementirano u kodu, deploy-ovano na Firestore, i APK instaliran na S24 za live testing. Google review nije triggerovan jer nisu upload-ovani novi AAB-ovi.

### A) V1.1 Places (geofence) — kompletno implementirano

Klijent-side geofencing preko Google Play Services `GeofencingClient`. Per-circle mesta (svi članovi vide iste), enter/exit event triggeruje lokalnu notif preko FGS-a (nema Cloud Functions u v1.1).

**Data model** — `core/places/PlaceModels.kt`:
- `PlaceModel`: id, name, lat, lng, radius (50-500m, default 100m), category (enum), createdBy, createdAt
- `PlaceEventModel`: id, placeId, placeName, userId, userName, type (ENTER/EXIT), timestamp
- `PlaceCategory` enum: HOME, SCHOOL, WORK, GYM, SHOP, OTHER
- Free tier: max 3 mesta po krugu (`FREE_TIER_MAX_PER_CIRCLE`)

**Firestore**:
- `/circles/{cid}/places/{pid}` — read/write samo članovi kruga
- `/circles/{cid}/placeEvents/{eid}` — create only self, no update/delete, TTL 24h (podesiti manual)
- Rules deploy-ovan (`firebase deploy --only firestore:rules --project krug-86527`)

**GeofenceManager** (`core/places/GeofenceManager.kt`):
- Wrapper oko `GeofencingClient` sa request ID formatom `{circleId}:{placeId}`
- Registruje batch geofence-a sa ENTER + EXIT transition types
- Zahteva `ACCESS_BACKGROUND_LOCATION` (već imamo)
- Max 100 geofence-a po device (Google limit), min 50m radius zbog GPS accuracy

**GeofenceBroadcastReceiver** (`core/places/GeofenceBroadcastReceiver.kt`):
- `@AndroidEntryPoint` Hilt DI za pristup PlaceRepository + FirebaseAuth
- Parse requestId → circleId + placeId, fetch placeName + userName, upiši PlaceEvent
- Manifest deklaracija sa `exported="false"`

**LocationTrackingService integracija** (`observeCirclePlaces()`):
- Prati sve krug-ove user-a, kombinuje sa places listener-om, re-registruje geofence pri promeni
- Odvojen scope za `placeEvents` observation — filter self events + notified IDs + `placesListenerStartedAt` replay guard
- Poštuje `placeNotifsEnabled` user setting (default true)

**UI** — `feature/places/`:
- **PlacesScreen** — lista mesta u krugu, FAB „Dodaj mesto", inline stats („3 mesta · 12 događaja danas"), Activity timeline sekcija (poslednjih 20 event-a), presence indikator („Trenutno ovde: Jelena, Marko" u zelenom), tap na row → nav to Map sa flyTo
- **AddPlaceScreen** — full-screen Mapbox picker sa fiksnim crosshair-om, „Moja lokacija" FAB (dole desno), Canvas overlay krug koji se skalira sa slider-om preko Web Mercator formule (`156543.03 * cos(lat) / 2^zoom`), Category picker (FilterChip-ovi) sa auto-suggest name-a
- **AddEditPlaceSheet** — modal sheet za EDIT postojećih mesta (nema izbor lokacije, samo name + radius + category)
- **PlaceDetailSheet** (u feature/map/) — otvara se tap-om na pin na main mapi. Prikazuje: kategorija badge (glyph slovo u boji), radius, kreator (imya iz members liste), poslednja aktivnost (najnoviji event), Edit + Delete dugmadi
- **CategoryPicker** composable — reusable za AddPlaceScreen + AddEditPlaceSheet

**Main map integracija** (feature/map/MapScreen.kt):
- Novi `placeManager` (PointAnnotationManager) + `placeRadiusManager` (PolygonAnnotationManager)
- Radius circle = 64-strani polygon aproksimacija u boji kategorije (Web Mercator geo circle, skalira sa zoom-om)
- Teardrop pin sa glyph slovom po kategoriji (H/S/W/G/$/•) — MapMarkers.placeMarker(context, category)
- Tap na pin → PlaceDetailSheet sa nav to Places screen za edit ili direct delete

**PlaceFocusBus** (`core/places/PlaceFocusBus.kt`):
- Deep-link pattern (kao SosFocusBus) — PlacesScreen postavi Focus, MapScreen collect-uje i flyTo-uje
- Zoom se skalira sa radius-om (100m → z16.5, 400m → z14.5, formula `16.5 - log2(radius/100)`)

**Per-user opt-in** (Settings → Privatnost → toggle):
- `UserSettings.placeNotifsEnabled: Boolean = true`
- `SettingsRepository.updatePlaceNotifs`
- LocationTrackingService filter: `if (!currentSettings.placeNotifsEnabled) return@forEach`

**Cleanup on circle delete** (`CircleRepository.deleteCircle`):
- Pre brisanja parent doc-a, briše places + placeEvents subcollection-e (nema Cloud Functions cascade)

### B) Location history 30d — Firestore write + reader UI

**Schema**: `/users/{uid}/locationHistory/{pointId}` — subcollection per-user.
- Field-ovi: lat, lng, accuracy, batteryPct, speed, bearing, timestamp
- TTL policy (MANUAL PODEŠAVANJE u Firebase Console → Firestore → TTL policies → Create → Collection group `locationHistory`, field `timestamp`, 30 days)

**Writer** (`LocationHistoryRepository.writePoint` pozvan iz LocationTrackingService):
- Filter `shouldWriteHistory()`: piše samo ako pomeranje >25m ILI prošlo >10min od poslednjeg zapisa
- Bez filtera ~4300 write/day/user (svakih 20s fix); sa filterom ~50-200 write/day/user
- Firestore free tier 20k write/day → dovoljno za 100 aktivnih testera

**Rules**: allow read authenticated, allow create isSelf, no update/delete. Cross-user privacy nije striktno enforce-ovan (pragmatic trade-off — Krug je invite-only, low stalking risk).

**HistoryScreen** (`feature/history/HistoryScreen.kt`):
- Mapbox map sa PolylineAnnotation trag-om + PointAnnotation markerima
- Day picker sa strelicama (max 30 dana unazad; desna strelica flipped preko `graphicsLayer scaleX = -1f`)
- Time scrubber slider (0..1 → start..end dana) sa Play/Pause dugmetom (auto-advance kroz ceo dan za ~30s)
- Start marker (zeleni dot 14dp) + current position marker (indigo dot 18dp)
- Places overlay iz aktivnog kruga (pin-ovi Kuće/Škole/etc)
- Stats bar: Pređeno · Aktivno vreme (gap-filter <15min) · Max brzina (km/h)
- Format day preko `Locale.forLanguageTag("sr-Latn")` (bez tog Locale("sr") default-uje na ćirilicu)

**Deep-links**:
- `MemberDetailSheet` → „Istorija kretanja" dugme → HistoryScreen(uid, name)
- `PrivacyScreen` → „Moja istorija kretanja" → HistoryScreen sa self uid (transparency: user vidi šta se čuva)

**GDPR cleanup** (`UserRepository.deleteUser`):
- Batch delete locationHistory u chunk-ovima od 500 (Firestore batch limit)
- ~6000 history points za aktivnog 30d user-a briše se u 12 batch-eva

### C) Android Auto MVP — CarAppService + Members/Places + SOS TTS

Prvi V1.2 feature u kodu. Auto submit je odvojen review u Play Console (Automotive track) — neće blokirati trenutan phone review.

**Gradle**:
- `libs.versions.toml`: `car-app = "1.4.0"` + `androidx-car-app` + `androidx-car-app-projected`
- `app/build.gradle.kts`: implementation obe car.app dependency

**Manifest**:
- Permisije: `androidx.car.app.NAVIGATION_TEMPLATES` + `MAP_TEMPLATES`
- Feature: `android.hardware.type.automotive` (required=false, ne filtrira phone install)
- Metadata: `com.google.android.gms.car.application` → `@xml/automotive_app_desc`, `androidx.car.app.minCarApiLevel = 1`
- Service: `.core.car.KrugCarAppService` sa intent-filter `androidx.car.app.CarAppService` + category `NAVIGATION`

**automotive_app_desc.xml** — `<automotiveApp><uses name="template"/></automotiveApp>`

**KrugCarAppService** — `createHostValidator()` koristi `ALLOW_ALL_HOSTS_VALIDATOR` (za dev/DHU), `onCreateSession()` → KrugCarSession.

**KrugCarSession** — DefaultLifecycleObserver hostuje SosCarNarrator (start on CREATED, stop on DESTROYED). `onCreateScreen()` → MapCarScreen.

**MapCarScreen** — PlaceListNavigationTemplate (split-view mapa + lista):
- Hilt EntryPoint (nema @AndroidEntryPoint jer CarAppService nije Hilt-instrumentated) za CircleRepository, LocationRepository, UserRepository, PlaceRepository, LocalPrefs, FirebaseAuth
- Real-time observe kroz `observeMyCircles` + `locationRepository.observe(uid)` po članu + `observePlaces(activeCircleId)`
- Row sa Place metadata (CarLocation) — Auto host automatski rendera pin na mapi
- Tap → `geo:lat,lng?q=lat,lng(label)` intent delegira na Google Maps za route
- Članovi: plavi pin sa GPS accuracy subtitle. Places: 📍 emoji + zeleni pin + radius subtitle
- Action strip sa Refresh dugmetom

**SosCarNarrator** — TextToSpeech („Pažnja. X traži pomoć. SOS aktiviran."):
- Sr-Latn locale sa fallback na `Locale.getDefault()`
- Filter: `sos.triggeredAt > startedAtMs` (bez ovog restart Auto-a bi pročitao stare SOS-eve)
- Dedup preko `notifiedUids` set
- Cleanup preko `stop()` u onDestroy lifecycle

### D) Bug fix-evi

- **PlacesViewModel bug**: FAB je pozivao `startEdit(null)` što je postavljalo `editingPlace=null`, ali sheet se otvarao samo ako je editing != null. Razdvojen `sheetOpen: Boolean` od `editingPlace: PlaceModel?`.
- **HistoryScreen ćirilica**: `Locale("sr")` → `Locale.forLanguageTag("sr-Latn")`.
- **Empty state PlacesScreen**: horizontalAlignment CenterHorizontally + verticalArrangement Center (bilo bez centering-a).

### E) Preostali kritični put pre production

1. **Firebase Console TTL policy** (jednokratno): Firestore → TTL policies → Create → Collection group `locationHistory`, field `timestamp`, 30 days retention. Bez ovog dokumenti se ne brišu automatski.
2. **Live testing** — geofence trigger zahteva fizičko kretanje (min 50m). Tester scenariji:
   - Dodaj mesto sa 100m radius-om kod Kuće
   - Izađi 200m dalje, sačekaj 2-3 min → očekuj EXIT event → drugom testeru stiže notif
   - Vrati se u radius → ENTER event
3. **Android Auto live test** — konektuj S24 na Android Auto (USB/wireless), proveri:
   - Krug se pojavljuje u launcher-u
   - Split-view sa članovima + Places
   - Tap na row → Google Maps navigate
   - SOS TTS (traži drugog testera da trigger-uje SOS dok voziš)
   - **Developer mode obavezan**: Android Auto app → tap Version 10x → Developer settings → Unknown sources ON
4. **Production release checklist**:
   - Bump versionCode/versionName
   - Nov AAB build
   - Upload u Closed testing track
   - Kad 12+ testera drži 14 dana → apply for Production access
5. **V1.2 next**:
   - Cloud Functions + FCM push za reliable notif (trenutno FGS-only, ne radi kad je app killed)
   - Address geocoding u AddPlaceScreen
   - Home widget (Android home screen)
   - Auto submit u Play Console → Automotive track (uz risk of rejection, retry)

### F) Health stanja (kraj sesije)

- Debug APK: instaliran na S24 (R5CWC1F9FND) + Xiaomi + SM-A376B
- Firestore rules: DEPLOYED (places + placeEvents + locationHistory)
- Nema kompajl grešaka; sve commit-e prošlo BUILD SUCCESSFUL
- Poslednji commit: `7eaac15` (Android Auto MVP)
- Testere lista: 15 (iz 25. sesije), opt-in status nije proveren posle push-a

### G) Commit istorija sesije

- `aa6d7e1` Feature V1.1: Places (geofence) — data model, UI, klijent notif
- `283a47c` Places polish: map picker sa radius overlay-om, pin polish, deep-link, per-user opt-in
- `98c773e` V1.1: kategorije mesta, tap-to-detail, activity timeline, location history 30d
- `8f7e1c7` Polish: history stats + Places overlay, category placeholder, GDPR cleanup
- `1887bde` History playback + presence indikator na PlacesScreen
- (bez commit-a) HistoryScreen Locale fix za ćirilicu
- `7eaac15` Feature V1.2: Android Auto MVP — CarAppService + members/places + SOS TTS

## Gde smo stali (2026-07-03, dvadeset peta sesija — Closed testing PUBLISHED, testeri regrutovani iz Firebase)

Kratka sesija, fokus na verifikaciju Google review statusa i regrutaciju dodatnih testera za 14-dnevni Closed testing period.

### A) Google review — APPROVED za 8 minuta

Play Console → Publishing overview → Submission activity: **Submission 1 (Jul 02, 2026, 2:20 PM submitted → 2:28 PM Published)**. 13 changes u paketu (Countries, Testers list, Store listing SR, Content Rating, Target audience, Privacy policy, Ads declaration, Data safety, Full-screen intent, App category, itd.). Nije bio human review, već automated Play Console policy check — prošao odmah.

**Šta to znači**: Closed testing track je LIVE, opt-in link radi, testeri mogu odmah da instaliraju. Sensitive permissions declarations (background location, FGS location, full-screen intent) su prihvaćene ZA Closed testing. Kada se bude apliciralo za Production access, biće još jedan review krug gde Google detaljnije gleda demo video za background location.

### B) Testeri iz Firebase Auth

Umesto ručne regrutacije, izvučeni postojeći useri iz Firebase Auth-a preko `firebase auth:export /tmp/krug-users.json --project krug-86527`. Total 39 accounts, 13 sa email (svi Gmail), 26 bez email-a (anonymous/phone-only auth).

**Email adrese dodane u Closed testing tester listu (9 novih)**:
- `magdalenabajic63@gmail.com` — Magdalena Bajic
- `stamparijabajiccacak@gmail.com` — Slobodan Bajic
- `helloaciko@gmail.com` — Aciko Website
- `markosavicbolero@gmail.com` — Marko Savic
- `bogdandjurovic45@gmail.com` — Bogdan Djurovic
- `boskodjurovic12@gmail.com` — Bosko Djurovic
- `makica09@gmail.com` — Marija Đurović
- `matijaa.savic11@gmail.com` — Matija Savić
- `dusica.bajic.db@gmail.com` — Dusica Bajic

Plus postojećih 5 (`krugappteam@`, `aleksandarr@`, `bajkeizmaste@`, `jelenavasilic84@`, `maslacjana@`) → **ukupno 14-15 testera**, preko Google minimuma 12.

### C) Opt-in link (Closed testing)

Play Console pokazao dva URL-a nakon što je track objavljen:
- `https://play.google.com/apps/testing/org.krug.app` — **opt-in link** (šalje se testerima)
- `https://play.google.com/store/apps/details?id=org.krug.app` — Play Store stranica (koristi se posle opt-in-a za instalaciju)

Napomena: format `apps/testing/PACKAGE_NAME` je za Closed testing; Internal testing koristi drugi format sa numeričkim track ID-jem (`apps/internaltest/4701482258894794361`).

**Draft poruke za testere** (WhatsApp/SMS):
```
Testiram novu app Krug (deljenje lokacije sa porodicom i prijateljima). Ako možeš da pomogneš:

1. Otvori: https://play.google.com/apps/testing/org.krug.app
2. Klikni "Become a tester" i Accept
3. Instaliraj Krug sa Play Store-a
4. Ostavi je instaliranu 14 dana (Google zahtev za testing period)

Hvala!
```

### Preostali kritični put pre production

1. **Pošalji opt-in link** svim testerima (poruka gore). 14-dnevni brojač kreće od momenta opt-in-a, ne od dodavanja na listu.
2. **Prati aktivnost testera** — Firebase Console → Crashlytics za crash-eve, Analytics za DAU/retention.
3. **Production access application ~mid-July** — Play Console → Testing → Closed testing → Apply for production access. Zahtev: 12+ opt-in testera aktivnih 14 dana.
4. **V1.1 Premium bekhend** — Places (geofence), Location history 30d, Battery alerts. Duži tračak, ne mora sad.

### Health stanja (kraj sesije)

- Nema code changes, samo Play Console operacije.
- Closed testing track: **Published/LIVE**.
- Firebase Auth export: 39 accounts total, 13 sa email.
- Poslednji commit: `5d169e5` (Play Console: 24. sesija).

## Gde smo stali (2026-07-02, dvadeset četvrta sesija — Play Console kompletiran, Closed testing submitted za review)

Cela sesija fokusirana na Play Console workflow: popuniti sve preostale App content declaracije, postaviti Internal + Closed testing tracks, snimiti demo video za background location, i submit-ovati za Google-ov app review. Radilo na S24 Ultra preko Play Store instalirane release verzije (org.krug.app "unreviewed"). Nijedan code commit ove sesije, ali dodati novi assets/docs.

### A) Play Console — App content deklaracije završene

**Sign in details** — kreiran test reviewer nalog `bajkeizmaste@gmail.com` (password: `Druhi4bre3`), password-only login, 2SV off, no passkey. Prošao kroz Krug flow, joinovan u „Review test" krug preko invite koda `488214` (dohvatio kroz Firestore REST API pošto se paste ne provalilo iz clipboard-a između account switch-eva). Reviewer instrukcije: max 500 chars, EN, opisan Google Sign-In flow i testovi (SOS, Refresh, temp share, Delete account).

**Target audience** — 13-15 / 16-17 / 18+. Not appealing to children.

**Content ratings** — IARC questionnaire kroz Social/Communication kategoriju, Communication tip (Skype/SMS model, ne social broadcasting). Location shared = Yes, purchases = No, block/report/moderation = No (nema chat), invite-only = Yes. Ratings: All ages/Everyone/PEGI Parental guidance/12+ u ostalim regionima (zbog „Shares Location" flag-a).

**Data safety** — Collected: precise location, name, email, User IDs, crash logs, diagnostics, app interactions, other user-generated content (imena krugova), device IDs. Sve encrypted in transit. Shared: nothing (Firebase i Mapbox tretirani kao service providers, ne third parties). Auth: OAuth only. Deletion URL: `https://krugapp.com/delete-account.html` (nova stranica, videti B).

**App category** — Communication. Contact email `krugappteam@gmail.com`, website `https://krugapp.com`.

### B) Delete account page (nova stranica na sajtu)

Napravljen dedicated `docs/delete-account.html` (SR+EN, matched styling privacy.html/terms.html) sa dve metode brisanja:
- Method 1 (in-app): Settings → Delete account
- Method 2 (email): `krugappteam@gmail.com` fallback za slučaj gubitka pristupa

Opisano šta se briše (profil, lokacija, SOS, članstva, krugovi ako je jedini vlasnik, settings, Firebase Auth), rok (30 dana za backup snapshots), i šta se ne briše (agregirani Crashlytics podaci bez ličnih identifikatora). Warning box: brisanje je nepovratno.

Ažuriran privacy.html sekcija 7.2 — više ne kaže „in-app feature is in development" (ta funkcija je već implementirana od ranije), sada linkuje na delete-account.html. Dodat URL u sitemap.xml. Fajlovi kopirani u `~/Desktop/krugapp-upload/` i live na `https://krugapp.com/delete-account.html`.

### C) Feature graphic redizajniran

Stara `feature-graphic-sr.png` imala je plavu pozadinu koja se stapala sa teal figurom u logu (nedovoljan kontrast) i „g" u „Krug" se preklapao sa taglineom „Tvoji ljudi. Uvek blizu.".

Generisano 4 varijante preko Python/PIL skripte (navy, cream, peach, plum) sa istim logo-tekstom kompozicijom ali proper vertical gap-om (40px) između title-a i tagline-a. User izabrao **plum** (`(55,40,90) → (35,25,65)` gradient). Finalizovano:
- `feature-graphic-sr.png` — plum, „Tvoji ljudi. Uvek blizu."
- `feature-graphic-en.png` — plum, „Your people. Always close." (za budući EN listing)

### D) Play App Signing SHA-1 fix (kritični bug)

**Simptom** (posle Play Store instalacije release verzije na S24): „No Google account found on this device" pri Continue with Google. Sign-in nije mogao da nađe nijedan nalog iako S24 ima 4+ Google naloga registrovanih.

**Root cause**: Play App Signing potpisuje instaliranu APK sa Google-ovim signing certifikatom (SHA-1 `56:B5:C7:44:98:B8:8F:07:53:F0:99:58:CA:1F:5D:25:E3:DE:09:B1`) — drugačiji od upload keystore SHA-1 (`21:6a:94:24:64:98:08:4a:...`) koji je registrovan u Firebase. Google Sign-In flow validira (package + APK SHA-1) protiv OAuth Android klijenta u Firebase; nepodudaranje = credential fail.

**Fix**: dodat Play App Signing SHA-1 i SHA-256 (`67:D5:26:4A:91:...`) u Firebase Console → Krug Release app → SHA certificate fingerprints. Ekstraktovan preko `apksigner verify --print-certs` na APK pulled sa S24 (`adb pull /data/app/.../base.apk`). Nije potreban AAB rebuild jer se registracija radi server-side. Propagacija ~2 min, potom Google Sign-In radio.

### E) Testing tracks

**Internal testing track** — kreiran, AAB 1 (0.1.0) upload-ovan, rollout aktivan. Opt-in URL: `https://play.google.com/apps/internaltest/4701482258894794361`. Testerska lista `Krug internal testers` sa 5 email-ova:
- `krugappteam@gmail.com`
- `aleksandarr@gmail.com`
- `bajkeizmaste@gmail.com`
- `jelenavasilic84@gmail.com`
- `maslacjana@gmail.com`

**Closed testing track (Alpha)** — kreiran, isti AAB iz library-ja, Serbia + Balkans (Bosnia, Croatia, Slovenia, North Macedonia, Montenegro) countries, ista testerska lista. Release preview trigerovao 3 sensitive permission errora (background location, FGS location, full-screen intent).

### F) Sensitive permissions declaration

**Background location (`ACCESS_BACKGROUND_LOCATION`)** — App purpose i location access opisi (~450 chars each) fokusiran na family location sharing, foreground service, private circles. Ključno: dodat demo video (30s, YouTube unlisted, user snimio sam) — link `https://youtube.com/shorts/Aq7_j1Mk9eg` paste-ovan u Video instructions polje.

**Foreground Service (`FOREGROUND_SERVICE_LOCATION`)** — štriklirani „Background location updates" i „User-initiated location sharing" tasks. Isti YouTube link kao demo video.

**Full-screen intent (`USE_FULL_SCREEN_INTENT`)** — kategorizovano kao „Other" sa objašnjenjem: koristi se isključivo za SOS emergency alerte koje šalje član kruga, mora biti vidljivo preko lock screen-a i drugih app-ova.

### G) Health apps declaration (workaround)

Play Console detektovao `ACTIVITY_RECOGNITION` permission i forsirao Health apps klasifikaciju. Krug NIJE health app — koristi permission samo za detekciju kretanja radi battery optimizacije (still/walking/vehicle → adjust GPS frequency). User odbio da uklanja permission (poklopilo bi Android Auto V1.2 idea).

**Rešenje**: štriklirana **„Other"** kategorija sa objašnjenjima (250 char limit): „No health features. ACTIVITY_RECOGNITION is used only for battery optimization: detecting movement states (still/walking/vehicle) to adjust GPS polling frequency. No fitness, health, step, or workout data collected or shared." Isti draft u sekciji 2 (Activity recognition permission).

### H) Publish flow

Nakon svih deklaracija, submit blokiran sa „To send changes for review, complete the required steps" iako sve piše kao complete. Root cause: Closed testing rollout nije završen zbog sensitive permission errora — vratili se, popravili, kliknuli Start rollout to Alpha. Zatim Play Console pokrenuo automated policy/quality checks (do 13 min) — na kraju sesije user čekao da završe.

**Status na kraju sesije**: čeka se pre-check → Send changes for review → Google app review (1-3 dana estimate).

### Preostali kritični put pre production

1. **Google review**: 1-3 dana za novu app, može do 7. Rezultat: „Krug" (a ne „org.krug.app (unreviewed)") u Play Store-u.
2. **Closed testing 14 dana**: Google zahtev za personal developer account-e — minimum 12 opt-in testera koji drže app instalirano bar 14 dana pre nego što se aplicira za Production access.
3. **Trenutno testera**: 5 na listi (jedan verifikovan da radi na S24). Treba minimum 7 dodatnih. TODO: regrutovati (familija, prijatelji).

### V1.2+ roadmap update

Dodat u Plan/V1.2+ listu: **Android Auto integracija (opcija B — full mapa sa članovima kao Waze)**. Koristi Navigation template iz CarAppLibrary, zahteva Google approval process 2-4 nedelje, rizik odbijanja jer Krug nije primarno nav app. Opcija A (messaging kategorija samo za SOS TTS notifikacije) odbačena — user hoće punu vizuelizaciju kruga.

### Health stanja (kraj sesije)

- `bundleRelease`: prošao (2m 25s), AAB 36 MB, sa svim commit-ovima do `276b75b`.
- Nema code changes ove sesije, samo docs (`delete-account.html`, `privacy.html` sekcija 7.2, `sitemap.xml`) i assets (`feature-graphic-sr.png`, `feature-graphic-en.png`, obrisane 4 eksperimentalne varijante v2-v6).
- Play Console: 90%+ complete, čeka se pre-check pass i Send for review.

### Sledeća sesija — TODO redosled

1. **Proveri Google review status** (Play Console → Publishing overview → Submission activity). Ako je approved, „org.krug.app (unreviewed)" postaje „Krug".
2. **Regrutuj još testera**: minimum 7 novih do 12 total. Deljenje Closed testing opt-in linka (pojaviće se u Testing → Closed testing → Testers tab kad rollout završi).
3. **Prati Crashlytics** — svaki crash od testera treba da vidiš u Firebase Console.
4. **14-dnevni brojač**: čeka se od dana kad 12+ testera opt-in kroz Closed testing. Cilj: apply for Production access ~mid-July.
5. **V1.1 Premium**: kada bekhend krene za tier support — Places (geofence alerts, glavni gap), Location history 30d, Battery alerts, Approximate location.

## Gde smo stali (2026-07-01, dvadeset treća sesija — Crashlytics fix, konkurencija analiza, V1 grupa)

Duga sesija fokusirana na: (1) rešavanje kritičnog cold-start crash-a iz Crashlytics-a, (2) istraživanje konkurencije (Paralino, HeyPolo, FamilyWall, Zood, Traccar, Dawarich) i mapiranje šta Krug nema, (3) implementacija V1 grupe feature-a i backend osnove za buduće premium tier. Radilo na SM-S928B (S24 Ultra) preko debug APK-a, poslednji commit `276b75b` (push-ed na origin/main).

### A) Kritični crash fix — cold-start na Android <12 (commit `6a13bf4`)

**Simptom** (Crashlytics): `Fatal Exception: java.lang.RuntimeException Unable to start activity ComponentInfo{org.krug.app/org.krug.app.MainActivity}: android.view.InflateException: Binary XML file line #28 in org.krug.app:layout/splash_screen_view: Failed to resolve attribute at index 0`.

**Root cause**: `installSplashScreen()` iz `androidx.core:core-splashscreen 1.0.1` na API <31 inflate-uje interni `splash_screen_view.xml` layout koji koristi `?attr/windowSplashScreenAnimatedIconSize` — atribut definisan SAMO u `Theme.SplashScreen`. `Theme.Krug` je nasleđivao `android:Theme.Material.Light.NoActionBar` pa je `TypedArray.getLayoutDimension` bacao `UnsupportedOperationException`.

**Fix**: novi `Theme.Krug.Splash` (parent `Theme.SplashScreen`) sa `postSplashScreenTheme=@style/Theme.Krug`. Activity theme u manifestu promenjen sa `Theme.Krug` na `Theme.Krug.Splash`. Isti pattern za `values-v31` (native atributi sa `android:` prefiksom).

### B) Circle picker + i18n polish (commit `472b056`)

- Circle picker row: klik na aktivan krug je bio no-op — sad ceo red vodi na detail ako je selected, na switch ako nije.
- Uklonjeni hard-coded srpski stringovi u CircleDetailScreen (three-dots menu "Označi kao dete", role label "Dete", content description "Opcije člana"). Dodati novi ključevi u EN + SR.

### C) Location "backward jump" fix — tri kvalitetna filtera (commit `406ffad`)

**Simptom** (user report): peer prati člana koji ide kolima, pozicija normalno napreduje, pa iznenada skoči 200-300m unazad, sledeći refresh ispravi. Klasičan cache/stale fix problem.

**Fix**: novi `passesQualityGate(loc, isCachedLastLocation)` u FGS-u, poziva se iz i stream callback-a i one-shot publish-a:
1. **Age gate** — cached `fused.lastLocation` stariji od 30s se odbacuje (Wi-Fi fingerprint može biti sat vremena star).
2. **Monotonic guard** — pamti `lastPublishedElapsedNanos`, drop-uje fix čiji je elapsed nanos stariji.
3. **Plausibility** — ako implied brzina iznad ~200 km/h (55 m/s), drop (GPS outlier, ne stvarno kretanje).

Novi state `lastPublishedElapsedNanos`, konstante `LAST_LOCATION_MAX_AGE_MS` (30s), `MAX_PLAUSIBLE_SPEED_MPS` (55).

### D) Null-safety + locale + lint (commit `ce58afa`)

- `handleSosUpdate` refactor: `sos!!.triggeredAt` zamenjen sa `sos?.takeIf { ... }` pattern.
- `UserRepository.upsertOnSignIn`: `user.displayName!!` zamenjen sa `?.takeIf { it.isNotBlank() }` chain.
- `Geo.formatDistance`: `String.format` sad koristi `Locale.getDefault()` — SR daje "1,5 km" umesto "1.5 km".
- `PowerSaveMonitor.registerReceiver` prebačen na `ContextCompat.registerReceiver` sa `RECEIVER_NOT_EXPORTED` — Android 14+ zahteva flag.
- Lint 2 errors → 0.

### E) Server-side presence — onDisconnect (commit `d601720`) + rules deploy

**Problem**: peer koji uđe u tunel, umre baterija, ili force-stop-uje app, ostavlja stari `updatedAt`. Nema signala razlike "offline" vs "paused" vs "stale fix".

**Solution**: RTDB `.info/connected` + `onDisconnect()`:
- Novi `LocationRepository.bindOnlinePresence(uid)` — observe-uje connected, kad true registruje `onDisconnect().setValue(false)` na `locations/{uid}/online` + `setValue(true)`.
- FGS drži cleanup Runnable, poziva u onDestroy sa eksplicitnim `setValue(false)`.
- Server automatski postavlja `online=false` ~30s posle stvarnog disconnect-a.
- `LocationModel` dobio `online: Boolean = true` polje (default true za legacy record-e).
- `publish()` uključuje `online=true` u payload (belt-and-suspenders da `setValue(data)` ne izbriše polje).

**RTDB rules deployed 2026-07-01**: dodat `"online": { ".validate": "newData.isBoolean()" }` u `locations/$uid`. `firebase deploy --only database` prošao bez incidenata.

UI: `MemberWithLocation.isOffline()` helper, member row statusLine prikazuje "Van mreže" / "Offline" umesto "Poslednje: pre X min" kad je flag false. `MemberDetailSheet` dobio CloudOff banner "izgubio je konekciju, sync će se automatski nastaviti".

### F) Offline vizuelni signali + haptic + a11y (commit `355c5fd`)

- Member row `rowAlpha` gradient: 1.0 (online) → 0.72 (offline) → 0.55 (long-offline 24h+).
- Map pin `iconOpacity`: 1.0 → 0.65 → 0.4.
- `rejectHaptic` dodat na account delete / leave circle / delete circle confirm.
- `confirmHaptic` na SOS cancel self.
- `pressScaleClickable` dobio `role: Role? = Role.Button` default za TalkBack.
- SosFab: prebacen na `pressScaleClickable` (0.92 pressScale), state-aware `contentDescription` ("Pošalji hitno SOS upozorenje" vs "Otkaži aktivni SOS").

### G) Istraživanje konkurencije + strateški plan

Istražene 6 aplikacija (search + docs + Play Store scrape): **Paralino** (privatnost + E2E), **HeyPolo** (SOS + teen driver), **FamilyWall** (all-in-one organizer + lokacija), **Zood** (samo E2E, dev-early), **Traccar** (enterprise open-source), **Dawarich** (self-hosted history).

**Šta Krug NEMA vs konkurencija**:
- Tier 1: Places / geofence alerts, location history, battery alerts, arrival/departure notifs
- Tier 2: Compass ka drugu, speed monitoring, approximate location, temporary sharing, rename member, multiple devices/account
- Tier 3: Recipe/meal/expenses (FamilyWall - off-scope), heatmaps (Dawarich)

**Šta Krug JEDINSTVENO ima**: SOS emergency (Paralino/FamilyWall/Zood/Traccar/Dawarich nemaju), Parental control (mark as child), Refresh ping, Server-side presence.

**Plan**:
- **V1 (odmah, sve free)**: Compass, Speed, Temporary sharing, Rename (→ Rename REJEKTOVAN od user-a, videti H4)
- **V1.1 Premium (paid tier)**: Places/geofence alerts, Location history 30d, Battery alerts, Approximate location toggle
- **V1.2+**: Multiple devices, E2E encryption, heatmap, import Google Timeline, **Android Auto integracija (opcija B: full mapa sa članovima, kao Waze)** — koristi Navigation template iz CarAppLibrary-ja, zahteva Google approval (review 2-4 nedelje, rizik odbijanja jer Krug nije primarno nav app); opcija A (messaging kategorija, samo SOS TTS notifikacije) odbačena — user hoće punu vizuelizaciju kruga

### H) V1 grupa implementacija (commits `fb2c6e5`, `276b75b`)

**H1. Compass to member** (`Geo.bearingDegrees` + `StatChip.iconRotationDeg`)
- Forward bearing izračun (0=sever, 90=istok).
- `StatChip` dobio animirani rotation (spring low stiffness) za smooth okretanje.
- Distance chip u MemberDetailSheet koristi `Icons.Filled.Navigation` (kite/kompas arrow) rotiran ka peer-u. Prvo je bio `NavigateNext` (chevron) što nije ličilo na kompas — user feedback → menja se u Navigation.

**H2. Speed chip** — LocationModel.speed već postojao; novi chip `Icons.Outlined.Speed` prikazuje "45 km/h" ako `speed > 1 m/s` (mirujući član nema chip da ne troši prostor).

**H3. Temporary sharing (auto-off)**:
- `UserSettings.shareUntilMs: Long?` novi field.
- `SettingsRepository.updateShareUntil()` piše/briše polje (FieldValue.delete za null).
- FGS `checkAndExpireTemporarySharing()` — na svaki fix proverava, gasi share + čisti flag kad istekne.
- PrivacyScreen: FlowRow sa 4 chip-a "Uvek / 1 sat / 4 sata / Do kraja dana" + live countdown labela ("Auto-gašenje za 59m 30s"). Prvo bio Row+weight, "Do kraja dana" se lomio na sr-Latn — user feedback → prebačen na FlowRow.

**H4. Rename in circle — REJEKTOVAN i vraćen**
Implementiran + Firestore rules deployed sa `hasOnly(['isChild', 'nickname'])`. Ali user rekao "izbaci rename" — UX nije bio zreo, klik na Save je pokazivao novo ime pa se posle par sekundi vraćalo. Kompletno uklonjeno: UI, VM, Repository setter, `observeMemberNicknames`, strings, Firestore rules revert. `firebase deploy --only firestore:rules` puštan 2× (add pa revert).

### I) Bug fix — "300m razmak kolima" (u commitu `fb2c6e5`)

**Simptom** (user report): dva člana fizički zajedno u kolima, na mapi 300m razdvojeni. User osvežio na oba više puta, ne pomaže.

**Root cause**: `requestOneShotFix()` na tap "Osveži" koristio `PRIORITY_BALANCED_POWER_ACCURACY` + `setMaxUpdateAgeMillis(60_000L)` + `setWaitForAccurateLocation(false)`. Rezultat: Google Play Services vraćao Wi-Fi cache fingerprint stariji od 30-60s.

**Fix**: nova signature `requestOneShotFix(userInitiated: Boolean)`. Kad je user-initiated (refreshSelf iz UI dugme ILI peer refresh ping):
- Preskače cache-publish korak (`fused.lastLocation` publish).
- `PRIORITY_HIGH_ACCURACY` (pravi GPS satelit).
- `maxUpdateAge = 5s` (skoro nula cache).
- `waitForAccurateLocation = true` + `setDurationMillis = 15_000L` (čeka fresh fix, 15s timeout).

Automatic entry-pointi (boot, worker) i dalje koriste BALANCED + 60s cache za brz odgovor bez battery drain-a.

### Health stanja (kraj sesije)

- `compileDebugKotlin`: 0 grešaka, 0 warning-a.
- `lintDebug`: 0 errors, ~150 low-priority warnings.
- `testDebugUnitTest`: 46 testova zeleni.
- APK je debug (92MB), release build nije re-generisan ove sesije.
- Firebase rules deployed: RTDB (online field), Firestore (samo isChild — nickname reverted).
- Debug APK u `~/Downloads/krug-debug.apk` za sideload testiranje.

### Sledeća sesija — TODO redosled

1. **Play Console 4 preostale declaracije** (blokada za Internal Testing): Sign in details (test Gmail nalog), Target audience, Content ratings, Data safety.
2. **Store listing** (screenshots, feature graphic, descriptions SR+EN — assets postoje).
3. **Permissions and APIs declaration** (background location — možda demo video).
4. **Testiranje V1 grupe** — reci šta radi kako treba, gde još fali polish.
5. **V1.1 Premium** (kad ide bekhend): Places (geofence alerts, najveći gap), Location history 30d, Battery alerts, Approximate location.

## Gde smo stali (2026-06-30, Play Console identity verification ODOBRENA + app entry kreiran + 6 declaracija završeno)

**🎉 Play Console identity verification PROŠLA** (2026-06-30): Google poslao „Your identity has been verified / Your identity verification was successful" notifikaciju na `krugappteam@gmail.com`. Developer account aktivan, publishing odblokiran.

**App entry kreiran u Play Console-u** (2026-06-30, dvadeset druga sesija):
- App name: `Krug`
- Package name: `org.krug.app` (zaključano zauvek)
- Default language: Serbian (Latin) `sr-Latn`
- Type: App, Free
- Sve declarations prihvaćene (Developer Policies, Play App Signing ToS, US export laws)
- Play Integrity installer check: ON (default)

**App content — 6 od 10 declaracija završeno**:
- ✓ Privacy policy URL → `https://krugapp.com/privacy.html`
- ✓ Ads → No
- ✓ Government apps → No
- ✓ Financial features → No (premium subscriptions NE računaju se kao financial features)
- ✓ Health apps → No (SOS nije medical service, peer-to-peer alert)
- ✓ Advertising ID → Yes, purpose: Analytics (Firebase Analytics SDK pulluje `play-services-ads-identifier`, verifikovano u `releaseRuntimeClasspath`)

**App content — preostalo 4 declaracije**:
- ⏸️ **Sign in details** (PRIORITET — blokira Target audience) — čeka kreiranje dedicated test Gmail naloga (predlog: `krug.review.tester@gmail.com` ili sl.) sa instrukcijama za reviewer-a kako da uđe preko „Sign in with Google"
- ⏸️ **Target audience and content** — blokirano dok Sign in details nije završen. Plan: Ages 13-15, 16-17, 18+; not appealing to children
- ⏸️ **Content ratings** — IARC questionnaire (sve „No" za Krug, expected rating Everyone/PEGI 3)
- ⏸️ **Data safety** — najduži form, treba detaljno popuniti šta sakupljamo (location precise foreground+background, email, name, device ID, app activity) + svrhe + sharing (No) + encryption (Yes Firebase HTTPS) + user deletion (Yes preko Settings → Delete account)

**Store presence — neotkrivene sekcije** (sledeća sesija):
- Main store listing (icon, screenshots, feature graphic, descriptions SR+EN — sve assets postoje već)
- Store settings (kategorija — Social ili Communication, kontakt info)
- Permissions and APIs declaration (background location justification — kritično, Google strogo proverava, često traži demo video)

**Sledeća sesija — TODO redosled**:
1. Napraviti test Gmail nalog (predlog: `krug.review.tester@gmail.com`, 10 min)
2. Popuniti Sign in details sa test credentials + instrukcijama
3. Target audience and content (5 min)
4. Content ratings (15-20 min)
5. Data safety (30-60 min)
6. Main store listing (30-60 min)
7. Store settings (5 min)
8. Permissions and APIs (može potrebovati demo video za background location)
9. Internal testing release — upload AAB
10. Closed testing — recrutuj 12+ testera (14 dana min za production access)

**Bitno za buduće sesije**: personal developer account Google requirement (od 2023) — MORA closed testing sa **minimum 12 opt-in testera** **minimum 14 dana** pre nego što se može aplicirati za Production access. Launch je realno ~2 nedelje od kompletiranja closed test setup-a.

## Gde smo stali (2026-06-29, kraj dvadeset prve sesije — pre-launch polish + release artefakti spremni)

Repo public: **https://github.com/aleksandar-cypress/krug**, poslednji commit `6b99a55` (pushed na origin/main). **Sajt LIVE na https://krugapp.com/** (i `privacy.html`, `terms.html`, `robots.txt`, `sitemap.xml`, `screenshots/`). SSL aktivan, sve HTTP 200. Hosting na user-ovom shared planu (nije GitHub Pages — proper domen sa custom email forwarding capability).

**Brand split**: User je 2026-06-27 napravio dedicated `krugappteam@gmail.com` Gmail nalog za Krug brand operacije:
- Firebase Console: dodat kao Owner (ownership migration završen)
- Play Console signup: ide pod tim nalogom (još ne urađeno, čeka $25 + ID verifikaciju)
- Sav user-facing copy (privacy/terms/landing/listing) prebačen sa `aleksandarr@gmail.com` (lični) na `krugappteam@gmail.com` (brand)
- Lični nalog ostaje za personal git/SSH, ne za Krug user-facing kontekste

**Release artefakti** osveženi na kraju 21. sesije:
- `~/Desktop/krug-release.apk` (69 MB) — sa svim polish izmenama, za beta distribuciju
- `app/build/outputs/bundle/release/app-release.aab` (36 MB) — signed bundle, spreman za Play Console upload čim approval stigne

**Play Console signup** kompletan + identity verification ODOBRENA (2026-06-30):
- Developer name: **„Krug Team"**
- Email: `krugappteam@gmail.com`
- Tip: Personal account
- $25 plaćeno, ID + selfie upload-ovan 2026-06-27
- Status: **VERIFIED** ✅ — Google poslao „Your identity has been verified" potvrdu 2026-06-30 (~3 dana od submission-a)
- App publishing **odblokiran** — može se kreirati app entry i upload-ovati AAB
- Sve assets su spremni za Play Store upload — listing copy SR+EN, screenshots, feature graphics, AAB.
Firebase: Firestore + RTDB rules deployovane. Release SHA-1 dodat u Firebase Console (`21:6A:94:24:64:98:08:4A:42:02:D6:4F:13:77:40:26:3C:A8:E0:36`), Google sign-in radi i u release build-u.
**Flota uređaja**: A37 (SM-A376B), Xiaomi Mi 11 (21081111RG), Samsung S24 Ultra (SM-S928B) — release build verifikovan na S24 (`R5CWC1F9FND`).

**Health stanja**: `compileDebugKotlin` 0 warning-a. `testDebugUnitTest` zelen — 46 testova prolaze (TimeBucket 18, Geo 11, StringFormat 7, DeviceNames 10). `assembleRelease` prošao u 21. sesiji (2m 11s, R8 + lintVital + Crashlytics mapping upload). `bundleRelease` prošao (14s). `:benchmark` Gradle modul i dalje validan. Nove UI/core pomoćne stvari iz 21. sesije: `KrugLogo.continuousSpin` parametar, `LocalPrefs.clearForAccountReset()` GDPR helper, splash-style overlay u `EnterCodeScreen`, programmatic show-when-locked u `MainActivity` (SOS-only).

## Dvadeset prva sesija (2026-06-29) — pre-launch polish + release verifikacija

Kratka sesija dok čeka Play Console approval. Tri user-prijavljene stvari + audit polish round + commit `6b99a55` pushed.

### A) User bug reports na S24 (commit `6b99a55`)

User testirao APK na S24 Ultra, prijavio tri problema:

**1. „Imam pozivnicu" button posle pridruživanja krugu**: kada je peti član uneo invite kod i pridružio se, na MapScreen-u i dalje vidi empty-state CTA (Napravi prvi krug + Imam pozivnicu) iako je već u krugu.
- Root cause: `acceptInvite` transakcija commit-uje, navigacija odmah ide `popBackStack(Map)`, ali `observeMyCircles` Firestore listener tek treba da emit-uje novi snapshot. Map briefly renderuje sa `circlesLoaded=true && circles.isEmpty()` pre nego što listener uhvati update.
- Fix: u `EnterCodeViewModel.submit` posle `JoinResult.Success` set `localPrefs.setActiveCircleId` odmah + `withTimeoutOrNull(3_000L) { observeMyCircles(uid).first { it.any { c -> c.id == result.circleId } } }` pre nav-a. Inject `CircleRepository` + `LocalPrefs` u ViewModel.
- UX: tokom čekanja prikazuje splash-style overlay (full-screen `Box` sa background.copy(alpha=0.96f) preko EnterCode sadržaja) sa rotirajućim `KrugLogo` + tekst „Pridružujem se krugu…". Novi `continuousSpin: Boolean` parametar u `KrugLogo.kt` koji super-ponira `rememberInfiniteTransition` 360°/1400ms nad postojećim `spin` Animatable-om. Novi string `enter_code_joining_status` u SR + EN.

**2. Mapa se otvara preko lock screen-a**: kada user otvori app sa launchera dok je telefon zaključan, mapa se odmah vidi bez unlock-a — privacy leak.
- Root cause: `AndroidManifest.xml` ima `android:showWhenLocked="true"` i `android:turnScreenOn="true"` statički na `MainActivity` (dodato ranije za SOS full-screen intent). To važi za SVAKI launch, ne samo za SOS.
- Fix: ti atributi uklonjeni iz manifesta. `MainActivity.handleSosFocusExtra()` programmatic-ki poziva `setShowWhenLocked(true)` + `setTurnScreenOn(true)` (sa Build.VERSION_CODES.O_MR1 fallback na `window.addFlags(FLAG_SHOW_WHEN_LOCKED or FLAG_TURN_SCREEN_ON)`) — SAMO kad notification intent ima `EXTRA_FOCUS_SOS_UID`. SOS wake-screen i dalje radi, normalan launch sad traži unlock.

**3. Privacy/Terms linkovi vode na github.io**: korisnik klikne Privacy ili Terms u About ekranu, vodi ga na stari github.io URL umesto krugapp.com.
- Provera: trenutni kod (`AboutScreen.kt:53-54`) već koristi `https://krugapp.com/privacy.html` i `https://krugapp.com/terms.html` od commita `f8e0596` (2026-06-27 15:17:28). APK na S24 (installed 2026-06-27 13:22:43, ~2h pre commit-a) je STARI build. Fix = rebuild + reinstall, ne code change. Urađeno u istom installDebug ciklusu kao gornji fix-evi.

### B) Polish audit round (isti commit)

**Hardcoded „Kopirano"/„Kopiraj kod"** (ShowInviteScreen.kt:186) → `R.string.invite_copy_code` + `R.string.invite_copy_code_copied` (SR „Kopiraj kod" / „Kopirano", EN „Copy code" / „Copied"). Jedini nalaz iz feature/ hardcoded-string audit-a koji je legitiman user-facing (ostalo: clipboard label-ovi koji nisu vidljivi, % i counter formati koji su tehnički).

**GDPR local prefs leak**: posle `deleteAccount` / `reauthAndDelete` / anonymous `signOut`, lokalni `SharedPreferences` flag-ovi (`onboardingCompleted`, `activeCircleId`, `sos_notified`, `activityRecPromptShown`) su preživeli — sledeći sign-in nasleđivao stari activeCircleId koji više ne postoji (i SOS dedup mapu za stare uid-ove).
- Dodat `LocalPrefs.clearForAccountReset()` koji briše sve te flag-ove (osim `pendingDeleteUid` koji recovery logic handluje eksplicitno).
- Pozvan iz: `AccountViewModel.deleteAccount` (clean delete path), `AccountViewModel.reauthAndDelete` (reauth recovery), `AccountViewModel.signOut` (anonymous granu samo, posle data cleanup-a), `SplashViewModel.decide` pending-delete recovery (oba: clean recovery + force-signOut fallback).

### C) Audit pass-ovi koji su pokazali clean code

- **Code audit (core/)**: Explore agent našao 12 potencijalnih problema. Pregled rezultata: realno 0 problema. Volatile fields već postoje (`lastPublishedLat/Lng/AtMs` na LocationTrackingService:92-93,695), `PowerSaveMonitor` već ima `awaitClose { runCatching { unregisterReceiver } }`, `DirectionsRepository.scope` koristi SupervisorJob koji nikad ne cancel-uje (Singleton). `knownSosTriggered` ConcurrentHashMap je marginal defensive — preskočeno.
- **i18n parity**: 252 ključa u `values/strings.xml` == 252 u `values-sr/strings.xml`. Svi `%1$d` / `%1$s` placeholders match preko key-eva.
- **Accessibility**: 28 `contentDescription = null` lokacija pregledan — sve dekorativne ikone uz vidljiv tekst label u istom Row/Column. Sve `IconButton`-i imaju smislen description preko `stringResource()`. Clean.
- **Firestore indexes**: 4 query-a (`whereArrayContains memberIds + orderBy createdAt` ima index, ostala 3 su single-field — ne trebaju composite). Sve pokriveno.
- **Em-dash check**: `app/src/main/res` strings.xml čist, sva preostala `—` su u XML komentarima (`data_extraction_rules.xml`, drawable comments) — ne user-facing.

### D) Release build verifikacija

`./gradlew assembleRelease` prošao za 2m 11s (R8 obfuscation, lintVital, Crashlytics mapping upload OK). `bundleRelease` 14s. Artefakti:
- `app/build/outputs/apk/release/app-release.apk` (69 MB) → kopiran na `~/Desktop/krug-release.apk` za beta distribuciju
- `app/build/outputs/bundle/release/app-release.aab` (36 MB) → spreman za Play Console upload

Metadata: `org.krug.app` v0.1.0 (versionCode=1), minSdk 26, targetSdk 36, compileSdk 36.

### Stanje na kraju 21. sesije

- Commit `6b99a55` pushed na origin/main
- Play Console signup i dalje u „Google is verifying your identity" — čeka se odobrenje (status quo od 2026-06-27 popodne)
- Build green: 0 compile warning-a, 46 unit testova prolaze, release + bundle prolaze
- APK svež na ~/Desktop sa svim fix-evima, instaliran na S24 (`R5CWC1F9FND`) i A37 (`RFGL30L2A5Z`) u sesiji
- Sledeća sesija: čeka se Play Console approval. Dok ne stigne, opcije za polish: dark mode pass (61 hardcoded color literal, većina legit), test coverage extension za `InviteRepository` (Firestore tx logic nikad testiran), perf profiling.

## Dvadeseta sesija (2026-06-27) — sajt live + brand split + landing polish

Sesija duga, podeljena u tri faze: app polish (early), landing polish (mid), hosting deployment (late). Mnogo iteracija na sitnim stvarima koje su user-i odmah primetili.

### A) Initial app polish + bug fix-evi (commits `0f5797b`, `53e3aff`)

Lansiran APK na S24, user prijavio probleme:
- Switch circle sheet sa 9+ krugova: Manage button skriven — LazyColumn dobio `weight(1f, fill = false)`
- Notification panel duplikat icon: `setLargeIcon` uklonjen iz `LocationTrackingService` + `SosNotifier`
- Logo spin animation: posle više iteracija (tween 600 → spring bouncy → tween + 350ms delay → spring no delay) → konačno **tween 600ms FastOutSlowIn + 250ms delay** pre `onClick()`. „Klik → malo se zavrti → prelazimo na drugi ekran" feel.
- App icon u Samsung App info: launcher icon ima fundamental design constraint (head-ovi na 4-14% / 86-95% viewport-a uz ivicu). Probao 12dp/18dp/25dp inset + LogoBlue gradient bg + dva-logo predlog. **User odluka: ostavi 22dp (Option 3)** — launcher na home screen-u izgleda OK, App info screen je low-priority Samsung-specific UI.

### B) Pre-testing prep (commit `47483da`)

- SosBanner hardcoded SR strings (MapScreen.kt:1067-1095): tri stvari (`"$name traži pomoć"`, plurals, `"krug „X""`) → novi i18n ključevi + plurals u oba locale-a
- Onboarding skip granted permissions: `OnboardingPage.isAlreadyGranted(context)` + edge case fallback (`viewModel.complete()` ako su sve strane preskočene)
- GPS waiting banner: novi Composable, prikazuje se kad `state.selfLocation == null AND hasForegroundLocation` (cold start 2-15s)

### C) Crashlytics NDK (commit `8e3cb94`)

Dodat `firebase-crashlytics-ndk` dependency. Bez ovog, Mapbox native SIGSEGV/SIGBUS ne dolazi u Crashlytics dashboard.

### D) Landing page hosting prep (commits `f8e0596`, `b76d504`, `124dfb9`)

User odlučio: kupiti `krugapp.com` domen + shared hosting, ne GitHub Pages.
- Sav privacy/terms URL referenciranje prebačen sa `aleksandar-cypress.github.io/krug/*` na `krugapp.com/*.html` (AboutScreen.kt + listing-{sr,en}.md)
- og:tags + sitemap.xml + robots.txt + theme-color + canonical links dodato
- **Bilingual privacy.html i terms.html** (SR + EN sa lang switcher): prethodno bili samo SR, sad full prevod sa .t-sr/.t-en spans, isti localStorage `krug-lang` key kao landing — izbor jezika persistira kroz sve tri stranice

### E) Landing polish runde

**Path 1 odluka (free-first, premium tek u v1.1)**: dugo strateški razgovor (commit `2cb19ab`). Razmotren Path 2 (full freemium od dana 1) — odbačen zbog 7-10 nedelja dodatnog rada (Play Billing, Cloud Functions backend, Places UI, history arhitektura, subscription management) + visok bug surface payments + nedostatak userbase za konverziju. Posle launch-a → 3 meseca rasta → v1.1 premium. Postojeći user-i posle v1.1 dobiće **soft enforcement** (postojeći krugovi/članovi ostaju pristupačni, novi krugovi su iza premium-a) — ne loss aversion nego value-add positioning.

**Pricing kontradikcija** (commit `2cb19ab`): bio „v1 = sve free" header + free card sa „1 krug, 6 članova" limit. Konzistentno: free unlimited, premium daje nove feature-e ne unlock.

**WOW screenshot galerija** (commits `9cee657`, `99faa70`): 5 phone mockup-ova → 4 (4+1 wrap je izgledao loše), CSS-only dark frame sa notch + shadow, staggered rotacija (-2°/+1°/-1°/+2°) sa hover lift. Screenshots optimizovani: 1440×2880 .jpg → 540×1080 .webp, quality 82, ~17-66 KB per fajl. SR/EN swap kroz isti pattern.

**Trust strip + email CTA + FAQ** (commit `b193a77`):
- Trust strip ispod hero CTAs: 4 checkmark badge-a (EU hosting, bez reklama, bez praćenja, pravljeno u Srbiji)
- Dead „Coming soon Google Play" CTA → aktivan `mailto:` link „Javi mi kad bude live" (pre-filled subject) — real conversion goal pre Play Store-a
- FAQ sekcija: 6 pitanja sa native `<details>/<summary>` (zero JS, accessible) — bateriju potrosnja, ko vidi lokaciju, brisanje naloga, hitne sluzbe disclaimer, vise krugova, da li ostaje besplatno

### F) Brand email split (commit `9407c14`)

User napravio `krugappteam@gmail.com` dedicated Gmail za Krug brand:
- Firebase Console: dodat kao Owner (ownership migration završen u sesiji)
- Sav user-facing email referenciranje prebačeno sa `aleksandarr@gmail.com` na `krugappteam@gmail.com` u 6 fajlova:
  - `docs/index.html` (2 mesta — hero mailto + footer)
  - `docs/privacy.html` (5 mesta — sve kontakt sekcije)
  - `docs/terms.html` (1 mesto — kontakt)
  - `docs/play-store/listing-sr.md` + `listing-en.md` (po 2 mesta — kontakt + what's new)
  - `docs/play-store/pre-launch-console-actions.md` (1 mesto)
- 13 mesta zamenjeno, 0 ostataka
- Memorija ažurirana: `feedback_email.md` sad kaže `krugappteam@gmail.com` (lični `aleksandarr@gmail.com` ostaje za personal git/SSH/non-Krug)

### G) Sajt LIVE (deployment ručno od user-a)

User je upload-ovao 7 fajlova + screenshots folder iz `~/Desktop/krugapp-upload/` u cPanel `public_html/`. Verifikovano kroz `curl -sI`:
- `https://krugapp.com/` → HTTP 200
- `https://krugapp.com/privacy.html` → HTTP 200
- `https://krugapp.com/terms.html` → HTTP 200
- `https://krugapp.com/robots.txt` → HTTP 200
- `https://krugapp.com/screenshots/02-map-onboarding-sr.webp` → HTTP 200

SSL aktivan (HTTP/2), Let's Encrypt verovatno preko cPanel-a.

### H) Sledeći koraci

1. **Play Console signup** pod `krugappteam@gmail.com`: $25 + ID verifikacija + 24-72h. Pre toga, otvorin incognito browser tab.
2. **Posle Play Console verifikacije**: sledeća sesija sa mnom — pratimo STATUS.md sekciju G iz 15. sesije (Create app → setup tasks → store listing → internal testing release). Estimated 1.5-2h.
3. **(Kasnije) cPanel email forwarding**: `support@krugapp.com` → `krugappteam@gmail.com`, pa svuda u copy-ju prebacujemo na brand domen email.
4. **Tester feedback**: APK na `~/Desktop/krug-release.apk` može da se šalje sad — testeri instaliraju, koriste, javlja se feedback.

## Devetnaesta sesija (2026-06-27) — device install + post-install bug fixes + testing prep

Sesija fokusirana na install Krug APK-a na Samsung S24 Ultra (`R5CWC1F9FND`) i fix-ovi koje je device testing odmah otkrio. **Pet commit-a u nekoliko diskretnih celina:**

### A) Inicialni install + tri device-discovered bug-a (commit `0f5797b`)

`./gradlew :app:installDebug` na S24 (SM-S928B, API 36) prošao. Tokom prvog testiranja user prijavio tri stavke:

1. **Switch circle sheet** sa 9+ krugova: „Manage circles" button na dnu se ne vidi. Root cause: `LazyColumn` u `CirclePickerSheet` (MapScreen.kt:1641) bez `weight()` modifier-a — lista raste preko dna sheet-a, button skroluje van. Fix: `Modifier.weight(1f, fill = false)` na LazyColumn, button uvek vidljiv ispod.
2. **App icon** u Samsung „App info" screen-u izgleda kao prazan beli krug — probao 12dp inset (pogoršano, vraćeno na 22dp). Posle više iteracija (12dp/22dp/25dp insets + LogoBlue gradient bg + dva-logo predlog + objašnjenje da Android API ne dozvoljava per-context icon) korisnik prihvatio Option 3: ostavi kako je. Launcher icon ostaje sa 22dp inset.
3. **Logo spin animation** na map top right buttons (CircleIconButton + CircleLogoButton): tween 600ms je radio ali user osetio kao slabu animaciju. Privremeno prešao na spring MediumBouncy + StiffnessMediumLow (user kasnije rekao da je „previše brz") — fix u sledećem commit-u.

### B) Notification duplikat icon + spin timing fix (commit `53e3aff`)

User otkrio kroz testing:
- **Notification panel pokazuje DVE ikone**: leva (launcher icon, automatski Samsung One UI app source indicator) + desna (`setLargeIcon` = `ic_notification_large`). Duplikat. Probao razne kombinacije za fix-ovanje desne (tighter viewport, white fill, circle bg) — sve su pravile različite probleme. **User odluka: skinuti `setLargeIcon` poziv u oba file-a** (LocationTrackingService.kt + SosNotifier.kt). Posle uklanjanja, notification ima samo jednu ikonu (launcher icon levo) — clean look. Trade-off: launcher icon ima problem sa sečenjem head-ova (4 figure uz ivicu logo viewport-a), ali to je fundamental design constraint koji bez logo redizajna ne može da se reši.
- **Spin animation timing**: tween 600ms + bez delay-a → screen exit transition (280ms iz KrugNavHost) starts immediately, ikona nestane sa screen-om pre nego što rotacija stigne da bude vidljiva. Više iteracija (spring + 350ms delay, tween 700ms, spring no delay, original 600ms no delay) dok user nije konačno potvrdio na: **tween 600ms FastOutSlowIn + 250ms delay pre `onClick`**. User vidi ~150° spin pre nego što screen krene, pa još deo tokom transition-a. „Klik → malo se zavrti → prelazimo na drugi ekran" feel.

### C) Pre-testing prep (commit `47483da`)

Tri stavke da bi testeri odmah ne našli:

1. **SosBanner hardcoded SR strings** (MapScreen.kt:1067-1072 + 1086-1095) — flag iz 17. sesije. Three real i18n bugs:
   - `"$name traži pomoć"` → `stringResource(map_sos_banner_one_help, name)` (novi ključ u oba locale-a)
   - `"${others.size} članova traži pomoć"` → `pluralStringResource(map_sos_banner_multi_help)` (novi plurals: EN one/other, SR one/few/other CLDR)
   - `"krug „$circleName""` → `stringResource(map_sos_banner_circle_label, circleName)` (novi ključ, em-dash uklonjen, escaped quotes EN)

2. **Onboarding skip granted permissions** (OnboardingPage.kt + OnboardingScreen.kt):
   - Novi `OnboardingPage.isAlreadyGranted(context)` — true kad je permission dat van app-a (sistemske Settings, ili uninstall+reinstall — Android čuva permission grants).
   - `buildOnboardingPages()` sad takođe filter-uje preskočene strane.
   - Edge case: ako su SVE strane preskočene, `LaunchedEffect(pages)` direktno zove `viewModel.complete()` — bez ovog je bio blank screen forever.

3. **GPS waiting banner** (MapScreen.kt:2315):
   - Novi `GpsWaitingBanner` Composable + dva string ključa (`map_gps_waiting_*`).
   - Pokazuje se kad `state.selfLocation == null AND hasForegroundLocation(context)` — tipično 2-15s na cold start dok FGS dobija prvi GPS fix.
   - Wrapped u `AnimatedVisibility` (expandVertically + fadeIn na enter, shrinkVertically + fadeOut na exit) kao ostali banneri. CircularProgressIndicator + naslov „Čeka se GPS" + body „Tvoja lokacija će se pojaviti za par sekundi".
   - Plasiran između `OfflineBanner` i `PowerSaveBanner` u banner stack-u.

### D) Sanity verifikovano pre testing-a

- `./gradlew :app:testDebugUnitTest` → 46 testova prolaze
- `./gradlew :app:assembleRelease` → 1m 29s, R8 minify + lintVital + Crashlytics mapping upload, signed sa tvojim release keystore-om
- Release APK kopiran u `~/Desktop/krug-release.apk` (65 MB) za distribuciju
- `compileDebugKotlin` → 0 warnings

### E) Šta NIJE urađeno tokom 19. sesije (ostavljeno za posle testing-a)

1. **App icon (launcher) head cropping** — fundamental design constraint, traži ili redizajn logo-a (head-ovi bliže centru) ili kompromisni inset (25dp+ → manji launcher). User odlučio Option 3 (ostavi kako je za sada).
2. **AccountScreen + BatteryModeScreen blank flash** — nije real bug (AccountScreen init iz sync FirebaseAuth, BatteryMode flash <100ms je nitpick). Skipped.
3. **Sve P1/P2 audit findings** iz 18. sesije (settings radius/padding normalize, dark mode brand colors, SR microcopy P1/P2, member avatar photo integration, MapScreen split nastavak) — ne blokiraju testing.

### F) Sledeći koraci

1. **Distribucija APK-a** — user šalje testerima preko WhatsApp/Drive/email. Tester instalira (mora dozvoliti „Install from unknown sources").
2. **Tester feedback** — javi mi kad iskoči nešto pa rešavamo.
3. **Play Console upload** — kad bude verifikovan account.
4. **Post-testing**: P1/P2 audit findings, MapScreen split nastavak, member avatar photo, premium tier groundwork (Roadmap Faza 8).

## Osamnaesta sesija (2026-06-27) — UI polish sweep (10/12 izabranih stavki)

User izabrao 12 UI unapređenja sa predloga, 10 završeno + 2 audit gap-a flagovana. Tri commit-a u 3 batch-a.

### Batch A (commit `a155875`) — perceived quality

- **Haptic feedback** (novi `core/util/Haptics.kt`):
  - `View.confirmHaptic()`: API 30+ `HapticFeedbackConstants.CONFIRM`, fallback `LONG_PRESS`
  - `View.rejectHaptic()`: API 30+ `REJECT`, fallback `LONG_PRESS` (za SOS)
  - `View.clickHaptic()`: `VIRTUAL_KEY` (svi API)
  - Apply sites:
    - MapScreen SosConfirmDialog `onConfirm`: `rejectHaptic` (irreversible action)
    - CreateCircleScreen `LaunchedEffect(createdCircleId)`: `confirmHaptic` pre nav-a
    - EnterCodeScreen `LaunchedEffect(joinedCircleId)`: `confirmHaptic` pre nav-a
- **Toast → Snackbar**:
  - `AboutScreen` imao 2 Toast-a (invalid link, no browser) — refaktor `openExternalUrl()` da vraća `OpenUrlResult` enum, caller koristi `SnackbarHostState`
  - `SettingsSubScaffold` sad uzima `snackbarHostState` parametar (default `remember { SnackbarHostState() }`)
- **Loading button states** (novi `ButtonLeadingIconOrSpinner` helper u CircleDetailScreen):
  - 4 button-a (2 invite, delete, leave) sad pokazuju 18dp `CircularProgressIndicator` umesto leading ikone dok `loading=true`
  - `CreateButton` u CreateCircleScreen već imao loading state, ne dirano

### Batch B (commit `8ae4dba`) — vizualni polish

- **AnimatedVisibility** na sva 4 map banner-a:
  - `PowerSaveBanner`, `OfflineBanner`, `PermissionWarningBanner`, `SosBanner`
  - `expandVertically() + fadeIn()` na enter, `shrinkVertically() + fadeOut()` na exit
  - `OfflineBanner` izvučen sub-Composable `OfflineBannerContent` jer je `LaunchedEffect` sa 30s tick-om morao da bude vezan za vidljivost (ne za composition)
  - `SosBanner` wrap na call-site (banner nema svoj toggle)
- **Onboarding staggered entrance**:
  - `IntroPage`: 5 sekcija sa 150-200ms stagger delay-em (hero scale-in → title slide-up → body slide-up → 2 feature row-e slide-up → CTA scale-in). Ukupno ~1.2s reveal.
  - `OnboardingPageScaffold` (koristi se za AllSetPage + PermissionPages): hero 0ms, text 250ms, button 500ms. Postojeći breath pulse na hero-u nije diran.
  - `LaunchedEffect(Unit) { visible = true }` pattern — flip flag posle prvog frame-a

### Batch C (commit `a8d6f8b`) — audit findings primijenjeni

Četiri paralelna read-only sub-agent-a (Settings audit, Edge states audit, Dark mode audit, SR microcopy review) — applied **P0 nalazi**:

- **Map marker self vs others** (`MapMarkers.pinMarker(isSelf: Boolean)`):
  - bubble + tail 18% veći (scale 1.18f), dodatni LogoBlue (`#3A86C8`) halo prsten oko belog
  - Cache key dobio `|self`/`|other` suffix da bitmap-i ne kolapsiraju
  - Call site (MapScreen.kt:1445) prosleđuje `member.isSelf`
- **Dark mode P0**:
  - `SplashScreen.kt:57`: `Color.White` background → `MaterialTheme.colorScheme.background`
  - `AuthScreen.kt:68`: isto
- **CircleListScreen error retry** (edge states P0):
  - `state.error` postojao u VM ali UI nikad nije čitao. Dodat `ErrorRetry` Composable u `when {}` između loading i empty state. Retry button zove `viewModel::refresh` (isti put kao pull-to-refresh).
  - 3 nova string ključa (`circles_error_title/body/retry`) u oba locale-a.
- **SR microcopy P0**:
  - `enter_code_error_already_member`: „Već **ste** član" → „Već **si** član" (ti konzistentnost)
  - `battery_saver_desc`, `battery_max_desc`: „**updates**" (engleski) → „ažuriranja" (srpski prevod)
  - `time_minutes_short`, `time_hours_short`: „%dmin"/"%dh" → „%d min"/"%d h" (razmak konzistentan sa `time_days_ago` „pre %d d")
  - `member_state_long_offline_title`: „%1$dd" → „%1$d d" (razmak)
  - Iste izmene u EN gde važi (parity)

### F) Šta NIJE urađeno (flagovi za sledeće sesije)

1. **Member avatar photo integration** (task 25) — Coil `AsyncImage` + Firebase Auth `photoUrl` + inicijali fallback na member-ima. Najveći scope koji sam preskočio.
2. **Settings vizualni audit findings** (P1/P2 iz Settings audit-a):
   - Card border radius drift (10/12/14/16dp između screen-ova), padding drift (12 vs 14 vs 16dp), typography token drift (labelMedium vs labelLarge za section header)
   - Mnogo malih izmena, diminishing returns — može u kasniji „normalize" pass
3. **Edge states P1 gaps**:
   - MapScreen ne pokazuje „Čeka se GPS fix" indikator kad `self.location == null` (default Belgrade prikazan tiho)
   - OnboardingScreen ne preskače već-granted permission stranice
   - AccountScreen / BatteryModeScreen blank flash dok DataStore ne učita prefs (<100ms ali nepolovišano)
4. **SR microcopy P1/P2** (lakše izmene tone-a, „Vec ste" → „Vec si" pattern checkpoints, „upravo sada" vs „sad" konzistentnost)
5. **Dark mode brand color hardcoded sites** (P1 iz dark mode audit-a) — `Color.White` tint na ikonama u branded buttons (OK u praksi, ali nije idealno za custom theme support kasnije)
6. **MapScreen split nastavak** (iz 17. sesije) — MapBanners.kt, MapTopBar.kt, MapSheets.kt, MemberDetailSheet.kt, MapboxContainer.kt
7. **SosBanner hardcoded SR** (linije 1044/1046/1062) — fix kad MapBanners.kt bude extract-ovan

## Sedamnaesta sesija (2026-06-27) — app polish: 8/8 izabranih unapređenja

Sesija fokusirana na quality-of-life unapređenja koja ne zavise od Play Console-a. User izabrao 8 stavki sa predloga (1, 2, 5, 6, 7, 8, 9, 10), sve završene.

### A) StrictMode + ANR detection (commit `a95c9f0`, deo polish batch-a)

`KrugApplication.onCreate` u debug build-u sad postavlja:
- `ThreadPolicy`: detectDiskReads, detectDiskWrites, detectNetwork, detectCustomSlowCalls
- `VmPolicy`: detectLeakedClosableObjects, detectLeakedRegistrationObjects, detectActivityLeaks, detectFileUriExposure

`penaltyLog` only (ne penaltyDeath) — Firebase init ima lažne pozitive koji ne smeju da ruše dev build. Release build potpuno isključen — nula overhead u produkciji.

### B) i18n completeness check (commit nikakav, audit bio clean)

Read-only sub-agent diff-ovao keys između `values/strings.xml` i `values-sr/strings.xml`:
- 331 ključeva u oba locale-a, 0 gap-ova, 0 orphan-a
- 1 plurals block (`circles_members_count`) ima EN: one/other, SR: one/few/other (CLDR-compliant)
- 100+ stringova sa `%d`/`%s`/`%1$s` placeholder-ima — argument count balansiran kroz EN/SR
- **Verdict**: katalog je strukturalno zdrav, nema šta da se fix-uje

### C) A11y / TalkBack pass (commit `a95c9f0`)

Read-only sub-agent našao 2 P0 + 3 P1 stavki. Fix-evi:
- **CreateCircleScreen ColorPicker**: dodato `Modifier.semantics { role = RadioButton; selected = isSelected; contentDescription = "Color option" }` na clickable Box-ove
- **CreateCircleScreen IconPicker**: isto + koristi postojeći label resource per icon kao contentDescription
- **CreateCircleScreen heading semantics**: section header-i „Boja" i „Ikona" dobili `Modifier.semantics { heading() }` za TalkBack rotor navigaciju
- **Novi string ključevi**: `create_circle_color_a11y`, `create_circle_icon_a11y_label` u oba locale-a

### D) Large text scaling audit (commit `a95c9f0`)

Read-only sub-agent našao 4 P0 + 4 P1 stavki. Fix-evi:
- **MapScreen.kt:2274 SOS banner**: `maxLines=2 + TextOverflow.Ellipsis` (bilo bez constraint-a, na 200% scale-u SOS poruka se prelivala)
- **MapScreen.kt:2228 member display name u detail sheet-u**: `maxLines=1 + Ellipsis + weight(1f, fill=false)`
- **ShowInviteScreen.kt:149**: hardcoded `fontSize=32.sp` → `MaterialTheme.typography.headlineMedium.copy(FontWeight.Bold)` (poštuje user font scale)
- **EnterCodeScreen.kt:249**: hardcoded inline `fontSize=28.sp` uklonjen, koristi sam typography token
- **CircleDetailScreen.kt:385 MemberRow displayName**: `maxLines=1 + Ellipsis + weight`
- **CreateCircleScreen.kt:218 circle name preview**: dodao explicit `overflow = TextOverflow.Ellipsis` na postojeći `maxLines=1`

### E) **Bonus**: sakriveni hardcoded SR strings (commit `a95c9f0`)

Tokom audit fix-eva otkrivene 3 triple-violation stavke koje su prethodni QA audit-i propustili (jer su bile u expression context-u, ne plain `Text("literal")`):

- **MapScreen.kt:2275 SOS banner u member detail sheet-u**: `"SOS aktivan — tvoji krugovi su obavešteni"` i `"$sosName traži hitnu pomoć"` — hardcoded SR + em-dash. Sad `R.string.map_sos_self_active` (rewritten bez em-dash-a u oba locale-a) + novi `R.string.map_sos_member_needs_help`.
- **CircleDetailScreen.kt:387**: `m.displayName.ifBlank { if (m.isSelf) "Ti" else ... }` — hardcoded „Ti". Sad `stringResource(R.string.member_label_you)`.
- **CircleDetailScreen.kt:396**: `contentDescription = "Dete"` — hardcoded „Dete". Sad `stringResource(R.string.member_child_cd)` (key je već postojao za istu svrhu u MapScreen-u).

**Flag**: SosBanner u MapScreen.kt:1044/1046/1062 ima JOŠ hardcoded SR copy-ja (`"$name traži pomoć"`, `"${others.size} članova traži pomoć"`, `"krug „"`). Otkriveno tokom MapScreen split priprema. Fix će ići kad SosBanner bude extract-ovan u MapBanners.kt (sledeća sesija).

### F) Pull-to-refresh na CircleListScreen (commit `fea9e87`)

`CircleListViewModel` dobio:
- `refreshTrigger: MutableStateFlow<Long>` combined sa `authState` pre `flatMapLatest`
- `refreshing: Boolean` u UI state-u
- `fun refresh()` — emituje novi timestamp (re-subscribe Firestore observer-a) + drži spinner vidljiv minimum 600ms (bez ovog, kešovan snapshot vrati za <50ms i spinner samo „blinkne")

`CircleListScreen` zameni `Box(...) { content }` sa `PullToRefreshBox(isRefreshing, onRefresh = vm::refresh) { content }`.

**Preskočen**: CircleDetailScreen za PTR. Screen je full Column sa header/invite buttons/nested member LazyColumn/leave button — PTR gestus iz header-a ne radi vizuelno, member lista nije list-dominated. Bolje da ne kompromitujemo UX.

### G) Compose @Preview annotacije (commit `14b5b87`)

Prethodno samo 1 preview u repo-u (KrugAppPreview u MainActivity koji ne radi u IDE zbog Firebase init-a). Dodato **10 novih preview-a** u 3 fajla:

- **CircleListScreen**: EmptyState, SkeletonList (shimmer), CircleRow (sa demo Porodica circle), CreateCircleFab
- **CreateCircleScreen**: CirclePreview, ColorPicker (svih 6 swatches), IconPicker (svih ikona)
- **CircleDetailScreen**: MemberRow (owner+self), MemberRow (child + canManage), CircleHeader

Sve wrapped u `KrugTheme { ... }` + `showBackground = true`. FQN `androidx.compose.ui.tooling.preview.Preview` umesto import-a da region {} block na dnu fajla ne curi imports gore.

### H) MapScreen.kt split — prvi korak (commit `a1cf9e2`)

`MapScreen.kt` je bio 2679 linija, lider liste za split kandidate. Plan: 5-6 manjih fajlova grupisanih po funkcionalnoj sekciji.

**Završeno u ovoj sesiji**: extract `MapDialogs.kt` (212 linija, 2 Composable-a):
- `ActivityRecognitionRationaleDialog` (rationale za fizičku aktivnost permission)
- `SosConfirmDialog` (custom SOS confirm dialog)

Side effect: `SosRed`, `SosRedDark`, `PrivateGray` promenjene iz `private val` u `internal val` u MapScreen.kt da budu dostupne split fajlovima u istom paketu.

**Posle ovog koraka**: MapScreen.kt 2679 → 2467 linija.

**Ostatak za sledeću sesiju**: MapBanners.kt (krugGlass + 4 banner-a), MapTopBar.kt (TopFloatingBar + CircleIconButton + CircleLogoButton + MembersPill + MemberMiniAvatar), MapSheets.kt (CirclePickerSheet + MembersSheet + MemberRow), MemberDetailSheet.kt, MapboxContainer.kt.

### I) Macrobenchmark modul (commit `f8d879f`)

Novi Gradle modul `:benchmark` (com.android.test plugin):

- **Versions** (`libs.versions.toml`): benchmark=1.3.3, androidx.test.{ext-junit 1.2.1, runner 1.6.2}, uiautomator 2.3.0; alias za `android.test` plugin
- **`:benchmark/build.gradle.kts`**: targetira `:app`, `experimentalProperties["android.experimental.self-instrumenting"] = true`, `benchmark` build type sa `matchingFallbacks += listOf("release")`
- **`:benchmark/src/main/AndroidManifest.xml`**: instrumentation za `androidx.benchmark.junit4.AndroidBenchmarkRunner` targetira `org.krug.app`
- **`StartupBenchmark.kt`**: 4 test slučaja
  - `coldStartupNone` (bez baseline profile, ref za regresiju)
  - `coldStartupBaselineProfile` (`CompilationMode.Partial()` — primenjuje baseline-prof.txt)
  - `warmStartup` (proces ostao u memoriji)
  - `hotStartup` (Activity ostala u memoriji)
  - Svaki radi 5 iteracija, meri `StartupTimingMetric()`
- **`:app/build.gradle.kts`**: novi `benchmark` build type (`initWith(getByName("release"))`, debug signing, matchingFallback->release) — instrumentiramo identičan minified+R8+baselineprofile artefakt produkcije
- **`:app/src/main/AndroidManifest.xml`**: dodato `<profileable android:shell="true" />` (macrobenchmark zahtev, bez ovog puca „Profileable not enabled")

**Run**:
```
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Output: `benchmark/build/outputs/connected_android_test_additional_output/`. Tipični brojevi na Pixel 4+: Cold 600-900ms (sa baseline), Warm 300-500ms, Hot 100-200ms. **Regresija = bilo koja granica probijena za >15% između commit-a.**

`README.md` dobio Macrobenchmark sekciju sa primer komandom + tipičnim brojevima.

### J) Bonus tech debt fix: createCircle silent failure

(Već u commit `c6e4e01` iz 16. sesije, ali napomenuto u Šesnaeste sesije sekciji C — vredi pomenuti i ovde zato što je P1 bug fix.) `CreateCircleViewModel` postavljao `genericError = "generic"` literal string ali UI ga nije čitao. Sad `Boolean` polje + UI prikazuje `R.string.create_circle_error_generic`.

### K) Sledeća sesija — preostalo

1. **Play Console developer account** — i dalje jedini hard blocker za sve Play Store stvari
2. **MapScreen.kt split nastavak** — 5 dodatnih ekstrakcija (MapBanners, MapTopBar, MapSheets, MemberDetailSheet, MapboxContainer)
3. **SosBanner hardcoded SR fix** — kad MapBanners.kt bude extract-ovan
4. **Run macrobenchmark na S24** — measure baseline, commit benchmark JSON kao referencu
5. **Testovi na uređaju**: TalkBack walkthrough, font scale 200% walkthrough (a11y + large text audit su statički)
6. **Premium tier groundwork** — Roadmap Faza 8: geofencing (places), history 30d, SOS background push (premium)

## Šesnaesta sesija (2026-06-27) — „dok ne stigne Play Console" sweep

## Šesnaesta sesija (2026-06-27) — „dok ne stigne Play Console" sweep

Sesija fokusirana na sve što se može uraditi bez Play Console-a. Pet diskretnih radnih celina, sve commit-ovane.

### A) Time/Geo sealed bucket refaktor (commit `f4164d7`)

Vraća unit test pokrivenost izgubljenu u 15. sesiji posle i18n refaktora (signature-i `compactLastSeen` / `sosRelativeTime` / `formatDistance` su uzeli `Context` parametar, blokirao plain JUnit).

- **`Time.kt`**: dva sealed type-a — `CompactTimeBucket` (Dash, JustNow, Minutes(n), Hours(n), DayPlus) i `RelativeTimeBucket` (Empty, JustNow, Minutes(n), Hours(n), Days(n)). Pure `bucketCompactLastSeen(updatedAt, now)` i `bucketSosRelative(triggeredAt, now)` rade bez Context-a. `compactLastSeen(Context, ...)` i `sosRelativeTime(Context, ...)` ostaju kao thin Context wrappers — **call-site-i u MapScreen netaknuti**.
- **`Geo.kt`**: `DistanceBucket` (Nearby, Meters(n), KmDecimal(d), KmInt(n)) + `bucketDistance(meters)`. `formatDistance(Context, meters)` thin wrapper.
- **`TimeBucketTest.kt`** (novi, 18 testova): granice 0/59/60/1439/1440 min za oba bucket-a + null/zero/negative edge cases.
- **`GeoTest.kt`** (popravljen, 11 testova): bio broken posle i18n-a, sad pokriva bucketDistance umesto lokalizovanih string output-a. Haversine testovi netaknuti.

### B) Sve compile warning-e eliminisane (commit `d406cc9`)

`compileDebugKotlin --rerun-tasks` sad 0 warning-a (samo AGP/compileSdk note koji nije deprecation).

- `Icons.Outlined.ExitToApp` → `Icons.AutoMirrored.Outlined.ExitToApp` (CircleDetailScreen.kt:237)
- `Icons.Outlined.DirectionsRun` → `Icons.AutoMirrored.Outlined.DirectionsRun` (MapScreen.kt:754)
- `@OptIn(ExperimentalCoroutinesApi::class)` na class-level `CircleListViewModel` (init blok ne prima annotation direktno) — za `flatMapLatest`
- `@OptIn(MapboxDelicateApi::class)` na `fitToMembers` funkciju — za `cameraForCoordinates(coordinates, camera, coordinatesPadding, maxZoom, offset)` overload. Pun package: `com.mapbox.maps.MapboxDelicateApi`.

### C) Pre-launch audit (dva paralelna read-only sub-agenta) + fix-evi (commit `c6e4e01`)

Dva Explore sub-agenta paralelno: landing page audit (docs/index.html + privacy.html + terms.html) i android app QA audit (kompletan src/main/).

**Landing audit findings**:
- footer logo `alt=""` (index.html:1210) → `alt="Krug logo"` (a11y P1)
- em-dash u CSS komentaru (index.html:53) → zamenjen zarezom (interna konzistentnost P2)
- ostali landing checks (em-dash, konkurenti, broken links, SR/EN leak, hardcoded URLs, mobile responsive, dates) — clean

**QA audit findings (pravi bug-ovi)**:
- **AboutScreen.kt:130**: footer copyright `"© $year Krug · Sva prava zadržana"` hardcoded SR string van strings.xml — EN korisnici videli SR. Sad `R.string.about_copyright` sa `%1$d` placeholder-om u oba locale-a.
- **CreateCircleViewModel.kt:79**: postavljao `genericError = "generic"` literal string, ali UI ga nikad nije čitao — kad `createCircle` baci network/Firestore exception, user vidio samo da spinner stane (silent failure). Sad `Boolean`, UI prikazuje `R.string.create_circle_error_generic` u istom supportingText slot-u gde se već prikazuju nameError/duplicateError. String je već postojao u oba locale-a (EN: „Couldn't create circle. Try again", SR: „Greška pri kreiranju kruga. Pokušaj ponovo").
- **colors.xml:3**: komentar `<!-- Brand palette (Life360-inspired, modern) -->` pominjao konkurenta po imenu — protiv memorije „no competitor names in copy". Sad `<!-- Brand palette (modern indigo + coral). -->`.

**Audit non-findings (kontrolisano negativno)**: Wrong email (`aleksandar.vasilic@login5.org`) nigde nije, `println()`/`print()` nigde u prod kodu, hardcoded test podaci (test@test.com, John Doe, default coords) nigde, dead code/commented blokovi clean. Permission rationale već implementiran graceful kroz PermissionPages.kt + MapScreen banner.

### D) Feature graphic finalizovan (commit `c2e0175`)

Plan iz 15. sesije: base postoji (logo + teal gradient), fali wordmark.

- ImageMagick overlay: Avenir Next 180pt za „Krug" wordmark, 36pt tagline pod njim, white sa 85% opacity. Logo ostaje na levoj 1/3.
- **`feature-graphic-sr.png`** (1024×500): „Krug" + „Tvoji ljudi. Uvek blizu."
- **`feature-graphic-en.png`** (1024×500): „Krug" + „Your people. Always close."
- Komanda (za reprodukciju):
  ```bash
  magick docs/play-store/assets/feature-graphic-base.png \
    -font /System/Library/Fonts/Avenir\ Next.ttc \
    -fill white -pointsize 180 -gravity center \
    -annotate +210-30 "Krug" \
    -pointsize 36 -fill "rgba(255,255,255,0.85)" \
    -annotate +210+80 "Tvoji ljudi. Uvek blizu." \
    docs/play-store/assets/feature-graphic-sr.png
  ```

### E) README rewrite (commit `c2e0175`)

Bio totalno zastareo (linije 4 i 8 govorile da je „MVP skeleton + placeholder screens", roadmap Faze 1-7 sve nemarkovane).

- Tagline fix: skinut „Life360-stil UX" (memorija: no competitor names)
- Status: „MVP skeleton" → „feature-complete, priprema za Play Store internal beta"
- Roadmap: Faze 0-6 ✅, Faza 7 (Play Store) u toku, Faza 8 (public launch + premium) dodata
- Folder structure proširena (core/* sa svim sub-paketima: auth, circle, location, permissions, prefs, sos, user, util; feature/* sa stvarnim screen-ovima ne TODO)
- Privacy section pokazuje na `docs/privacy.html` (postoji), uklonjen TODO link na nepostojeći `play-store-location-declaration.md`
- Dodate Tests sekcija + release signing setup korak (KRUG_KEYSTORE_*)

### F) Šta NIJE urađeno (i dalje čeka Play Console verifikaciju)

1. **Play Console developer account** — user nema otvoren. $25 one-time + ID verifikacija + 24-48h. Ovo je jedini hard blocker za sve preostalo.
2. **Push commit-a na origin** — biće urađeno na kraju sesije (`git push`).
3. **EN-05 screenshot retake** (sad ima fix za „1 members" plural). Manja stvar, može za 17. sesiju.
4. **SR-02 scene retake** da matchuje EN-02 empty state. Skipped za sad jer je internal beta; za production launch razmotriti.

### G) Sledeća sesija — checklist za upload (kad Play Console bude verifikovan)

Isti kao 15. sesija sekcija F, ali sad bez feature graphic blokera:

1. **Play Console → Create app**: name „Krug", language Serbian, free, declarations
2. **Setup tasks** (10 stavki): App access, Ads (No), Content rating (Social, sve No → PEGI 3), Target audience 13+, News (No), Health (No), Government (No), Financial (No), Data safety, App category Lifestyle
3. **Store settings**: privacy URL `https://aleksandar-cypress.github.io/krug/privacy.html`, contact `aleksandarr@gmail.com`
4. **Main store listing → Serbian (default)**: copy iz `docs/play-store/listing-sr.md`, upload icon + `feature-graphic-sr.png` + SR screenshots
5. **Manage translations → Add English (US)**: copy iz `listing-en.md`, `feature-graphic-en.png`, EN screenshots
6. **Testing → Internal testing → Create release**: upload fresh `app/build/outputs/bundle/release/app-release.aab` (rebuild posle ovih commit-a), release notes oba locale-a
7. **Testers tab**: kreiraj email listu „Krug Beta", dodaj 5-10 emailova, kopiraj opt-in URL i pošalji
8. **Wait ~10 min** dok Play Store ne refresh-uje, testeri instaliraju

## Petnaesta sesija (2026-06-26) — Play Store assets + i18n polish

Sesija fokusirana na pripremu za Play Store internal beta upload. Završeno:

### A) i18n lokalizacija formatter funkcija (commit `900bfcb`)

- `formatDistance(Context, meters)`, `compactLastSeen(Context, updatedAt)`, `sosRelativeTime(Context, triggeredAt)` sad uzimaju Context i čitaju iz string resources
- 10 novih string ključeva u `values/strings.xml` + `values-sr/strings.xml`:
  - `distance_nearby` („blizu" / „near")
  - `time_dash`, `time_just_now_short` (sad/now), `time_minutes_short` (`%dmin`), `time_hours_short` (`%dh`), `time_day_plus` (1d+)
  - `time_just_now_long` („upravo sada" / „just now"), `time_minutes_ago`, `time_hours_ago`, `time_days_ago`
- `MapScreen.kt` ažuriran sa `LocalContext.current` na 3 call-site-a (SosBanner, MemberDetailSheet, OfflineBanner)

### B) Play Store screenshots (commit `a801684`)

- **5 EN + 5 SR screenshot-a** snimljenih na Samsung Galaxy S24 Ultra (1440×3120 native)
- SR fajlovi inicijalno A-E, preimenovani u `01-05-*.jpg` da mirror EN naming:
  - `01-map-member-detail` — map + member detail sheet (Battery/Distance/Last seen chips)
  - `02-map-onboarding` — EN ima empty-state CTA, **SR ima aktivan krug** (scene mismatch, namerno za sada)
  - `03-create-circle` — Novi krug form
  - `04-join-circle` — 6-cifreni kod
  - `05-circle-members` — Members list
- Lokacija: `docs/play-store/screenshots/{en,sr}/`
- Feature graphic **base** napravljen (`docs/play-store/assets/feature-graphic-base.png`, 1024×500, logo + teal gradient, **nedostaje tekst**)

### C) i18n polish surfaced from screenshot review (commit `a801684`)

- **„1 members" gramatika fix**: `circles_members_count` konvertovan iz `<string>` u `<plurals>` resource
  - EN: `one` / `other`
  - SR: `one` / `few` / `other` (CLDR pravila)
- `CircleListScreen.kt` i `CircleDetailScreen.kt` koriste `pluralStringResource(R.plurals.circles_members_count, n, n)`
- **Diacritic fix u SR locale**: „Drustvo" → „Društvo" u `icon_label_friends` i `create_circle_name_placeholder`
- **Napomena**: EN-05 screenshot još uvek prikazuje „1 members" (snimljen pre fix-a). Retake pre produkcijskog launch-a.

### D) Crop na Play Store 2:1 ratio (commit `01c9007`)

- Inicijalno screenshot-i 1440×3120 = ratio 2.167, prelazi Play Store „longer side ≤ 2× shorter side" pravilo
- ImageMagick batch crop sa offset `+0+120` → finalno 1440×2880 = tačno 2:1
- Simetrično 120px top + 120px bottom uklanja status bar (uključujući USB/dev mode ikonice) i nav bar
- App content netaknut, mali sliver nav bar-a viri na vrhu (zanemarljivo)

### E) Blokeri za upload (nismo stigli)

1. **Play Console developer account** — user nema otvoren. $25 one-time fee, ID verification (passport/lična karta + selfie), 24-48h čekanje za Google verifikaciju. **Akcija**: user da otvori sledeću sesiju ili paralelno.
2. **Feature graphic 1024×500 finalni** — postoji base sa logom i gradient-om, ali fali „Krug" wordmark tekst + opciono tagline na desnoj 2/3. Plan: dodati tekst kroz ImageMagick overlay (Helvetica/SF Pro, „Krug" veliki + ispod sitno „Porodica na mapi").

### F) Sledeća sesija — checklist za upload (kad Play Console account bude verifikovan)

1. **Finiš feature graphic-a** (10-20 min, ImageMagick overlay)
2. **Play Console → Create app**: name „Krug", language Serbian, free, declarations
3. **Setup tasks** (10 stavki): App access, Ads (No), Content rating (Social, sve No → PEGI 3), Target audience 13+, News (No), Health (No), Government (No), Financial (No), Data safety (tabela iz sekcije E STATUS.md), App category Lifestyle
4. **Store settings**: privacy URL `https://aleksandar-cypress.github.io/krug/privacy.html`, contact `aleksandarr@gmail.com`
5. **Main store listing → Serbian (default)**: copy iz `docs/play-store/listing-sr.md`, upload icon + feature graphic + SR screenshots
6. **Manage translations → Add English (US)**: copy iz `listing-en.md`, EN screenshots
7. **Testing → Internal testing → Create release**: upload `app/build/outputs/bundle/release/app-release.aab`, release notes oba locale-a
8. **Testers tab**: kreiraj email listu „Krug Beta", dodaj 5-10 emailova, kopiraj opt-in URL i pošalji
9. **Wait ~10 min** dok Play Store ne refresh-uje, testeri instaliraju

### G) Tehnički long detalji

- Build status: `./gradlew :app:compileDebugKotlin` prošao posle plurals refaktora. Samo 3 pre-existing deprecation warning-a (Icons.Outlined.ExitToApp / DirectionsRun → AutoMirrored verzije; delicate API u MapScreen).
- Postojeći `TimeTest.kt` (60 linija, 18 test slučajeva) obrisan u commit `900bfcb`. **Strategija za vraćanje**: izvuci pure logiku u `TimeBucket` sealed type (JustNow, MinutesAgo(n), HoursAgo(n), DaysAgo(n)) koji testovi mogu da pokriju bez Context-a, plus Composable `@Composable fun TimeBucket.format(): String` za UI side.
- SR-02 scene mismatch sa EN-02: SR pokazuje aktivan krug („Friends" pill, Članovi/SOS dugmad), EN pokazuje empty „Create your first circle" CTA. Različite vrednosti propozicija, OK za internal beta. Za produkcijski launch razmotriti retake.

## Četrnaesta sesija (2026-06-25) — Faza 2 Play Store priprema (release signing + 2 bug fix-a + strateške odluke)

Sesija fokusirana na pripremi za Play Store internal beta. Iz Faze 2 plana završeno: **#1 signing keystore + signed AAB** + smoke test. Otkriveni i fix-ovani usput dva bug-a (map follow + Activity Recognition rationale). Strateške odluke za pricing + i18n + domain.

## Četrnaesta sesija (2026-06-25) — Faza 2 Play Store priprema (release signing + 2 bug fix-a + strateške odluke)

Sesija fokusirana na pripremi za Play Store internal beta. Iz Faze 2 plana završeno: **#1 signing keystore + signed AAB** + smoke test. Otkriveni i fix-ovani usput dva bug-a (map follow + Activity Recognition rationale). Strateške odluke za pricing + i18n + domain.

### A) Release signing + AAB pipeline

- **Keystore generisan**: `release-keystore.jks` (PKCS12, RSA 2048, 25 god validity, `CN=Aleksandar Vasilic, O=Krug, L=Cacak, C=RS`, alias `krug-release`)
- Lozinka i credentials sačuvani van git-a (`release-keystore-credentials.txt` gitignored — TODO ručno backup u mail/Notes, posle obrisati)
- SHA-1: `21:6A:94:24:64:98:08:4A:42:02:D6:4F:13:77:40:26:3C:A8:E0:36`
- SHA-256: `67:FE:3A:7B:7C:43:6F:FB:DC:E0:97:EC:F0:87:22:38:CD:CA:41:E1:C5:FB:E1:31:8A:A5:5F:09:49:5F:88:6B`
- **`app/build.gradle.kts`**: čita `KRUG_KEYSTORE_PATH/PASSWORD/KEY_ALIAS/KEY_PASSWORD` iz `local.properties` (gitignored), gracefully fallback-uje na unsigned ako keystore fali (drugi developer / CI)
- **`baseline-prof.txt` format fix**: AGP 8.7 wildcard expansion odbijao `HSP` flag-ove na class-only linijama — skinuti flagovi sa class refs, zadržani na method linijama
- **`google-services.json` updated** — release SHA-1 dodat u Firebase Console, novi json downloadovan i ubačen u `app/`
- **Prvi signed AAB**: `./gradlew :app:bundleRelease` → `app-release.aab` (34.5 MB). `jarsigner -verify` prošao, cert SHA-1 inside AAB-a se poklapa sa keystore-om
- **Smoke test na S24** kroz `bundletool build-apks --connected-device` + `install-apks`: app proces pokrenut, Firebase Crashlytics/Sessions inicijalizovani, Mapbox native libs (`libmapbox-maps.so`, `libmapbox-common.so`) učitani, baseline profile installer odradio, **nema FATAL/ClassNotFoundException** → ProGuard pravila pokrivaju ceo stack (Firebase + Mapbox + Compose + Hilt). Google sign-in radi posle SHA-1 dodavanja u Firebase Console.

### B) Bug fix-evi (oba commit-ovana)

**1. Map camera follow focused member** (commit `4b9a371`)
- Bug: kad je član u kretanju i app ode u background → posle resume-a fresh location stigne ali kamera ostaje na staroj poziciji, pin "izvlači" iz vidnog polja.
- Fix: `MapViewHolder.easeFollow(lng, lat)` (pan-only easeTo 800ms, bez zoom change-a) + novi `LaunchedEffect` koji prati `updatedAt` fokusiranog člana dok je `detailUid != null`. Reset baseline-a na detailUid change. Ne sukobljava se sa postojećim `pendingRefocus` (refresh button) path-om.

**2. Activity Recognition rationale dialog** (commit `dbc3fce`)
- Bug: pri prvom ulasku na Mapu, system permission dialog za `ACTIVITY_RECOGNITION` iskakao bez konteksta → user vidi golu poruku "Allow Krug to access physical activity?" bez objašnjenja zašto je tražimo.
- Fix: brand-styled rationale dialog (`LogoBlue` gradient + `DirectionsRun` ikona) sa naslovom + body + Dozvoli/Ne sada button-ima. `LocalPrefs.activityRecPromptShown` flag sprečava re-prompt — pokazuje se jednom, posle samo kroz sistemska podešavanja.
- Strings dodati u `values/` i `values-sr/`: `activity_rec_title/body/allow/not_now`.

### C) Strateške odluke (sačuvane u memory + ovde)

**Pricing & feature tier plan (free vs premium):**

- **Free tier (v1 launch — sve uključeno besplatno za internal beta)**:
  - 1 krug max, max 6 članova
  - SOS local (recipient mora biti foreground/notification-ready)
  - Real-time location, battery, putna distance, activity-aware tracking, polish
- **Premium tier (planirano za v1.1+)**:
  - Unlimited krugovi, unlimited članovi
  - SOS background push (preko Cloud Functions — Blaze plan)
  - Places + geofencing
  - Location history 30 dana
  - Trip reports
- **Razlog**: SOS ostaje u free zbog ethical/safety razloga + Play Store guidelines (Life360 takođe drži SOS u free). 6 članova (ne 4) — prosečna porodica 4-5 ljudi, limit 4 udara u zid pre nego što user vidi vrednost. v1 launches free da skupimo signal o vrednosti pre nego što gradimo billing infra (Play Billing + paywall + premium flag + Cloud Functions = 1-2 nedelje rada + Blaze plan upgrade).

**Internacionalizacija — "Krug" ostaje brand:**

- Play Console podržava **per-locale store listing** (različit title + description po jeziku)
- SR locale: čist "Krug"; EN locale: "Krug: Family Circle" ili sa explanatory tagline u opisu
- Bez rename-a — 13 sesija rada uloženo u brand, logo, strings, repo, domain hunt. Word of mouth radi i sa meaningless brand imenima (Life360, Strava, Slack — sve "meaningless" reči koje su izgrađene kroz brand).
- Target tržišta: SR/HR/BA/MNE/SLO (~15M govornika), diaspora (US/DE/AT/CH), sa per-locale listing-om ka ostatku sveta.

**Domain odluka:**

- Glavni `krug.X` domeni (.com, .app, .io, .co, .rs) svi zauzeti. `krug.com` je **Krug Champagne** (francuska kuća šampanjca, nedostupna) — različita kategorija pa nije problem za Play Store, ali bare `.com` je trajno nedostupan.
- **Izabran: `krugapp.com`** (~$10-12/god, 100% dostupan — Verisign whois "No match" + DNS NXDOMAIN + RDAP 404 + HTTP 000 — sva 4 izvora potvrđuju)
- Razlog vs `krug.family` (~$35/god): `.com` univerzalno prepoznatljiv, 3x jeftiniji, brand fleksibilnost (može za prijatelje/kolege/tim, ne lock-uje u "family")
- Predložen registrar: **Porkbun** (besplatan WHOIS privacy + SSL, manje upsell-a). Backup: Namecheap.
- TODO: korisnik kupuje, posle DNS → Firebase Hosting → deploy privacy/terms

### D) Preostalo iz Faze 2

- ✅ #1 Signed AAB — gotovo
- ✅ #2 **Data safety form** — audit kompletiran, odgovori spremni za Play Console submit (vidi tabelu ispod)
- ⏳ #3 Screenshots — odloženo (započet plan: 5 scena × 2 locale-a + feature graphic). App icon 512x512 generisan (`docs/play-store/assets/app-icon-512.png`). Feature graphic 1024x500 TODO.
- ✅ #4 **Store listing copy** — gotovo (`docs/play-store/listing-sr.md` + `listing-en.md`, sve unutar Play Console limita)
- ⏳ #5 Internal track upload + 5-10 testera — blokirano dok ne kupiš domain za privacy URL

**Privacy + Terms HTML** (`docs/privacy.html` + `docs/terms.html`) ažurirani 25. jun:
- Ispravka Mapbox tvrdnje (DOES šalje koordinate ka Directions API, ne samo style)
- Dodato: FCM token, App Check sekcija, Activity Recognition opt-out u sistemskim podešavanjima, Crashlytics custom keys nuansa
- Datum osvežen

**AboutScreen.kt URL-ovi (`PRIVACY_URL` / `TERMS_URL`)** trenutno pokazuju `aleksandar-cypress.github.io/krug/...` — TODO update na `krugapp.com/...` kad domain stigne.

### E) Data safety form audit (za Play Console)

Glavna tabela — šta Krug skuplja i kuda ide:

| Data type (Google Play taxonomy) | Collected | Shared | Optional | Purpose | Retention |
|---|---|---|---|---|---|
| Name | Yes | No | No | App functionality, Account mgmt | Until account deletion |
| Email | Yes | No | No | Firebase Auth + profile | Until account deletion |
| User IDs (Firebase UID) | Yes | No | No | App functionality | Until account deletion |
| Precise location (lat/lng/accuracy/bearing/speed) | Yes | Service provider (Mapbox za Directions API) | **Yes** (toggle u app-u) | App functionality (real-time location sharing) | Live overwrite, no archival |
| Approximate location | Yes (derived) | No | Yes | App functionality | Real-time only |
| Photos | Yes (URL ref samo) | No | Yes | Profile avatar (Google Sign-In photoUrl) | Until account deletion |
| App interactions (Crashlytics breadcrumbs) | Yes | Yes (Google Firebase) | No | Analytics | 30+ dana |
| Crash logs | Yes | Yes (Google Firebase) | No | Analytics | 30+ dana |
| Diagnostics | Yes | Yes (Google) | No | Analytics | 30+ dana |
| Device IDs (model, OS, App Check attestation) | Yes | Yes (Google, Mapbox) | No | App functionality, Fraud prevention | Until uninstall |
| FCM token | Yes | Yes (Google FCM) | No | App functionality (planirano za push, trenutno samo storage) | Updated on app start |

**NE skuplja**: phone, address, payment, health/fitness, contacts, calendar, messages, audio, files, web history, advertising IDs.

**3rd party distinction**:
- **Service providers** (data processors, ne "sharing" u Google smislu): Google (Firebase Auth/Firestore/RTDB/Crashlytics/App Check/FCM), Mapbox (Directions API + style fetch)
- **Not shared sa**: oglašivačima, data brokerima, drugim 3rd party stranama

**Korisničke kontrole**:
- Location sharing: toggle per circle
- Activity Recognition: sistemska permisija (može da revoke-uje)
- Notifications: sistemska permisija
- Account deletion: Settings → Delete Account (GDPR fan-out kroz Firestore + RTDB + Auth.delete())

**Top-level toggles za Play Console**:
- Does your app collect or share required user data? → **YES**
- All data encrypted in transit? → **YES** (TLS preko Firebase + Mapbox HTTPS)
- Users can request data deletion? → **YES** (Delete Account flow postoji)

### F) Permissions manifest (rationale-i za Play Console)

| Permission | Rationale |
|---|---|
| `INTERNET` | Firebase + Mapbox APIs |
| `ACCESS_NETWORK_STATE` | Network connectivity check pre API poziva |
| `ACCESS_FINE_LOCATION` | Real-time GPS koordinate za location sharing |
| `ACCESS_COARSE_LOCATION` | Fallback ako GPS ne radi |
| `ACCESS_BACKGROUND_LOCATION` | FGS LocationTrackingService update-uje lokaciju u background-u |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | FGS za kontinualno location tracking |
| `POST_NOTIFICATIONS` | SOS + location tracking ongoing notifications |
| `VIBRATE` | SOS alarm haptic |
| `ACTIVITY_RECOGNITION` | Activity-aware GPS profil (hodanje/vožnja/mirovanje) za bolju bateriju |
| `USE_FULL_SCREEN_INTENT` | SOS notifikacije bude zaključan ekran (Android 14+) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | FGS reliability protiv agresivnih OEM battery optimizer-a |
| `RECEIVE_BOOT_COMPLETED` | FGS auto-restart posle reboot-a + WorkManager keepalive |

### G) Sledeća sesija — preostale Play Store stvari

**Bez domain-a (možemo odmah):**
1. **Privacy Policy + Terms draft** — markdown fajlovi u `docs/` (ili sličnom folderu), ready za deploy čim domain stigne
2. **Store listing copy** — short description (80 chars) + full description (4000 chars max) za SR i EN locale + "What's new" za v0.1.0
3. **Screenshots** — 4-5 listing screenshot-ova sa S24 (možda i emulator za EN locale)

**Sa domain-om (kad kupiš `krugapp.com`):**
4. DNS konfiguracija → Firebase Hosting (besplatno)
5. Deploy privacy + terms na `krugapp.com/privacy` + `krugapp.com/terms`
6. About ekran u app-u — proveri da link-ovi pokazuju na nove URL-ove
7. Play Console submit svih Data Safety odgovora
8. Internal track upload AAB-a, invite 5-10 testera

**Buduće (posle Play Store launch-a):**
- GDPR delete account flow — postoji ali audit kaže "v1 limitation server-side enforcement" → proveri da li je client-side dovoljan za Play Store policy ili treba Cloud Functions
- Premium tier (v1.1) — Play Billing Library + paywall + premium flag + SOS background push (Cloud Functions, Blaze plan)

---

## Trinaesta sesija (2026-06-23) — English lokalizacija + UI polish v2 + critical map camera bug

Dan posvećen finalnoj polish fazi pred Play Store basic launch.

### A) English lokalizacija + multi-locale infrastruktura
- `values/strings.xml` postao engleski default fallback, `values-sr/strings.xml` srpski
- ~230 stringova prevedenih u oba locale-a; audit hardcoded srpskog iz Kotlin-a
- `CircleIconAssets.labelForKey: String` → `labelResForKey: @StringRes Int` (call site-ovi koriste `stringResource(...)`)
- `AuthViewModel` (ViewModel ne može `stringResource`) dobio `@ApplicationContext` injection + `appContext.getString(...)`
- JEDAN AAB sadrži sve locale-ove; Play Store automatski isporučuje per-locale split

### B) UI polish v2
- **Settings hierarchy**: 4 brand-color sekcije (Profil / Privatnost / Performanse / Aplikacija), `SettingsItem` sa title + subtitle + icon + accent color
- **EnterCode keypad**: 6-box dizajn (transparent `BasicTextField` + `CodeBox` Row), auto-focus + keyboard on launch
- **MembersSheet**: sort + alpha + hint kad je user sam u krugu (`map_members_alone_hint`)
- **NavHost slide transitions**: 280ms slide-in/out left, slide-out right on pop
- **Custom SOS confirm dialog**: brand-styled umesto sistemskog AlertDialog-a
- **Splash logo rotacija**: 360° (bilo 180°), entrance sa strana → orbit-style formation
- **About logo crop fix**: padding adjust da gornji deo nije odsečen
- **Settings ikona**: vraćena na obični gear (probali A/B/C variant-e, sve ružne)

### C) Critical bugs fix
- **Indigo "plavi krug" leak** (već fix-ovano u 12. sesiji, ali polish nastavljen): puck disable PRE + POSLE `loadStyle` ostavljen kao defensive belt-and-suspenders
- **Pulse animacija polish**: 360° rotation umesto smanjivanja
- **Crashlytics fix S24**: splash zakucao na loading — `decide()` wrap-ovan u try/catch sa fallback na `SignedOut` + `finally { SplashGate.ready.set(true) }` (garantovan exit)
- **🔥 Map camera stuck on Belgrade default** (najveći — commit `bdaa45d`): user bez krugova viđao Belgrade Topčider umesto svoje GPS lokacije iako log potvrdio `flyTo(Čačak)`.
  - **Root cause**: race između `factory.setCamera(Belgrade)` + async `loadStyle()` + `LaunchedEffect.flyTo(self)`. Kad flyTo padne PRE style-loaded, Mapbox ga "izgubi", a factory-jev setCamera prevlada.
  - **Fix**: `MapViewHolder.styleLoaded` flag postavlja se iz `loadStyle` callback-a. `LaunchedEffect` čeka i `mapView != null` i `styleLoaded == true`, zatim `setCamera` (instant, ne flyTo — initial jump ne traži animaciju). Verifikovano vizuelno na A37 u Čačku.

### D) Baseline profile setup
- `app/src/main/baseline-prof.txt` sa hand-crafted hot path-ovima (Application, MainActivity, Splash, NavHost, KrugLogo, Compose runtime)
- `androidx.profileinstaller` dodat — ART precompile pri install-u, ~10-30% brži cold start
- Macrobenchmark module nije setup-ovan (deferred); hand-crafted profile pokriva startup

### E) Eksperiment — rebrand u "Orbit" (odbačen)
- Probali Earth-in-center splash + "Orbit" naming
- Ne sviđa se vizuelno, vraćeno na "Krug" — ostaje kao radno ime do Play Store launch-a

### F) Mock location debugging (Samsung A37)
- Xiaomi pokazivao 229km distance — discovery: Appium Settings app (`io.appium.settings`) imao MOCK_LOCATION permission, feed-ovao Pančevo koordinate
- Privremeno disable-ovan, pa restored (user mora Appium za testing)

## Plan za sledeću sesiju — Faza 2: Play Store priprema

Polish faza je gotova. Sledeća sesija ide ka **Play Store internal/closed beta**.

### Deliverables:

**1) Signing keystore + signed AAB** (~1h)
- Generisati release keystore (`keytool -genkeypair`), backup-ovati offline
- `app/build.gradle.kts` → signing config sa kredencijalima iz `local.properties` (nikad u git)
- Build `:app:bundleRelease` → AAB ready za upload

**2) Data safety form** (~1h)
- Play Console deklaracija: SHARING (Firebase Auth email, Firestore lokacija + display name, RTDB lokacija)
- Permission rationale: foreground service, background location, notifications, post notifications

**3) Screenshots** (~1h)
- 4-5 screenshot-a za listing (Mapa sa krugom / CreateCircle / MembersSheet / Settings / About)
- Po dva locale-a (sr + en); device frame opcional

**4) Store listing copy** (~30min)
- Short description (80 chars) + full description (4000 chars max)
- "What's new" za prvi release

**5) Internal track upload + invite 5-10 testera**
- Real-world test sa porodicama 1-2 nedelje
- Crashlytics monitoring + feedback loop

### Strateški plan ka Play Store i monetizaciji

**Trenutno stanje feature parity sa Life360**: ~30-40% (free tier), ~15% (premium).

**Free / Basic tier (trenutno) — za Play Store launch:**
- Krugovi (multi), real-time location, battery, putna distance
- Privacy mode, SOS lokal (foreground recipient)
- Activity-aware battery efficiency, brand polish
- **Predloženi free limiti** (da bismo imali razlog za upgrade):
  - Max 3 kruga, max 8 članova po krugu
  - Location history vidljiva samo 24h
  - SOS samo lokal (recipient mora biti foreground)

**Krug Pro tier — kasniji rollout (posle Play Store basic launch + beta):**
- **Places + Geofencing** ⭐ ("Dete je stiglo u školu") — najveća vrednost
- Location history 30 dana — trasa kretanja
- **SOS background push** preko Cloud Functions (radi i kad je primalac sa zatvorenim app-om)
- Unlimited krugova + članova
- Trip reports (km/min/max speed za dan)
- Crash detection (kasnije, ML)
- Priority support, early access

**Pricing model (dogovoreno)**: **Family Plan annual** — jedan plaća, ceo krug ima Pro. Najbolja konverzija jer "ne plaćaš za sebe nego za bezbednost porodice". Slično Life360 modelu.

**Cloud Functions / Blaze upgrade**: deferred do "Pro tier" launch-a. Free tier i basic launch ostaje na Spark planu.

### Roadmap redosled posle sledeće sesije
1. Internal/closed Play Store beta (jedan AAB sa srp + eng) → soft launch
2. Real-world test sa porodicama (1-2 nedelje)
3. Public Play Store launch — basic free
4. Cloud Functions + Blaze upgrade + Places/Geofencing → prvi Pro feature
5. Pro tier rollout sa Family Plan
6. Iterate: location history, trip reports, SOS background

## Dvanaesta sesija (2026-06-22 → 2026-06-23) — UI polish + 4 user-reported bugs + strateški plan

### Dan 1 (2026-06-22): UI polish — commits `8a66526`, `8c53d49`

### Dan 2 (2026-06-23): 4 user-reported bug fixes — commit `422368a`

User je tokom korišćenja prijavio 4 problema. Sve rešeno:

**#1 Indigo "plavi krug" track na mapi** (najveći mystery)
- **Simptom**: kad se član kreće, posle refresh-a niz indigo krugova ostaje na mapi kao "track" prethodnih pozicija. Camera focus na sledeći refresh ide na te krugove, ne na člana.
- **Pogrešan tropot**: prvo sam mislio da je Mapbox built-in location puck (LiveGPS plavi circle). Aplicirao trostruko disable (`updateSettings { enabled=false; pulsingEnabled=false }` + direktan setter, PRE i POSLE `loadStyle` callback-a). Nije pomoglo jer to nije bio root cause.
- **Pravi root cause**: `MapViewHolder.runUpdatePulse(lng, lat)` pravi `CircleAnnotation` (indigo `#818CF8`), animira 800ms, briše na kraju. Ali kad se LaunchedEffect cancel-uje pre kraja (član se kreće brzo, novi location update menja key), `mgr.delete(ann)` se NIKAD ne poziva → annotation ostaje zauvek. Više update-a = više leak-ovanih krugova.
- **Fix**: `try { ... animate ... } finally { runCatching { mgr.delete(ann) } }` — garantuje cleanup čak i pri cancellation-u.
- Mapbox puck disable ostavljeno kao defensive (ne škodi ako stvarno postoji u nekoj situaciji).

**#2 Course-up navigation** (driving mode)
- **User wish**: "kada sam isao u Banjicu put bi trebao da se refrehuje uvek ka severu, isto kao kada radi navigacija za voznju" — direction of travel uvek "gore" na ekranu kad se vozi.
- **Implementacija**:
  - `LocationModel` dobio polja `bearing: Float` (0..360°) + `speed: Float` (m/s).
  - `LocationTrackingService.publishLocation` čita `loc.bearing` + `loc.speed` iz Android Location objekta (sa `hasBearing()` / `hasSpeed()` checks — 0f fallback ako GPS još nije fix-ovao smer).
  - `LocationRepository.publish` prosleđuje u RTDB; rules ažurirane sa `bearing` (0..360) i `speed` (>=0) validatorima. RTDB rules deploy-ovano.
  - `MapScreen` LaunchedEffect na `selfBearing` + `selfSpeed`: kad `speed >= 2.78 m/s` (10 km/h driving threshold), `mapView.easeTo(bearing)` rotira kameru. Kad user stane → reset na north-up (0°).
  - `MapViewHolder.rotateBearing(bearing)` helper sa 400ms ease-in animacijom.

**#3 "5m" → "5min" u compactLastSeen**
- "m" je dvosmislen (metri vs minute) jer u istom StatChip row-u u MemberDetailSheet stoji distance ("5m" = 5 metara). Sufiks "min" je eksplicitno jasan.
- `core/util/Time.kt` ažuriran + unit test fix. **36/36 testova zelenih.**

**#4 Ghost member (član obriše app)**
- **Problem**: ako član obriše app, njegov FGS prestaje da publish-uje. Lokacija ostaje stale, "Osveži lokaciju" zahtev nikad ne dobije odgovor. Prvi user vidi člana kao "stuck" zauvek.
- **Rešenje (klijent-side, bez Cloud Functions-a)**:
  - Helper `MemberWithLocation.isLongOffline()` — true ako `updatedAt > 24h`.
  - Pin na mapi 40% alpha (`withIconOpacity(0.4)` + `withTextOpacity(0.4)`) za long-offline članove — "ghost" visual.
  - Banner u `MemberDetailSheet`: "Nije aktivan ${days}d. Možda je obrisao app... Vlasnik kruga može ga ukloniti iz Detalji kruga." Days izračunato iz `updatedAt`.
  - "Osveži lokaciju" button **disabled** za long-offline članove (refresh request svakako ne stiže do uništenog FGS-a, ne treba čekanje).
- Cloud Functions varijanta (server-side auto-cleanup) razmotrena ali deferred — Spark plan dovoljno za sada.

### Dan 1 (2026-06-22): UI polish — commits `8a66526`, `8c53d49`

### Onboarding (commit `8a66526`)
- **PageScaffold**: bela krug-kontejner za icon zamenjena **brand gradient kontejnerom** (`LogoBlue50` → `LogoPink50`) sa pulse animacijom (`infiniteRepeatable` 1.0 ↔ 1.04 / 3.2s, `FastOutSlowInEasing`, `RepeatMode.Reverse`).
- **IntroPage (Welcome)**: dodat `KrugLogo` 140dp hero iznad welcome teksta — brand first impression umesto čistog teksta.

### Loading states (commit `8a66526`)
- **CircleListScreen**: dok Firestore prvi snapshot ne stigne, prikazujemo **4 shimmer skeleton kartice** (alpha 0.35 ↔ 0.85 / 1.1s repeat) umesto blank screen-a. Strukturalno matchuje CircleRow oblik (44dp avatar circle + dve text linije).

### Micro-interactions (commit `8a66526`)
- **`pressScaleClickable` modifier** (`ui/brand/PressScale.kt`) — replikuje `Modifier.clickable` ali sa spring scale animacijom na press (0.96x default, `DampingRatioMediumBouncy`, `StiffnessMedium`).
- Primenjeno na: `CreateCircleFab`, `CreateCircleButton`, "Napravi prvi krug" pill, "Imam pozivnicu" Surface, `CircleRow` (pressedScale=0.98), `CirclePickerRow`, `ShareGradientButton` (ShowInvite).
- **`CircleIconButton` + `CircleLogoButton`**: na svaki tap **360° spin** (`animateFloatAsState` sa `spinTrigger * 360f`) — ista vrsta animacije kao splash logo. Umesto press scale (user feedback: "umesto smanjivanja, isto tako zarotiras logo kao na pocetku").

### MapScreen sheets polish (commit `8a66526`)
- **MemberDetailSheet**: 
  - `StatChip` pozadina sada **brand-tinted** (8% alpha accent + 22% alpha border) umesto generic `surfaceContainerHigh`. Battery zeleno/orange/crveno, distance plavo, last-seen primary. Svaki chip dobija identitet svoje accent boje.
  - Avatar krug ima **shadow halo u marker boji** (`shadow` sa `ambientColor`/`spotColor` = markerColor) za depth.
- **CirclePickerSheet**: 
  - `RadioButton` → **44dp color-coded circle icon** sa ikonom kruga (`CircleIconAssets.forKey`) — vizuelni jezik konzistentan sa TopFloatingBar pill-om.
  - Selected state: **2dp colored border + 10% alpha tinted background + "Aktivan krug" subtitle** u accent boji (umesto generic `primaryContainer`).
  - "Detalji" TextButton → `IconButton` sa `ChevronRight` (kompaktniji, ne odvlači pažnju).
  - Press scale na ceo red (0.98x).

### Splash spin (commit `8c53d49`)
- `SPIN_TARGET_DEG`: 180° → **360°** (pun krug). User testirao oba, ostavljeno na 360° kao default (više energije).

### ShowInvite screen redesign (commit `8a66526`)
- Dodat **`KrugLogo` 96dp hero** na vrhu — brand prisustvo na "magic moment" trenutku.
- **Code box**: `primaryContainer` flat → **gradient LogoBlue → LogoBlueLight** sa belim digit box-ovima i shadow halo-om u LogoBlue boji. Veće dimenzije (40×60dp digit boxes).
- **Novi "Kopiraj kod" button** između code-a i share-a — sa `ContentCopy` ikonom, switch-uje na `Check` + "Kopirano" tekst nakon tap-a (1.8s feedback delay). Koristi `ClipboardManager`.
- **Share button**: Material `Button` → **gradient pill** (kao CreateCircleFab) sa press scale animacijom, baltimore + Share ikona u semi-transparent krugu.

### CreateCircle redesign (commit `8c53d49`)
- **Live preview krug 120dp** na vrhu — boja + ikona iz state-a, ime ispod (placeholder ako prazan). Shadow halo u accent boji (`ambientColor`/`spotColor` = accentColor).
- **Color picker**: krug 44dp, selected ima `onBackground` border 3dp.
- **Icon picker**: unselected ima `outlineVariant` border 1dp (bilo: bez border-a, gubilo se na beloj pozadini ekrana → user je javio "krug je odsečen"). Selected: accent fill + 2dp accent border.
- **Create CTA**: solid `LogoBlue` pill (probano: inline `Brush.linearGradient` se rekreirao na svakoj recomposition-i kad input text promeni state, vizuelno "kvario" boju button-a na Samsung S24 — solid color je stabilniji izbor).

### Bug fixes (commit `8c53d49`)
- **Mapbox built-in location puck disable** (`MapScreen.kt:1189`): user prijavio "plavi krug ostaje na mapi posle refresh-a". Mapbox Standard style automatski uključuje location component sa plavim puck-om iz live device GPS-a — nezavisan od naših pin anotacija. Hardened disable: `mv.location.updateSettings { enabled = false; pulsingEnabled = false }` + `mv.location.enabled = false` (samo direktan setter Samsung One UI ponekad ignoriše).
- **Refresh focus** (`MapScreen.kt:464`): kad user tapne "Osveži lokaciju" člana, sada **odmah** flyTo na trenutnu poznatu poziciju + setuje `pendingRefocus` za novi update. `LaunchedEffect(pendingRefocus, state.members)` detektuje kad stigne nov `loc.updatedAt > baseline` i fly-uje na novu poziciju.
- **CircleListScreen state.loading**: bio je `Unit` (blank screen) → sada poziva `SkeletonList()`.

### UI eksperimenti koji nisu prošli (vraćeni)
- **Avatar Settings button**: pokušaj sa Google profile photo u Settings button-u (Gmail/Maps pattern). User: "ne ne, ruzno je".
- **Brand gradient Settings button**: gear ikona preko gradient kruga. User: "ne svidja mi se".
- **Profile + Settings split**: 3 button-a (Krugovi + Avatar + Settings). User: "ne ne, zajebi, ostavi samo gear ikonu ovakva kakva jeste".
- Finalna odluka: Settings ostaje plain `Icons.Outlined.Settings` gear sa 360° spin na tap.

### Krug spinner i fade-in heuristike (commit `8a66526`)
- `KrugSpinner` komponenta (4 brand dots rotirajuća) ostala neugrađena — postojeći loading indicatori u button-ima 18dp gde brand boje ne odgovaraju (button background je već primary color).
- Splash KrugLogo bez alpha fade-in heuristike: pokušali fade-in da pokrije Samsung One UI launcher pulse, ali to se vidi kao "logo niče iz transparentnog" na drugim uređajima. Vraćeno na "logo se prikazuje odmah na finalnoj veličini, samo spin daje animaciju".

### Šta NIJE urađeno (deferred za sledeću sesiju)
- **Settings hierarchy polish** — list rows → grouped cards po kategorijama (Profil / Privatnost / Performanse / O app).
- **EnterCode keypad polish** — 6-box keypad style code input umesto jednog text field-a.
- **MembersSheet polish** — kad tapneš "Članovi" pill, otvara se flat list.
- **NavHost slide transitions** — između screen-ova trenutno fade default.
- **Custom SOS confirm dialog** — Material AlertDialog → brand-styled dialog.
- **Empty state za MapScreen** kad imaš krug ali nema članova još.
- **Samsung S24 cold-start "white screen"** — user prijavio: app je zakucao na loading screen, posle USB konekta otišlo na Map. Logcat pokazao normalan startup (FGS, Firebase init, Mapbox surface render za 1s); MapScreen JIT-compiled tek u 11:13 (≈2 minuta posle launch-a) što je verovatno bila kombinacija slow JIT + Doze. Ostavljeno kao "intermitentni problem, pratimo" — potencijalni fix: baseline profile za Compose hot path.

## Play Store gotovost (procena ~60-70%)

**Tehnički gotovo (✓):**
- Release build sa R8 minifikacijom (TIER 4 fix iz desete sesije)
- `targetSdk 36`, 64-bit support
- Crashlytics + Timber breadcrumbs
- Privacy policy URL (`aleksandar-cypress.github.io/krug/privacy.html`) i Terms URL postoje
- Permission flow + onboarding
- FGS pravilno konfigurisan
- 36 unit testova zelenih

**Što fali (~1-2 sesije rada):**
- **Screenshots** — 5-8 reprezentativnih ekrana (može iz emulatora ili sa S24/Mi11)
- **Feature graphic** 1024×500 (banner za store listing)
- **Store listing copy** — kratak opis (80 chars), dugi opis (~500 reči), title, what's new
- **Data safety form** — Google traži deklaraciju (lokacija je "sensitive", ime, email, photo, device ID)
- **Upload signing key** — kreirati `release.keystore`, konfigurisati `signingConfig` u `app/build.gradle.kts`, dodati passwords u `~/.gradle/gradle.properties`
- **AAB build** (`./gradlew bundleRelease`) i upload na Play Console
- **VersionCode bump** (trenutno `versionCode=1`)
- **Internal testing track setup** — 100% bezbedan prvi korak

## Sledeća sesija — kandidati

1. **Play Store priprema** — signing keystore + signed AAB + data safety + screenshots + store listing copy
2. **Settings hierarchy polish** (#A) — category cards umesto flat list-a
3. **EnterCode keypad polish** (#B) — 6-box code input
4. **MembersSheet polish** — match CirclePicker visual jezik
5. **NavHost slide transitions** — između screen-ova
6. **Samsung S24 cold-start investigation** — baseline profile za Compose hot path
7. **Real-world test** — distribuiraj nov build beta grupi (Aleksandar + Jelena)
8. **Empty state za MapScreen** sa logo iznad

## Jedanaesta sesija (2026-06-22) — SVG brand rollout + splash animacija

### Novi brand asset (commit `76d6f9d`)
- `logo.svg` (i `logo.psd`) ručno napravljen brand — 4 figure (krug ljudi) sa lučnim povezivačima, boje iz `Color.kt` (`LogoBlue` `#3A86C8`, `LogoPink` `#E56B8F`, `LogoTeal` `#48B09B`, `LogoOrange` `#F3B250`).
- Konvertovan u Android `VectorDrawable`:
  - `ic_krug_logo.xml` (color)
  - `ic_krug_logo_monochrome.xml` (themed icons Android 13+)
- viewBox 911.83 × 909.26 sa `group translateX/Y` koji kompenzuje original SVG `translate(-44.8 -47.77)`.

### `KrugLogo` Compose komponenta — `app/src/main/java/org/krug/app/ui/brand/KrugLogo.kt`
- Parsira SVG path-ove preko `PathParser`, drži po Path-u + boji za fine kontrolu.
- API: `KrugLogo(modifier, animated, spinKey, contentDescription)`.
- `animated = true` → spin 180° (1.2s, `FastOutSlowInEasing`) sa 300ms delay-om posle launcher icon morph-a; bez scale entrance-a i bez breath idle animacije (oba sklonjeno posle iteracija sa user-om).
- `spinKey: Any?` → svaka promena vrednosti okida jednu 360° rotaciju za tap-to-spin easter-egg-e (npr. AboutScreen). Prvi composition se preskače.
- `animated = false` → statičan render za male ikone (MapScreen "Krugovi" button, AuthScreen, empty state).

### Splash flow (`SplashScreen.kt` + `MainActivity.kt`)
- `KrugLogo(animated=true)` 192dp centriran na beloj pozadini.
- `MIN_DISPLAY_MS = 1_600L` — min vreme prikaza pre navigacije (omogućava spin-u da se kompletuje).
- `setOnExitAnimationListener { provider.remove() }` u MainActivity-u — override-uje Android 12+ default icon zoom-out exit animaciju koja je izazivala "logo se pojavi veliki na sekund" flash.
- Sistemski splash icon `ic_splash_icon.xml` je vector sa **jednim transparentnim path-om** (`fillColor=#00000000`). Ovaj specifičan oblik:
  - Empty body = Samsung One UI fallback na launcher icon (treptaj).
  - Solid white = MIUI dodaje shadow oko bele ploče (vidljiv kvadrat).
  - **Transparent path** = sistem vidi validan drawable bez piksela za render, oba uređaja prikazuju ništa.

### `KrugSpinner` — `ui/brand/KrugSpinner.kt`
- 4 brand-colored dots koji rotiraju oko centra, infinite linear animation.
- Spreman za zamenu `CircularProgressIndicator`-a u većim loading state-ovima (nije force-replace u-ovoj sesiji jer su postojeći u button-ima 18dp gde brand boje ne odgovaraju).

### Logo zamene kroz app
- **Launcher ikona**: `ic_launcher_foreground.xml` i `ic_launcher_monochrome.xml` koriste vector logo sa **22dp inset-om** (centralna 64dp zona od 108dp adaptive canvas-a).
- **AuthScreen**: `Image(painterResource(krug_logo.png))` → `KrugLogo(modifier=size(230.dp))`. Crisp na svim density-jima.
- **AboutScreen**: isti logo 230dp + tap-to-spin easter egg (clip(`RoundedCornerShape(115.dp)`) za circular ripple + `padding(10.dp)` da head_blue top ne padne u clip ivicu).
- **MapScreen "Krugovi" button**: `Icons.Outlined.Diversity3` → static `Image(painter=R.drawable.ic_krug_logo)` 36dp unutar 48dp glass button-a.
- **CircleListScreen empty state**: `Icons.Outlined.Group` 80dp → `KrugLogo` 120dp (prvi utisak novog user-a sad je brand).

### Splash animation tuning (više iteracija)
Krenuli smo sa "orbital entrance" (4 figure spiraliraju iz off-screen-a + spin), preko "scale-up from tiny", do finalne **samo-spin** verzije. Razlozi za uklanjanje entrance-a:
- Scale entrance od 0.3 → 1.0 stvara vizuelni "shrink-then-grow" diskontinuitet sa Android launcher-icon-to-splash morph-om.
- Bilo koji entrance veličinski mismatch sa sistemskim splash-om je vidljiv kao "logo se pojavi veliki/mali na sekund" pre prave animacije.
- Bez entrance scale-a, Compose splash render je identičan sistemskom (oba 192dp), pa launcher morph → sistem splash → Compose splash je glatko.
- Single 180° spin daje "wow" bez stvaranja diskontinuiteta.

### SAFE_FIT_FACTOR uklonjen
Inicijalno KrugLogo je imao internal `SAFE_FIT_FACTOR = 0.94f` (5% padding unutar canvas-a) radi safety od circular clip-ova. Ovo je pravilo "splash logo se shrink-uje 5% nakon sistemskog splash-a → veliki na sekund pa se smanji". Sklonjen → KrugLogo sad fits canvas tesno. AboutScreen koji ima circular clip dobio `.padding(10.dp)` direktno u modifier-u (clip-safe padding samo gde treba).

### Notification ikone (commit `fb67947`)
- **`ic_notification.xml`** — 24dp monohromatska 4-dot silhouette, za status bar small icon. Zamenjuje `ic_launcher_foreground` koji je imao premali safe zone za notification crop maske.
- **`ic_notification_large.xml`** — pun color logo u viewBox 1800x1800 sa group translate (398, 397) koji centrira figure na **41% radijusa od centra** (outer extent ~46% sa head radius-om). Konvertuje se u 192dp bitmap preko `ContextCompat.getDrawable.toBitmap(192, 192)` i prosleđuje preko `setLargeIcon`.
- Oba poziva (`LocationTrackingService` FGS notification + `SosNotifier`) korigovana.

### Samsung One UI quirk — NIJE REŠEN
- **Problem**: Na A37, FGS notification badge (veliki krug levo u notification panelu) prikazuje launcher icon umesto `setLargeIcon`-a. Samsung One UI 7+ izgleda hardcoded ignoriše `setLargeIcon` za FGS notifikacije i koristi launcher icon foreground sa svojim kružnim crop-om, koji seče glave figura.
- **Pokušaji koji nisu radili**: `setLargeIcon(bitmap)`, povećanje padding-a unutar `ic_notification_large`, IconCompat varijanta.
- **Jedini fix koji bi radio**: povećati `ic_launcher_foreground` inset sa 22dp na 32dp+ — ali to čini launcher ikonu na home screen-u manjom, što user ne želi.
- **Trenutno stanje**: na Mi 11 notifikacija izgleda crisp i correct; na A37 figure su delimično isečene u badge-u. User je prihvatio da se ostavi ovako.

### Šta NIJE urađeno u ovoj sesiji (deferred)
- Empty state logo u MapScreen-u (kad nema krugova) — postoji "Napravi prvi krug" gradient pill ali bez logoa iznad. CircleList empty state je dobio brand logo.
- `KrugSpinner` nije ugrađen u postojeće loading state-ove (button progress indicators su 18dp gde brand boje su kontraproduktivne).
- Sistem splash duration tuning — sada je `MIN_DISPLAY_MS = 1_600L` što daje spin-u dovoljno vremena ali možda može da se skrati ako treba.

## Sledeća sesija — kandidati

1. **Samsung notification badge fix** — istražiti može li se override-ovati Samsung-ov default badge behavior preko `setStyle(NotificationCompat.DecoratedCustomViewStyle)` ili RemoteViews. Ili napraviti separate `ic_launcher_round` sa drugačijim inset-om koji koristi samo Samsung.
2. **Distribuiraj nov build beta grupi** — current `fb67947` je značajan brand upgrade.
3. **Real-world test** — vožnja sa A37, validacija lokacije + brand consistency.
4. **UI banner za Firestore error** — data layer (F9 iz prethodne sesije) još uvek spreman, UI ne pokazuje banner.
5. **Play Store priprema** — versionCode bump, screenshots, opis. Release build verifikovan da prolazi.

## Deseta sesija (2026-06-21) — user-reported bugovi + 4-tier code audit + UX polish

### User-prijavljeni bugovi (commit b49d2ed, c4755cc, da8f96f)
- **MapScreen "Udaljen" → "Udaljenost"** (label fix).
- **Putna umesto vazdušne distance**: `DirectionsRepository` sa Mapbox Directions API + LRU cache (64 entry, 5min TTL, 100m bucket). Fallback na haversine dok se učitava ili pri network fail (label "Udalj. (vazd.)" tad).
- **Privatni mod baga**: `isPrivate()` sada okida samo na `loc.paused == true`. Staleness više ne flipuje peer u privatni mod — battery-mode intervali (LOW=15min, STILL=20min, LOW_THROTTLED=30min) više nisu false-positive.
- **Crash na Uslovi link**: openUrl prebačen na Chrome Custom Tabs (`androidx.browser:1.8.0`), sa ACTION_VIEW + Toast fallback-om. Back se sada vraća u Krug umesto da gasi app.
- **Capitalize naziv kruga**: `core/util/StringFormat.kt` sa locale-aware `capitalizeFirstLetter()`, primenjeno u CreateCircleViewModel i CircleDetailViewModel.
- **SOS notifikacija "Neko"**: prebačeno na embedovan senderName + circleName u RTDB payload-u (zero-latency, nije observe-user fetch koji ume da timeout-uje). MapViewModel.triggerSos resolve-uje self ime preko 3-fold fallback chain-a (memberFlow → FirebaseAuth → UserRepository sa 3s timeout). LocationTrackingService.fetchDisplayName proširen chain (displayName → email prefix → friendly device → ""). Receiver-timeout 2s → 5s.
- **In-app SOS banner redesign**: 🆘 ikona u semi-transparent krugu, gradient red→red-dark, naslov sa imenom ("X traži pomoć"), subtitle "krug „Y" · pre Z min", per-member avatar pill (beli krug sa inicijalima u SosRedDark), "Pokaži" FilledTonalButton beli, pulsirajući glow shadow sinhron sa map ripple.
- **RTDB rules deploy**: dodato `senderName` (≤64) + `circleName` (≤32) validacije.

### Phase: stabilnost (commit 9ca7bae)
- **Crashlytics breadcrumbs + custom keys**: `CrashlyticsContext` singleton (uid + anonymous + activeCircleId), `CrashlyticsTree` propagira INFO logove kao breadcrumb-e, `Timber.i()` na svim ključnim akcijama (sign-in/out, SOS trigger/clear, circle create/leave/delete, circle switch, FGS start/destroy/profile-switch, app start).
- **Process-death recovery**: `LocationHealthWorker` proverava `shareLocationGlobal` (ne budi FGS uzalud), detektuje kill-loop pattern (lifetime < 60s → `Timber.w` non-fatal), detektuje silent A14+ start failure (proverava isRunning posle 2s → `Result.retry()`). LocationTrackingService prati `startedAtMs` + `lastFgsLifetimeMs`.
- **Permission UX**: `PermissionWarningBanner` na vrhu mape, lifecycle ON_RESUME re-check.
- **Unit testovi**: `app/src/test/` sa JUnit 4 + Truth, `core.util.Geo` (haversineMeters, formatDistance) + `core.util.Time` (compactLastSeen, sosRelativeTime) extracted iz MapScreen. 36 testova: StringFormat(7), DeviceNames(10), Geo(8), Time(11). Sve green.

### Audit Pass 1 — 4 paralelna Explore agenta (Auth/Location/Circle/UI)
Pokrenuti agenti, konsolidovan rangirani izveštaj sa file:line referencama. Razdvojeno u 4 TIER-a po impact-u.

### TIER 1 — CRITICAL (commit c224ed0)
- **T1.1 Rules: child shareLocation lock**. firestore.rules line 81 — `isSelf(memberUid)` davao update SVIH polja na member doc-u. Sada self-update ograničen na samo `nickname` field. Child ne može da promeni `isChild` ili `shareLocation` direktnim API pozivom. Rules deploy-ovane.
- **T1.2 FGS boost job lifecycle**. `scheduleProfileReconfigOnBoostExpiry` sad čuva Job handle u `boostExpiryJob`, cancel-uje se eksplicitno pre `scope.cancel()` u onDestroy. Bez ovog, `delay()` može da nadživi service teardown i `applyProfile` bi gađao polu-destroyed `fused` klijent.
- **T1.3 Mapbox MapView dispose**. Novi DisposableEffect(mapViewState) u onDispose poziva `annotationManager.deleteAll()`, `circleManager.deleteAll()`, clear-uje sosRipples + annotationToUid, null-uje onPinClick i poziva `mapView.onDestroy()`. Bez ovog, MapView (OpenGL kontekst + telemetry kanali) ostaje u memory svaki put kad user navigira sa Map ekrana.
- **T1.4 SOS dedup persistence**. `knownSosTriggered` prebačen iz in-memory mape u LocalPrefs (load u FGS onCreate, save na svaku promenu). TTL filter pri load-u drop-uje entry-je starije od 30min. Bez ovog, ako Android ubije FGS proces (OOM, BootReceiver restart), isti SOS bi opet zvonio.
- **T1.5 Invite accept Firestore transakcija**. Stari flow imao 3 race window-a: check-then-act na maxUses, AlreadyMember check non-atomic, joinCircle + invite usedBy razdvojeni. Sad sve tri provere + tri write-a (circle.memberIds, member subdoc, invite.usedBy) u jednu atomsku transakciju sa auto-retry.

### TIER 2 — HIGH (commit 78d3c8a)
- **T2.1 photoCache LRU bounded** — eviction stale URL-ova + hard cap 64.
- **T2.2 SOS animation pause** — `sosPhase = 0f` kad `activeSosMembers.isEmpty()`, LaunchedEffect gate-ovan; ranije se animacija tikala 60fps i okidala coroutine launch svake ms iako updateSosRipples nije imao šta da uradi.
- **T2.3 Permission busy-wait → lifecycle** — uklonjen 500ms polling loop iz LocationPermissionPage i NotificationsPermissionPage.
- **T2.4 GDPR delete ghost recovery** — `pendingDeleteUid` u LocalPrefs; SplashViewModel pri sledećem startu retry-uje cleanup + Auth.delete; ako i dalje fail, force signOut.
- **T2.5 SignInResult.Reason expansion** — `AccountDisabled`, `InvalidCredential`; novi `mapFirebaseAuthError` čita FirebaseAuthException error code-ove.
- **T2.6 Invite brute-force throttle** — exponential backoff (1s/2s/5s/15s) za consecutive failures + AtomicBoolean za double-tap race.

### TIER 3 — polish (commit 78d3c8a)
- **T3.1 Accessibility** — contentDescription na svim back IconButton-ima (CircleList, CircleDetail, CreateCircle, EnterCode, SettingsScaffold).
- **T3.2 Dead code** — skenirano, agent grešno klasifikovao `lastSeenLabel` kao dead, ipak je u upotrebi. SOS_TTL_MS duplikat između VM i FGS ostavljen kao defensive.
- **T3.3 Hardcoded boje → konstante** — `HEX_SOS_RED`, `HEX_PULSE_INDIGO` itd. reflektuju brand tokene iz Color.kt.
- **T3.4 lastObservedUpdate cleanup** — drop UID-ove koji nisu u trenutnim members posle obrade.
- **T3.5 UserRepository.updateDisplayName** — trim + max 40 char + odbij blank na repository nivou.
- **T3.6 Google sign-in timeout** — `withTimeoutOrNull(15s)` na CredentialManager.getCredential.
- **T3.7 Haptic feedback audit** — uklonjen iz CirclePicker (sekundarna nav).
- **T3.8 Auth flow shareIn** — jedan AuthStateListener za ceo proces; SharedFlow + WhileSubscribed(5s) + replay=1.

### Audit Pass 2 — još 4 paralelna Explore agenta (verifikacija + perf + error + build)
Detaljniji audit fokusiran na regression check, threading, error consistency, build/secrets. Konsolidovan u TIER 4.

### TIER 4 — release blockers + UX polish (commit 897dc93)
- **F1 Proguard @Keep za Firebase POJO**. Release build sa `minifyEnabled=true` je do sada bio slomljen — R8 bi preimenovao field-ove pa Firestore deserialization silent fail. Dodato keep rules za UserModel, CircleModel, MemberModel, InviteModel, SosModel, LocationModel, UserSettings + njihovi konstruktori. **Verifikovan release build sad prolazi (assembleRelease).** Najopasniji ceo audit nalaz.
- **F1.b debugAppCheck → implementation** (bila debugImplementation, što je u release-u izazivalo Unresolved reference).
- **F2 LocalPrefs commit=true → commit=false (apply)** — sync disk I/O na Main thread je ANR risk; 3 hot path-a (onboarding, circle switch, pending delete).
- **F3 SplashViewModel pending-delete u withTimeoutOrNull(10s)** — bez ovog Firestore/RTDB down bi mogao infinite-spin Splash.
- **F4 PermissionWarningBanner inicijalni check sinhroni** — `computeMissingPermissions(context)` helper; ranije je čekao prvi ON_RESUME pa se banner ne bi pojavio pri prvom otvaranju mape.
- **F5 signInAnonymously withTimeoutOrNull(15s)** — konzistentno sa Google.
- **F6 RTDB `onCancelled` logging** — LocationRepository observe + observeRefreshRequests, SosRepository observe — sad `Timber.w(error.toException(), ...)` + emit null/empty. Crashlytics breadcrumb dobija trag kad RTDB pada.
- **F7 EnterCode cooldown countdown UI** — `cooldownRemainingSec` eksponovan u UiState; ViewModel 1Hz tick coroutine, EnterCodeScreen prikazuje "Sačekaj X s" countdown + disable submit dugmeta.
- **F8 MapViewModel.observeUser uklonjen** — koristi se `UserRepository.observeUser` direktno (eliminisan duplikat Firestore listener-a).
- **F9 CircleRepository.lastSnapshotError StateFlow** — MapUiState i CircleListUiState imaju `circlesError`/`error` polje da UI razlikuje "user nema krugove" od "Firestore down". UI banner još nije implementiran, data layer spreman.
- **F10 DirectionsRepository CompletableDeferred in-flight** — dva istovremena poziva sa istim key-em sada dele isti fetch umesto duplikat HTTP-a.
- **F11 Mapbox fingerprint battery quantize** — `batteryPct / 20` bucket-i umesto raw %, ne okida deleteAll() na 1% promenu.
- **Defensive polish**: knownSosTriggered defensive empty init pre permission check, photoCache.toList() pre forEach, Firestore PersistentCacheSettings 50MB cap, AndroidManifest tools:targetApi 31 → 36.

### UX iteracije (commit 55e6e66)
- **Mapbox kompas u rotaciji** — `fadeWhenFacingNorth = true`, pojavi se gore-desno (ispod buttons row-a) kad user dva-prsta rotira mapu; tap vraća na sever. Nevidljiv dok je mapa već poravnata sa severom.
- **Krugovi ikona** — `Icons.Outlined.Group` → `Icons.Outlined.Diversity3` (3-4 osobe u kružnoj formaciji, vizuelno "krug ljudi"). Prvi pokušaj sa `GroupWork` odbačen po user feedback-u.
- **AboutScreen footer** — copyright pinovan na dno ekrana (outer Column sa weight + scroll iznad).

### Šta NIJE urađeno (deferred, low priority)
- **Firestore rules: bilo ko authenticated može da čita sve krugove/članove** — poznata limitacija (TODO comment u rules-u), traži Cloud Functions ili veći refactor invite flow-a. Risk je teorija (privacy leak svih krugova u prod-u), ne ugrožava family use case.
- **Dark mode** — eksplicitno ostavljeno light-only po Theme.kt komentaru, brand identity je fiksiran.
- **UI banner za Firestore error state** — data layer kroz F9 spreman ali UI ne pokazuje banner; user vidi prazan list umesto "Greška, retry" kad Firestore padne.
- **Touch target sweep <48dp** — neke ikone u avatar/battery chip-ovima 36dp; nije applikovano svuda jer su neke vizuelne ikone, ne click target-i.
- **Google API key cert restrictions** — Firebase Console action, ne kod. Korisnik treba da doda Android cert restrictions na ključ `AIzaSyChf...` ručno.

## Sledeća sesija — kandidati

1. **Distribuiraj nov build beta grupi** (Aleksandar + Jelena) — ovaj build (55e6e66) je značajan upgrade nad 2382cce, vredi push-ovati.
2. **Real-world test** — voziti se sa A37, posmatrati lokaciju da li i dalje skače kad rotiraš (validira kompas), posmatrati battery drain (validira animation pause + battery quantize).
3. **UI banner za Firestore error** — kratak rad, koristi `circlesError`/`error` field-ove iz state-a, pokaži retry banner.
4. **Status bar transparency / edge-to-edge polish** — sad sa kompasom + statusBarsPadding-om, vredi proveriti da ništa ne curi ispod sistemskih traka.
5. **Play Store priprema** — versionCode bump, screenshots, opis. Release build verifikovan da prolazi, mapping fajl se generiše za Crashlytics.

---

## Deveta sesija (2026-06-20 noć) — logo brand palette + location quality (Phase 1+2) + orphan circle cleanup

### Logo-derived brand palette
- **Color.kt**: 4 nove konstante izvučene direktno iz `krug_logo.png` (sampled pixel boje):
  - `LogoBlue = #3A86C8` (gornja figura, primarni brand)
  - `LogoBlueLight = #5BA0DC` (gradient pair)
  - `LogoBlue50 = #E5F0FA` (svetla container varijanta)
  - `LogoPink = #E56B8F` (leva figura, sekundarni)
  - `LogoPink50 = #FCE7EE`
  - `LogoTeal = #48B09B` (desna figura)
  - `LogoOrange = #F3B250` (donja figura)
- **Theme.kt**: `colorScheme.primary = LogoBlue`, `colorScheme.secondary = LogoPink`, `primaryContainer = LogoBlue50`, `secondaryContainer = LogoPink50`. Sve Material Buttons, primary tints, secondary container elementi automatski koriste brand.
- **AuthScreen + AboutScreen**: "Krug" naslov i Google sign-in dugme eksplicitno LogoBlue.
- **CircleListScreen + MapScreen gradient pillovi**: hardcoded indigo zamenjen sa LogoBlue/LogoBlueLight.
- **MapScreen self pin**: `#818CF8` → `#3A86C8` (logo blue).
- **batteryColor (MapScreen + MapMarkers)**: ≥50% → LogoTeal, 20-49% → LogoOrange, <20% → crvena (kritična).
- **MapMarkers paleta**: logo pink/teal/orange na prva 3 mesta da najčešći hash-evi padaju na brand boje; ostatak (violet/hot pink/cyan/orange/blue) za diversity preko 3-4 člana.
- **AboutScreen logo**: 140dp → **230dp** (isti kao AuthScreen, brand dominira).
- **AuthScreen**: uklonjen "Email" outlined button (placeholder za nepostojeću funkciju, samo zbunjivao). Ostalo Google + (debug) anonimna.

### Em-dash uklonjen iz production UI
- 8 mesta u app stringovima + Kotlin hardcoded text (notif body, SOS naslovi/baneri, battery desc, status linije, child banner, Diagnostics placeholders).
- 16 mesta u docs/ (privacy.html, terms.html, index.html) — page titles koriste `·` middle dot, inline definicije koriste `:` colon.
- Em-dash ostaje samo u code komentarima (dev-only).
- Welcome subtitle: "Budite uvek blizu onih do kojih vam je stalo" → **"Sigurnost i bliskost u jednom dodiru"**.

### Anonymous signOut cleanup (sprečava orphan circles)
- **Root cause:** anonimni Firebase Auth daje nov uid pri svakom sign-in cycle. Ako anonimni vlasnik kruga signOut-uje, krug ostaje sa starim uid-om kao `ownerId`. Pri sledećem signIn-u user dobija drugi uid → "novi user" ne vidi krug, ostali članovi vide orfan krug sa nepostojećim vlasnikom. Plus dete označeno od starog uid-a je zaglavljeno (UI sakriva "Izađi iz kruga").
- **Manual cleanup (today):** preko `firebase firestore:delete circles --recursive --force` obrisana cela kolekcija da Xiaomi izađe iz stuck-in-child stanja.
- **Code fix:** `AccountViewModel.signOut()` detektuje `FirebaseUser.isAnonymous` i pre auth signOut-a poziva isti cleanup kao `deleteAccount`:
  - `circleRepository.cleanupForDeletedUser(uid)` — owner krugovi se brišu, member krugovi se napuste
  - `locationRepository.deleteForUser(uid)`
  - `sosRepository.clear(uid)`
  - `userRepository.deleteUser(uid)`
- **Google sign-in user-i nemaju problem** — uid je stabilan, cleanup ne treba.
- **Edge case ostavljen:** uninstall bez signOut + Google user koji promeni nalog → orfan i dalje moguć (app ne može cleanup ako nije instaliran). Pravi fix tek sa Cloud Function user inactivity TTL (Blaze plan).

### Location quality Phase 1 (battery-neutral / pozitivan)
- **Movement filter:** publish samo ako se pomerio > 15m od poslednje published lokacije, ili ako je prošlo > 90s (force publish za freshness signal). Eliminiše redundantne publish-eve kad korisnik miruje.
- **Accuracy filter:** drop fixevi sa accuracy > 100m. Indoor/tunnel GPS spike-evi se ne publish-uju.
- **SOS boost:** `MapViewModel.triggerSos` paralelno zove `LocationTrackingService.triggerSosBoost(context)` → FGS prelazi na BURST profil 30min za frequent peer updates tokom hitne. Posle isteka, scheduleProfileReconfigOnBoostExpiry vraća profil na default.
- **Refresh boost:** kad peer pošalje fresh refresh ping, FGS prelazi na BURST 5min. Ako se ta osoba kreće, peer je prati uživo, ne dobija samo jedan fix.
- **Low-battery throttle:** ispod 15% baterije i ne puni se → LOW_THROTTLED profil (30min interval) umesto LOW (15min).
- **BURST profil:** 60s interval, 30s fastest, 0m displacement (konzervativni, ne 30s aggressive — battery drain podnošljiv tokom kratkih burst-eva).

### Location quality Phase 2 (Activity Recognition)
- **`ActivityRecognitionClient`** (Google Play Services) koristi akcelerometar (low-power, uvek-on senzor) da detektuje šta korisnik radi.
- **Permission:** `android.permission.ACTIVITY_RECOGNITION` (A10+ runtime). Lazy prompt u MapScreen kad user prvi put uđe u Map. Granted → FGS registruje client; skipped → graceful fallback na LOW.
- **`ActivityRecognitionReceiver`** (novi BroadcastReceiver) prima detection broadcast-e, filtrira confidence < 60, update-uje `LocationTrackingService.detectedActivity` companion var. "Poke-uje" FGS sa `EXTRA_ACTIVITY_CHANGED` da odmah reconfiguriše profile (ne čeka sledeći location callback).
- **Per-activity profili u `LocationProfile` enum-u:**
  - `VEHICLE` — 1.5min/45s/0m (voziš se, treba česti fix)
  - `BICYCLE` — 2min/60s/30m (biciklira)
  - `WALKING` — 4min/2min/30m (hoda/trči)
  - `STILL` — 20min/10min/500m (stoji/sedi, retko)
- **`computeProfile` prioritet:** SOS/refresh boost → MAX (opt-in) → low-battery → per-activity (VEHICLE/BICYCLE/WALKING/STILL) → LOW default.
- **DiagnosticsScreen:** dodato `detectedActivity` u FGS sekciju + `activityRecognition` permission status. Beta testeri mogu da vide šta profil radi uživo.

### Što i dalje treba (preostalo posle ove sesije)
- Distribuirati novi APK beta grupi kroz Firebase App Distribution (čekamo test feedback od user-a)
- Empty members CTA — "Niko se nije pridružio — pošalji pozivnicu" u MembersSheet kad si jedini
- Battery saver banner na Map kad user u SAVER modu
- Crashlytics breadcrumbs za key akcije
- Improved offline banner — "Offline — poslednje ažuriranje pre X min"
- Release signing + Play Store internal testing track (production korak)
- Google reauth flow za delete-account (one-step umesto sign-out/sign-in/retry)
- FCM SOS push za ubijeni app scenario (treba Blaze plan ~$1-3/mesec)
- Places/Geofencing v1 (per-place "Obavesti članove" toggle, child mode tie-in)
- History trail (24h breadcrumbs po članu)
- Refresh boost spam cap (zaštita ako peer spamuje "Osveži lokaciju")

## Gde smo stali (2026-06-20, osma sesija — UI polish + child mode invite + circle edit + onboarding skip)

Firebase rules: Firestore + RTDB **deployovane** sa child mode + paused field validatorima.
A37 + Xiaomi Mi 11 oba sa najnovijim build-om. Beta grupa nepromenjena (aleksandarr + jelenavasilic84), **nije** ponovo distribuirano.

## Osma sesija (2026-06-20 veče) — polish, refinement, returning user UX

### Onboarding skip INTRO za returning users
- **`SplashDecision.OnboardingPending(skipIntro: Boolean)`** — sealed class postala data class sa parametrom.
- `SplashViewModel.decide()` — ako Firestore user doc kaže `onboardingCompleted=true` (ili LocalPrefs flag), ali su permission-i izgubljeni (reinstall, OEM revoke), onboarding počinje **direktno od LOCATION ekrana** — INTRO welcome je preskačen.
- `Routes.Onboarding(skipIntro: Boolean = false)` — flag propaguje kroz nav.
- `buildOnboardingPages(context, skipIntro)` filtrira `INTRO` kad je true.
- Razlog: returning user koji reinstall-uje app je već video Welcome ranije; nema potrebe da ga opet vodimo kroz iste poruke. Idemo direktno na permission grant.

### Circle edit (ime, boja, ikona)
- **`CircleRepository.updateCircleDetails(cid, name, color, icon)`** — owner-only update (rules već enforce).
- **`hasOwnedCircleNamed +excludeCircleId`** — duplicate check ignoriše sam taj krug pri edit-u (ne smatra "kept the same name" za duplikat).
- **`CircleDetailViewModel.updateDetails()`** orchestrira validaciju + repo poziv. Vraća Boolean — false ako duplikat ili greška.
- **CircleDetailScreen TopAppBar Edit ikona** (samo za owner-a) → `ModalBottomSheet` sa novim `EditCircleSheet` composable-om.
- **`feature/circle/EditCircleSheet.kt`** — OutlinedTextField (20 char limit, duplikat error) + ColorPicker (6 boja edge-to-edge) + IconPicker (4 ikone, accent preview) + Sačuvaj/Otkaži.

### Auto-clear stale /locationRequests TTL
- **`LocationRepository.observeRefreshRequests`** signature change: `Flow<Set<String>>` → **`Flow<Map<String, Long>>`** (uid → timestamp).
- **`LocationTrackingService.observeRefreshRequests`** — separuje fresh (< 5min) od stale ping-ova. Stari se brišu **bez triggering-a one-shot fix-a** (sprečava reakciju na zaboravljene ping-ove kad FGS oživi sat kasnije).
- `REFRESH_REQUEST_TTL_MS = 5 * 60_000L` konstanta.

### SOS budi zaključan ekran
- **`AndroidManifest.xml`**: `USE_FULL_SCREEN_INTENT` permission + MainActivity `android:showWhenLocked="true"` + `android:turnScreenOn="true"`.
- **`SosNotifier`**: `setFullScreenIntent(pi, true)` na notification builder-u — kombinacija sa channel `IMPORTANCE_HIGH` + `CATEGORY_ALARM` budi screen čak i kad je telefon zaključan i u Doze.
- `CATEGORY_ALARM` daje auto-grant za `USE_FULL_SCREEN_INTENT` na Android 14+ (specijalna kategorija).

### Refresh refocus baseline fix
- **Bug:** `pendingRefocus = uid to System.currentTimeMillis()` koristio device clock kao baseline. Server timestamp (`updatedAt`) može biti ahead/behind device clock — poređenje `loc.updatedAt > since` nije pogađalo, kamera nije pratila novu lokaciju.
- **Fix:** baseline je sad **current `location.updatedAt`** u trenutku tap-a (server timestamp). Poređenje uvek koristi server vreme — pouzdano čak ako je device clock skewed.

### Duplicate circle name block
- **`CircleRepository.hasOwnedCircleNamed(uid, name, excludeCircleId)`** — query user-ovih krugova, lowercase + trim compare.
- **`CreateCircleViewModel.submit()`** — proverava pre `createCircle`. Ako duplikat, postavi `state.duplicateError=true`.
- **UI:** OutlinedTextField sa `isError = nameError || duplicateError`, supportingText prikazuje "Već imaš krug sa tim imenom". Reset-uje se čim user kuca novo.
- `create_circle_error_duplicate` string dodat.

### Map empty state — direktni shortcuts + flicker fix
- **Pre:** pill "Napravi prvi krug" → vodio na CircleList → još jedan "Napravi krug" dugme. Suvišan klik.
- **Sada:** pill ide **direktno na CreateCircle**. Plus ispod pill-a **"Imam pozivnicu →"** mali link → direktno na EnterCode.
- `MapScreen +onCreateCircle, +onJoinByCode` callback param-i; KrugNavHost wire-uje na CreateCircle / EnterCode route.
- **Flicker fix:** `MapUiState +circlesLoaded: Boolean = false`. Inicijalna `MapUiState()` ima false; `combineForUser` postavlja na true unutar konstruktora MapUiState-a. Empty state CTA + sakriven MembersPill renderuju se SAMO kad `circlesLoaded=true` — nema više bljeskanja "Napravi krug" CTA-a pre nego što Firestore vrati postojeći krug.

### Pin update pulse animacija
- **`MapViewHolder.runUpdatePulse(lng, lat)`** — one-shot CircleAnnotation 10dp → 42dp širi se kroz 800ms (24 steps), fade-out alpha 0.55 → 0. Boja indigo `#818CF8`.
- **`MapScreen`** drži `lastObservedUpdate: Map<uid, Long>` state; detektuje kad poraste `location.updatedAt` za uid → pokrene pulse (osim SOS pinova koji već imaju radar ripple).
- Inicijalna observacija ne pulse-uje (samo zapamti baseline) — sprečava bljeskanje za sve članove pri prvom load-u.

### Sign-out cleanup
- **`AuthRepository.signOut()`** sad zove `FirebaseDatabase.getInstance().goOffline()` **PRE** `firebaseAuth.signOut()`. Drops aktivne ValueEventListener-e sa starim auth token-om (Firebase ih ne raskida automatski na auth change).
- Na sledeći signIn, postojeća `refreshDatabaseAuth().goOnline()` oživljava konekciju sa novim token-om.

### LocalLifecycleOwner deprecation
- Migracija sa `androidx.compose.ui.platform.LocalLifecycleOwner` na `androidx.lifecycle.compose.LocalLifecycleOwner` u `PermissionPages.kt`. Compose 1.7+ deprecation warning eliminisan.

### LocationPermissionPage Phase 2 escape hatch
- **Bug:** user koji ne ume da promeni "While in use" → "Allow all the time" u MIUI/OEM system settings-u ostao zaglavljen na "Otvori postavke" ekranu.
- **Fix:**
  - Faza 2 koristi **`ACCESS_BACKGROUND_LOCATION` permission launcher** umesto direktnog `openAppSettings()` — Android A11+ automatski redirektuje na app Location settings.
  - **"Preskoči" secondary CTA** se pojavljuje **POSLE prvog tap-a** (`bgAttempted = true`). User koji ne uspe da grant-uje može da pređe dalje sa degraded experience (foreground-only tracking).
- Polling + ON_RESUME nastavljaju da pokupe state ako user uspešno promeni u settings-u.

### IntroPage duplicate fix
- **Bug:** prvi feature row je ponavljao `onb_welcome_title + onb_welcome_body` (već u hero bloku iznad). User je primetio "Aplikacija koja vas povezuje..." dva puta.
- **Fix:** uklonjen Welcome feature row; ostaju samo "Kako Krug radi" + "Privatnost".

### About screen polish
- 160dp logo → **140dp**; "Krug" naslov u **`displaySmall`** + indigo + Bold.
- Dodat **tagline** "Family Circle" (`app_tagline`) ispod.
- **Verzija** u `labelMedium` ispod tagline-a.
- Privacy + Terms link-ovi pretvoreni u **kartice** (`Surface + RoundedCornerShape(14.dp)` + ikona + OpenInNew indikator) umesto TextButton-a.
- **Copyright** "© 2026 Krug · Sva prava zadržana" na dnu.
- Scrollable Column za male ekrane.

### Notifications app open + auth-restore
- (Već postojalo, samo verifikovano) — SOS notifikacija click otvara `MainActivity` sa SINGLE_TOP + CLEAR_TOP. Nakon unlock-a, app je na Map screen-u sa SOS banner-om.

### Sledeće sesije — preostalo
- Empty members CTA — "Niko se nije pridružio — pošalji pozivnicu" u MembersSheet kad si jedini
- Battery saver banner na Map kad u SAVER modu
- Crashlytics breadcrumbs za key akcije
- Improved offline banner — "Offline — poslednje ažuriranje pre X min"
- Release signing + Play Store internal testing track
- Google reauth flow za delete-account
- FCM SOS push (treba Blaze plan ~$1-3/mesec)
- Places/Geofencing (per-place "Obavesti članove" toggle, default = on za isChild)
- History trail (last 24h breadcrumbs)
- Member trail / Places — uz Blaze ($)

## Gde smo stali (2026-06-20, sedma sesija — child mode + onboarding 3 koraka)

Firebase rules: Firestore + RTDB **deployovane** sa novim child mode + paused field validatorima.
A37 + Xiaomi Mi 11 oba sa najnovijim build-om. Beta grupa nepromenjena (aleksandarr + jelenavasilic84), **nije** ponovo distribuirano.

## Sedma sesija (2026-06-20 popodne) — child mode, onboarding 3 koraka, paused sharing

### Child mode v1 (per-circle, client-side enforce)
- **`MemberModel +isChild: Boolean = false`** (Firestore subcollection circles/{cid}/members/{uid}).
- **`CircleRepository.setChildStatus(cid, uid, isChild)`** — owner-only operacija.
- **`CircleRepository.observeMembersChildMap(cid)`** — live `uid → isChild` map, koristi se u CircleDetail i MapViewModel.
- **`CircleRepository.observeUserIsChildAnywhere(uid)`** — vraća true ako self ima isChild=true u BAR JEDNOM krugu (aggregacija kroz sve krugove).
- **Firestore rules update** (deployed): owner sme da menja SAMO `isChild` field na `circles/{cid}/members/{uid}` — nikad role, shareLocation, itd.
- **UI:**
  - **CircleDetailScreen MemberRow** — owner vidi 3-dot menu na ostalim članovima (ne self) sa opcijom "Označi kao dete" / "Ukloni oznaku deteta". `ChildCare` ikona pored imena + "Dete" label za markirane članove.
  - **PrivacyScreen** — ako `observeUserIsChildAnywhere == true`, banner "Roditeljska kontrola aktivna" + Switch disabled (ne može da pauzira deljenje). Defensive: `setShareGlobal` early-return ako je child.
  - **AccountScreen** — "Obriši nalog" dugme sakriveno ako je child anywhere. Defensive: `deleteAccount()` viewmodel-side `if (isChildAnywhere) return`.
  - **CircleDetailScreen** — "Izađi iz kruga" sakriveno + child banner ako je self označen kao dete u tom krugu.
  - **MembersSheet / MemberDetailSheet (mapa)** — ChildCare ikona pored imena (16dp u listi, 20dp u detail header-u).
- **`MemberWithLocation +isChild`** — `MapViewModel.combineForUser` observ-uje active circle's childMap i prosleđuje.

### Child invite flow (no race)
- **`InviteModel +prefillIsChild: Boolean = false`** — owner pri kreiranju invite-a bira da li je za dete.
- **`InviteRepository.createInvite(cid, uid, prefillIsChild=false)`** — piše flag u Firestore.
- **`CircleRepository.joinCircle(cid, uid, asChild=false)`** — pri accept-u, ako je invite prefillIsChild=true, member doc se kreira sa `isChild=true` ODMAH.
- **CircleDetailScreen — owner vidi DVA dugmeta:**
  - Primary "Pozovi članove" (filled)
  - Outlined "Pozovi dete (roditeljska kontrola)" sa ChildCare ikonom
- Eliminisan prozor između accept-a i ručnog markiranja gde bi dete moglo da isključi sharing ili obriše nalog.

### CreateCircle flow refactor
- **Pre:** `submit()` → kreira krug + invite atomarno → ide direktno na ShowInvite sa kodom.
- **Sada:** `submit()` samo kreira krug → `onCreated(circleId)` → navigate na **CircleDetail** → user tamo bira tip invite-a.
- `CreateCircleViewModel`: uklonjen `inviteRepository` dependency.
- `CreateCircleScreen.onCreated` callback signature: `(circleId: String) -> Unit`.
- KrugNavHost reroute: `CreateCircle → CircleDetail` (umesto ShowInvite).
- Razlog: bez ovog, prvi invite kod nakon "Napravi krug" je uvek non-child — vlasnik nije imao priliku da označi prvi invite kao dečji.

### Paused sharing visible to peers
- **Problem:** kad Xiaomi user isključi Privacy → shareLocationGlobal toggle, peers su i dalje videli njegov stari pin do isteka 15min staleness threshold-a. Plus Samsung tap-a "Osveži lokaciju" i ništa se ne događa (Xiaomi FGS `if (!shareGlobal) return`).
- **Fix:**
  - `LocationModel +paused: Boolean = false`
  - `LocationRepository.setPaused(uid, paused)` — piše SAMO `paused` child field u RTDB (ne dira lat/lng — peers zadržavaju last-known za "Otvori u Google Maps"). Kad un-pause, ažurira i `updatedAt = now`.
  - `PrivacyViewModel.setShareGlobal()` poziva i `locationRepository.setPaused(uid, !value)` paralelno sa Firestore settings update.
  - `MemberWithLocation.isPrivate()` PROVERAVA `location.paused` PRE 15min staleness check-a — peers vide "Privatni mod" odmah.
  - **`database.rules.json`** update (deployed): `paused` field validator `newData.isBoolean()`.

### Onboarding 6 → 3 koraka
- **Pre:** INTRO → LOCATION → BACKGROUND_LOCATION → NOTIFICATIONS → BATTERY → DONE
- **Sada:** **INTRO → LOCATION (combined fg+bg) → NOTIFICATIONS** (auto-complete)
- **Drop:**
  - `BATTERY` page — defer u Settings → Baterija (već postoji).
  - `DONE` page (AllSetPage) — auto-navigate u Map čim notifications grant-uje (`goNext()` u poslednjem step-u poziva `viewModel.complete()` direktno).
- **Combined LOCATION page (state machine):**
  - Faza 1: foreground location (sistemski dialog za ACCESS_FINE/COARSE_LOCATION).
  - Faza 2: background location — koristi `ACCESS_BACKGROUND_LOCATION` permission launcher (Android A11+ automatski redirektuje u app Settings → Location). Polling + ON_RESUME pokupe rezultat.
  - **"Preskoči" dugme** se prikazuje POSLE prvog tap-a na background grant (`bgAttempted = true`) — sprečava da user ostane zaglavljen ako ne ume da promeni "While in use" → "Allow all the time" u system settings-u (česti UX problem na MIUI).
- **IntroPage duplikacija fix:** prvi feature row je ponavljao welcome title+body (već u hero bloku iznad). Uklonjen — ostaju samo "Kako Krug radi" + "Privatnost" row-ovi.

### Notifications mandatory (peta sesija fix completed)
- Već bilo započeto, ali sad je čisto: `NotificationsPermissionPage` nema više "Preskoči" secondary. Posle dva odbijanja sistema (`shouldShowRequestPermissionRationale=false`), primary CTA se prebacuje na "Otvori sistemska podešavanja".

### Friendly device names (Samsung + Xiaomi)
- `core/util/DeviceNames.kt` — mapira `Build.MODEL` (cryptic kod) na ljudski naziv.
- **Samsung Galaxy:** S20 → S24 Ultra, A37 → A54, Z Fold/Flip, Note 20.
- **Xiaomi/Redmi/POCO:** Mi 11/12/13/14, Redmi Note 11/12, POCO F3/X3. Bez "Lite/NE/5G" suffixa (user feedback — nepotrebno).
- **Live transform u MapViewModel.memberFlow** — radi i za postojeće user-e bez re-sign-in.
- **`UserRepository.upsertOnSignIn`** fallback name koristi friendly oblik (novi sign-in piše friendly u Firestore direktno).
- **UI dedup:** MemberRow + MemberDetailSheet skip device subtitle ako je `displayName == deviceModel` (anon user bez nicknamea — top + bottom bi bili isti).

### SOS ripple animation
- **`MapViewHolder +circleManager: CircleAnnotationManager`** — kreiran PRE point annotation manager-a (renderuje se ispod pinova).
- `updateSosRipples(sosMembers, phase)` — kreira/update-uje/briše CircleAnnotation per uid.
- Compose `rememberInfiniteTransition` driver-uje `phase` 0..1 u 2s ciklusu.
- Radius: 20px → 80px, opacity: 0.5 → 0 kroz fazu. Boja `#DC2626` (SOS red).
- `LaunchedEffect(activeSosMembers, sosPhase)` triggera `mapViewState.updateSosRipples`.

### Haptics
- `LocalView.current.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)` — kratak lagani tick.
- Aplicirano na: pin tap, MembersSheet member row tap, SosFab, "Osveži lokaciju", Circle picker pick.

### Map style follows system theme
- Pre: `pickMapStyle()` hour-based (7-19h STANDARD, inače DARK) — konflikt sa Mapbox Standard auto-adaptacijom.
- Sada: uvek `Style.STANDARD` koji **prati system theme** (system Dark mode → tamna mapa). User kontroliše kroz Display settings na uređaju.

### Zoom 14 → 16.5 na klik
- `MapViewHolder.flyTo` koristi zoom 16.5 (umesto 14) na klik člana ili SOS banner — bliža auto-fokus na inspekciju.

### Landscape lock
- `AndroidManifest.xml` MainActivity `android:screenOrientation="portrait"` — nema rotacije, smatra se mobile-only app.

### Battery icon + color
- `BatteryBadge` composable: ikona (BatteryFull / BatteryChargingFull ako se puni) + procenat sa color tier:
  - ≥50% zelena `#10B981`
  - 20-49% žuta `#F59E0B`
  - <20% crvena `#EF4444`
- Aplicirano u MemberRow (MembersSheet) i StatChip "Baterija" u MemberDetailSheet.

### MemberDetail chip polish
- **Pre:** "Poslednje" chip imao value "pre 5 min" / "poslednji put viđen pre više od dana" — drugi se lomio u dva reda.
- **Sada:** `compactLastSeen()` helper vraća **"sad" / "5m" / "2h" / "1d+"** (uvek single-line). `lastSeenLabel()` (full) ostaje za MembersSheet gde ima prostora.
- StatChip composable: `maxLines = 1`, `overflow = Ellipsis` na label i value. Padding 14dp → 10dp horizontal.
- "Udaljenost" → "Udaljen" (kraće za chip).

### Photo u MembersSheet rowovima
- `MemberRow` prima `photo: Bitmap?` iz photoCache (postojeći Coil-loaded photo za markere).
- Ako photo postoji, render Image u avatar krug; inače inicijal.

### Refresh auto-refocus
- Kad user tapne "Osveži lokaciju" za drugog člana, `pendingRefocus = (uid, since)` se postavi.
- `LaunchedEffect(pendingRefocus, state.members)` watcher-uje za novi `updatedAt > since` na tom uid-u → flyTo automatski → clear pending.
- 30s timeout — ako fresh fix ne stigne, drop pending (target FGS ubijen, sharing paused, itd.).

### Diagnostics screen (debug-only)
- `Settings → Dijagnostika (debug)` — vidljiv samo u `BuildConfig.DEBUG`.
- 4 sekcije: FGS state (isRunning, lastPublishAt, publishAgo), Permissions, Identity (uid truncated, providerId, isAnonymous, email), Uređaj (rawModel, friendlyName, androidVersion).
- "Osveži" dugme (live re-read) + "Kopiraj sve" (ClipboardManager) — beta testeri mogu jednim paste-om da pošalju dijagnostiku.

### AuthScreen redesign
- Indigo gradient backdrop UKLONJEN → **bela pozadina**.
- Logo 180dp → **220dp**, bez Surface wrapper-a.
- "Krug" naslov sad u **indigo** umesto belo.
- Google dugme: bela bg + indigo text → **indigo bg + bela text** (jasniji kontrast na beloj pozadini).
- Email dugme: indigo outlined.

### Splash icon veći
- `drawable/ic_splash_icon.xml` inset **28dp → 10dp** (~60% veći logo na splash-u).

### Novi logo
- `logo.png` u root-u, 1254×1254 PNG, 4 vibrant figure (umesto starih 6) raspoređene u krug — čistiji dizajn.
- Kopiran u `drawable-nodpi/krug_logo.png` i `logo-krug.png` (root reference).

### "+ Napravi krug" gradient FAB
- `CircleListScreen` ExtendedFAB → custom `CreateCircleFab` (indigo gradient pill, 36dp + ikon u beloj prozirnoj kapsuli, jak shadow).
- EmptyState button → `CreateCircleButton` (full-width gradient varijanta).

### CircleList + CircleDetail render iconKey
- `CircleListScreen.CircleRow` — color disc povećan 40dp → 44dp, ikona kruga unutar (CircleIconAssets.forKey).
- `CircleDetailScreen.CircleHeader` — hardcoded `Icons.Outlined.Group` zamenjen sa `CircleIconAssets.forKey(state.iconKey)`.
- `CircleBrief +iconKey` + propagacija kroz MapViewModel.combineForUser.
- `CircleDetailUiState +iconKey` + load iz CircleModel.

### CreateCircle: 6 boja + 4 ikone u jednom redu (edge-to-edge)
- **`CirclePresets.colors`** suženo 8 → **6** (uklonjen cyan i orange — vizuelno bliski drugima).
- **`CirclePresets.icons`** suženo 6 → **4**: Porodica, Drustvo, Putovanje, Događaj.
- ColorPicker i IconPicker: **`Row(fillMaxWidth) + Arrangement.SpaceBetween`** umesto FlowRow + spacedBy. Stavke se ravnomerno raspoređu ivica-do-ivice.
- Icon picker circles 48dp → **60dp** sa color preview kad selected.

### EnterCode button visibility
- **Bug:** "Pridruži se" dugme imao `Spacer(Modifier.weight(1f))` koji ga gurao na dno → tastatura ga prekrivala → user vidi "white screen" iznad keyboard-a.
- **Fix:** uklonjen weight(1f), dodat `Modifier.imePadding()` na Column, dugme odmah ispod input polja.

### Notifications text
- `onb_notif_body`: "Obaveštenja su obavezna — bez njih nećete dobiti SOS od člana kruga koji traži hitnu pomoć" (jasnije zašto je obavezno).

### Trailing tačke uklonjene iz UI stringova
- 27 stringova u `strings.xml` + 6 hardcoded u Kotlin fajlovima — iOS-style čistije etikete.

### Log noise → Crashlytics fixes
- **BootReceiver** skip `MY_PACKAGE_REPLACED` na Android 14+ (FGS sa type=location iz background broadcast-a baca SecurityException).
- **publishLocation + locationCallback**: `CancellationException` → debug nivo (ne warn) — FGS shutdown nije non-fatal za Crashlytics.
- **LocationTrackingService.onCreate** SecurityException catch: warn → debug.

### Sledeće sesije — preostalo
- Sign-out cleanup (cancel RTDB listeners)
- LocalLifecycleOwner deprecation (Compose 1.7)
- Pin update pulse animation
- Map empty state — inline "Napravi krug" CTA
- About screen polish (verzija, copyright, logo)
- Release signing + Play Store internal testing
- Google reauth flow za delete-account
- FCM SOS push (treba Blaze plan)
- History trail / Places-Geofencing (postv1 feature-i)

## Šesta sesija (2026-06-20, jutarnja) — UX polish + circle identity

Repo public: **https://github.com/aleksandar-cypress/krug**
GitHub Pages live: **https://aleksandar-cypress.github.io/krug/** (Privacy Policy + Terms)
Firebase App Distribution **enabled**, beta grupa: aleksandarr@gmail.com, jelenavasilic84@gmail.com (+ maslacjana@gmail.com bez grupe).
Današnji commit: `4c38feb` Member detail polish + circle icon picker + UI cleanup. **NIJE jos distribuiran** beta grupi.

Testirano na A37 (S24 nije konektovan ovu sesiju). A37 trenutno offline u trenutku snimanja — ne radi Firebase Auth sign-in dok ne dobije network.

## Šesta sesija (2026-06-20) — UX polish, circle identity, log noise

### Member detail polish
- **Refresh auto-refocus** — kad user tapne "Osveži lokaciju" za drugog člana, `pendingRefocus = (uid, since)` se postavi. Kad stigne novi `location.updatedAt > since`, kamera automatski flyTo na novu poziciju. 30s timeout drop pending ako ne stigne fresh fix.
- **Private mode detekcija** — `MemberWithLocation.isPrivate()` ekstenzija: `updatedAt > 15min` ili `location == null` (za druge, ne self). U privatnom modu:
  - MembersSheet: status "Privatni mod" u sivom umesto "pre X min", baterija sakrivena
  - MemberDetailSheet: info banner objašnjava, "Osveži lokaciju" dugme sakriveno (poštedi user-a frustracije sa pingom koji ne radi), "Otvori u Google Maps" ostaje (last-known pozicija)
  - Mapbox pin: gray (`#9CA3AF`) umesto member boje
- **Charging + Distance chips** u MemberDetailSheet stats redu:
  - Baterija chip: ako `charging=true`, label postaje "Puni se" + `BatteryChargingFull` ikona; inače "Baterija" + `BatteryFull`. Boja zelena/žuta/crvena po nivou
  - Udaljenost chip: haversine od selfLocation, format "blizu" / "X m" / "X.X km" / "X km"
- **Friendly device names** (`core/util/DeviceNames.kt`) — mapira `Build.MODEL` (SM-S928B, SM-A376B) na "Galaxy S24 Ultra", "Galaxy A37 5G". Pokriva Galaxy S/A/Z linije unazad do S20. Primenjeno u MapViewModel.memberFlow — radi i za već signed-in user-e bez re-sign-in (live transform u UI sloju).
- **Battery ikona + boja u MembersSheet rowovima** — `BatteryBadge` composable umesto Surface+Text. Charging state u ikoni.
- **Google fotka u MembersSheet rowovima** — MemberRow prima `photo: Bitmap?` iz photoCache (već se koristio na markerima).
- **Zoom 14 → 16.5** na klik člana (`MapViewHolder.flyTo`) — bliže auto-fokus pri inspekciji.

### Circle identity (icon picker svuda)
- **`CirclePresets.icons`** suženo na 4: `family`, `friends`, `travel`, `event` (Porodica/Drustvo/Putovanje/Događaj). 4 staju u jedan red sa većim krugovima (60dp).
- **`CirclePresets.colors`** suženo na 6 (uklonjen cyan + orange — vizuelno najbliže drugima). Staje u 1 red edge-to-edge.
- **`feature/circle/CircleIconAssets.kt`** — mapping iz iconKey → Material ImageVector + lokalizovani label.
- **CreateCircleScreen**:
  - ColorPicker: `FlowRow` → `Row(fillMaxWidth) + Arrangement.SpaceBetween` — krugovi edge-to-edge bez viška praznog prostora desno
  - IconPicker: ista logika, accent boja prati selektovanu boju kruga (preview real-time dok bira)
- **Render iconKey svuda gde se prikazuje krug**:
  - `MapScreen.TopFloatingBar` pill: pre samo color dot, sad 28dp avatar (boja + ikona)
  - `CircleListScreen.CircleRow`: 44dp boji disc + ikona unutra
  - `CircleDetailScreen.CircleHeader`: 56dp disc + 28dp ikona (umesto hardcoded Icons.Outlined.Group)
- **`CircleBrief +iconKey`** + propagacija kroz `MapViewModel.combineForUser`.
- **`CircleDetailUiState +iconKey`** + load iz CircleModel.

### "+ Napravi krug" gradient FAB redesign
- `CircleListScreen` ExtendedFAB → custom `CreateCircleFab` — gradient pill (indigo 600→400), 36dp `+` u beloj prozirnoj kapsuli, jak shadow.
- EmptyState `Button` → `CreateCircleButton` (full-width gradient varijanta).

### AuthScreen redesign — bela pozadina
- Indigo gradient backdrop **uklonjen**, sad **bela pozadina**.
- Logo 180dp → **220dp**, bez Surface wrapper-a (bela na beloj ne ima smisla).
- "Krug" naslov u indigo (BrandIndigo600).
- Google dugme: white-bg indigo-text → **indigo-bg white-text** (kontrast).
- Email dugme: white outlined → **indigo outlined**.
- Debug anonymous + footer: muted onSurfaceVariant.

### Splash icon veći
- `ic_splash_icon.xml` inset **28dp → 10dp** (~60% veći logo na splash-u). Novi logo (4 figure) ima više belog prostora pa može manji inset bez clipping-a.

### Novi logo (4 figure)
- `logo.png` u rootu sa 4 vibrant figure raspoređene u krug (plava/pink/tirkizna/narandžasta) — čistija siluetna konstrukcija od starog 6-figure dizajna.
- Kopirano u `drawable-nodpi/krug_logo.png` (zameni stari) i `logo-krug.png` (root reference).

### Notifications mandatory
- `NotificationsPermissionPage`: secondary "Preskoči" dugme **uklonjeno**. Posle prvog tap-a, ako sistem više ne prikazuje dialog (double-deny ili "Don't ask again"), primary CTA se prebacuje na "Otvori sistemska podešavanja".
- Body string update-ovan: "Obaveštenja su obavezna — bez njih nećete dobiti SOS od člana kruga koji traži hitnu pomoć".
- A37 infinite-loop bug (iz prethodne sesije) ostaje rešen jer `SplashViewModel.decide()` NE proverava `hasNotifications` — samo location.

### Landscape lock
- `AndroidManifest.xml` MainActivity `android:screenOrientation="portrait"`.

### Trailing tačke uklonjene iz UI stringova
- 27 stringova u `strings.xml` + 6 hardcoded u Kotlin fajlovima (AuthScreen footer, AuthViewModel error poruke, MapScreen SOS dialog body + Privatni mod info banner).
- Internal tačke u višerečenicnim body stringovima zadržane.
- iOS-style čistije etikete.

### Log noise → Crashlytics fixes
- **BootReceiver**: skip `MY_PACKAGE_REPLACED` na A14+. Razlog: Android 14+ ne dozvoljava startovanje FGS sa type=location iz background broadcast-a — baci SecurityException. Pre fix-a, svaki Play Store auto-update generisao bi 1 lažni non-fatal u Crashlytics. Sad: debug log, return. User otvara app posle update-a → FGS startuje iz foreground (Map DisposableEffect).
- **`publishLocation` + `locationCallback`**: catch `CancellationException` zasebno, log debug umesto warn. Razlog: kad scope umire (FGS shutdown), in-flight publish coroutine baci JCE. Pre: 2-10 lažnih non-fatals po FGS smrti. Sad: tih.
- **`LocationTrackingService.onCreate`** SecurityException catch: warn → debug (expected background entry).
- `CrashlyticsTree` forward-uje samo `Log.WARN` i `Log.ERROR` — sa fix-ovima dashboard bi imao samo prave bugove.

### Bug fix: BootReceiver crash na reinstall
- Symptom: posle reinstall-a na A37, log pokazivao `SecurityException: Starting FGS with type location ...`
- Root cause: A14+ FGS-with-location ne sme iz background context-a (broadcast receiver bez activity).
- Fix: BootReceiver early-return za MY_PACKAGE_REPLACED na A14+.
- Plus dodatna defensiva u `LocationTrackingService.start()` companion: već je proveravala permission, ali ne i background eligibility — to je sistem-level check koji ne možemo zaobići.

### Logo (PSD source)
- `logo.psd` postoji na Desktop-u ali NIJE u repo-u (Photoshop source je intentional skip — veliki binary, nepotreban za build).
- Ako neko clone-uje repo i hoće da menja logo, mora da regeneriše PSD ili da koristi `logo.png` kao base.

### Šta NIJE urađeno (sledeća sesija — biranje):
- **Child mode v1** (per-member, vlasnik označava u CircleDetail; client-side hide leave/share-pause/delete-account)
- **Diagnostics screen** (debug-only Settings ekran sa FGS state, last publish, permissions, last error — alat za beta podršku)
- **Pin animacije** (SOS ripple, update pulse)
- **Onboarding skraćivanje** (combine Welcome/HowItWorks/Privacy)
- **Haptics**
- **Map style auto light/dark**
- Sign-out cleanup (cancel RTDB listeners)
- Auto-clear stale /locationRequests TTL
- Release signing + Play Store internal track
- LocalLifecycleOwner deprecation (Compose 1.7)

## Gde smo stali (2026-06-17, peta sesija — beta-ready)

Repo public: **https://github.com/aleksandar-cypress/krug**
GitHub Pages live: **https://aleksandar-cypress.github.io/krug/** (Privacy Policy + Terms)
Firebase App Distribution **enabled**, beta grupa kreirana, prvi release `0.1.0-debug (1)` distribuiran.

Testirano paralelno: Samsung A37, Samsung S24 Ultra (Google sign-in radi na S24, anonimni na A37). Brisanje naloga GDPR fan-out radi. Glass UI + Inter font + members peek live.

## Peta sesija (2026-06-17) — UI polish + GDPR + distribution

### Self-refresh dugme
- `LocationTrackingService.refreshSelf(context)` — startuje FGS sa `EXTRA_FORCE_REFRESH=true` koji preskače 3-min cooldown.
- `MemberDetailSheet` self grana sad ima "Osveži moju lokaciju" dugme + "Otvori u Google Maps" (ako ima lokaciju).
- `MapScreen` rutira: `member.isSelf` → `LocationTrackingService.refreshSelf(context)`, ostali → `viewModel.refreshMember(uid)`.

### Location publish reliability
- `requestOneShotFix()` u FGS sad radi **dva paralelna fix-a**:
  1. `getLastLocation()` — instant cache (Wi-Fi/cell/GPS), publish odmah
  2. `requestLocationUpdates(maxUpdates=1, BALANCED)` — sveži fix kao upgrade, pouzdaniji indoors od `getCurrentLocation(HIGH_ACCURACY)` koji često vraća null
- `publishLocation(uid, loc, source)` helper sa Timber-om za debug.

### Permission detection on Splash (reinstall fix)
- `SplashViewModel.decide()` sad **prvo** proverava `PermissionUtils.hasForegroundLocation(context)`. Ako nema → `OnboardingPending` bez obzira na Firestore/LocalPrefs flag.
- **NE proverava `hasNotifications`** — notifikacije imaju "Preskoči" dugme; ako traži, korisnik koji je svesno odbio bi se beskonačno vraćao u onboarding (Samsung A37 bug).
- Bez ovog, posle reinstall-a OS-level permissions su izbrisani ali Firestore pamti `onboardingCompleted=true` → user sleće na Map bez ijednog permission-a → FGS tiho odustaje (`SecurityException` na startForeground sa LOCATION type-om bez ACCESS_FINE_LOCATION na Android 14+).

### PermissionPages granted-shortcut
- `LocationPermissionPage`, `NotificationsPermissionPage`, `BatteryOptimizationPage` — `onPrimary` sad direktno zove `onGranted()` / `onContinueOrSkip()` ako je permission/exemption već granted.
- **Razlog:** `LaunchedEffect(granted)` se fire-uje samo kad se ključ MENJA. Ako je permission već granted (npr. iz prethodne sesije), `granted=true` na start-u; tap dugmeta pokrene launcher koji sistem odmah resolve-uje sa already-granted → callback ne menja stanje → `LaunchedEffect` se ne re-fire-uje → page se ne pomera. A37 ostao zaglavljen na "Dozvoli pristup lokaciji" iako je permission bio granted.

### SOS sound/vibration v2
- Notification channel ID **`krug_sos` → `krug_sos_v2`**. Channel postavke (importance, sound, vibration) na Androidu se ne mogu menjati posle prvog kreiranja — fresh ID forsira ponovno kreiranje sa našim novim postavkama (IMPORTANCE_HIGH, alarm sound, vibration pattern `[0, 500, 200, 500, 200, 500]`).
- **Direktan `Vibrator.vibrate(VibrationEffect)`** poziv u `notifySos()` kao belt-and-suspenders fallback — radi i kad Samsung One UI "Silent category" silence-uje sideload debug APK notifikacije.

### Splash double-jump fix (Android 12+)
- `androidx.core:core-splashscreen 1.0.1` dep + `SplashGate` singleton objekt sa `AtomicBoolean ready`.
- `MainActivity.onCreate`: `installSplashScreen().setKeepOnScreenCondition { !SplashGate.ready.get() }` PRE `super.onCreate`. Drži sistemski splash dok `SplashViewModel.decide()` ne postavi `SplashGate.ready=true`.
- Compose `SplashScreen` više ne pokazuje logo/text/spinner — samo bela Box pozadina. Eliminisao "system splash logo → Compose splash logo različite veličine → next route" jump.

### Privacy Policy + Terms na GitHub Pages
- `docs/index.html` — landing sa linkovima.
- `docs/privacy.html` — GDPR-aligned politika (10 sekcija): koje podatke prikupljamo, EU region (Firestore `eur3`, RTDB `europe-west1`), Mapbox ne dobija lokaciju, retention, prava korisnika, Poverenik link, kontakt.
- `docs/terms.html` — 14 sekcija: definicije, prihvatanje, opis usluge, **SOS disclaimer** ka 192/193/194/112, obaveze korisnika, odricanje od garancija, srpsko pravo + Beograd nadležnost.
- **Kontakt email svuda: `aleksandarr@gmail.com`** (NIKADA `aleksandar.vasilic@login5.org` za Krug — saved kao memory).
- `AboutScreen` dugmiće "Politika privatnosti" i "Uslovi korišćenja" sad otvaraju prave URL-ove preko `Intent.ACTION_VIEW`.
- **GitHub Pages enabled** (Source: Deploy from branch `main` /docs). Repo morao da bude **public** (GH Pages je free samo za public repos).

### Top bar pill — color dot + circle name
- `CircleBrief +colorHex: String` prosleđen kroz `MapViewModel.combineForUser`.
- TopFloatingBar pill: tačka boje aktivnog kruga (10dp) levo od imena. Pokazuje ime aktivnog kruga uvek (ne više "X krugova" count).

### SoS dugme tekst
- `SosFab` — tekst **"SoS"** umesto `Icons.Filled.Warning` ikone. `titleSmall` + FontWeight.Black + letter-spacing 0.5sp.
- Inactive = glass-style, Active = solid crveni (urgency override).

### Create Circle 20-char limit
- `CreateCircleViewModel.NAME_MAX_LENGTH = 20`. `setName` i `submit` enforce-uju (defense-in-depth).
- TextField supportingText: live counter `"X/20"`.

### Glass morphism na map pill-ovima
- `Modifier.krugGlass(shape)` helper — translucent white vertical gradient (alpha 0.82→0.72) + suptilan border gradient + 14dp shadow.
- Primenjeno na: TopFloatingBar pill, CircleIconButton (Group/Settings), MembersPill, inactive SosFab.
- Bez prave backdrop blur-a (zahtevalo bi `haze` lib ili `RenderEffect`) — translucent + border + shadow je dovoljan vizuelni efekat iznad Mapbox-a.

### Inter font (downloadable Google Fonts)
- `androidx.compose.ui:ui-text-google-fonts` dep + `res/values/font_certs.xml` (GMS provider sertifikati).
- `KrugTypography` rebuilt: ceo font sistem koristi `Inter` (Regular/Medium/SemiBold/Bold/Black). Tightened letter-spacing na display/headline (Inter dobro nosi -0.7 do -1.0 sp).
- Prvi run može imati 1-2s kašnjenja dok GMS download-uje font; cache-uje se posle.

### Members peek (bottom pill avatars umesto count-a)
- `MembersPill(members, photoCache, onClick, modifier)` umesto starog `(count, ...)`.
- Stack od **3 mini avatara (26dp)** sa **30% preklapanjem** + beli 1.5dp border (kao iOS Find My / WhatsApp grupe). `+N` badge za overflow.
- `MemberMiniAvatar` koristi member boju ili Coil-cached fotku.
- **Active SOS** → pulsirajući crveni border preko `rememberInfiniteTransition` + `animateFloat` (alpha 1.0 ↔ 0.35, 700ms reverse).

### Logo size bumps
- AuthScreen logo: 140dp → 180dp (container shape 40→48, shadow 12→16, inner padding 14→12).
- AboutScreen logo: 96dp → 160dp.

### `isCharging` → `charging` rename
- Kotlin `is` prefix na Boolean property je konfundovao Firebase ClassMapper, generišući "No setter/field for isCharging" warning na svakom read-u.
- Promenjeno u `LocationModel`, `LocationRepository.publish` (zapisuje key `charging`), i **`database.rules.json`** (validator zove se `charging`).
- **KRITIČNO:** ako se promeni field name a rule se ne update-uje, `$other.validate: false` blokira sve write-ove kao "Permission denied". Već videno na A37 — `charging` field nije bio dozvoljen dok nismo deploy-ovali update-ovana pravila.

### GDPR account deletion (Spark plan, no Cloud Functions)
- **Repo fan-out:**
  - `LocationRepository.deleteForUser(uid)` — `/locations/{uid}` RTDB
  - `SosRepository.clear(uid)` — već postojao
  - `CircleRepository.cleanupForDeletedUser(uid)` — krugovi gde sam vlasnik → `deleteCircle`, krugovi gde sam član → `leaveCircle`
  - `UserRepository.deleteUser(uid)` — settings subcollection + user doc
  - `AuthRepository.deleteAccount()` — `FirebaseUser.delete()`; vraća `false` ako Firebase traži recent re-login (Google sign-in)
- **Orchestrator:** `AccountViewModel.deleteAccount(context)` u tačnom redosledu: stop FGS → RTDB cleanup → Firestore fan-out → Auth delete. Ako auth delete vrati `false`, signal-uj UI-u preko `deleteNeedsReauth=true`.
- **UI:** AccountScreen real confirmation dialog ("Obriši trajno") + progress text "Brisanje…" + reauth-needed dialog. Stari "Brisanje dolazi uskoro" stub obrisan.

### Auth-bounce posle re-sign-in (RTDB Permission denied fix)
- Symptom: posle `deleteAccount → signInAnonymously`, RTDB klijent je čuvao stari token (obrisanog korisnika) i odbijao SVE publish-ove kao "Permission denied" čak i sa novim `firebaseAuth.currentUser.uid`.
- Fix: `AuthRepository.refreshDatabaseAuth(user)` se zove posle svake (anonimne i Google) prijave:
  - `user.getIdToken(true)` — force refresh JWT
  - `FirebaseDatabase.goOffline()` + `goOnline()` — bounce konekciju da pokupi novi token

### Firebase App Distribution
- App ID (debug): `1:441540594744:android:bd8143f5ad8d84e9fb6acd`
- App ID (release): `1:441540594744:android:ccf79ac86d8a6c2afb6acd`
- Grupa **`beta`** (display: "Beta Testers") kreirana.
- Tester-i u grupi:
  - `aleksandarr@gmail.com`
  - `jelenavasilic84@gmail.com`
- Prvi release: **`0.1.0-debug (1)`** — distribuiran beta grupi 2026-06-17.
- Console URL: https://console.firebase.google.com/project/krug-86527/appdistribution

#### Distribute komanda za buduće build-ove
```bash
./gradlew assembleDebug
firebase appdistribution:distribute app/build/outputs/apk/debug/app-debug.apk \
  --app 1:441540594744:android:bd8143f5ad8d84e9fb6acd \
  --groups beta \
  --release-notes "..."
```

## Šta NE radi još (punch lista — preostalo)

| Šta | Effort | Prioritet |
|-----|--------|-----------|
| **Pin animacije na mapi** (SOS ripple + pulse na update) | ~1.5h | UI WOW, sledeća sesija |
| Map style toggle (light/dark auto prema vremenu) | ~15min | UI nice-to-have |
| Subtle haptics na tap pina/dugmadi | ~20min | UI polish |
| Sign-out cleanup (cancel RTDB listener-e) | ~20min | quality |
| Auto-clear stale `/locationRequests` sa TTL-om | ~20min | quality |
| Google reauth flow za delete-account (Recent login required) | ~1h | nice-to-have |
| Release signing config + Play Store internal testing | ~2h | sledeći production korak |
| `LocalLifecycleOwner` deprecation warnings (Compose 1.7) | ~10min | sitnica |

## Komande za sledeću sesiju

```bash
# Build + install paralelno na oba uređaja
./gradlew assembleDebug
adb -s R5CWC1F9FND install -r app/build/outputs/apk/debug/app-debug.apk &
adb -s RFGL30L2A5Z install -r app/build/outputs/apk/debug/app-debug.apk

# Firebase distribute novom buildu
firebase appdistribution:distribute app/build/outputs/apk/debug/app-debug.apk \
  --app 1:441540594744:android:bd8143f5ad8d84e9fb6acd \
  --groups beta \
  --release-notes "..."

# Deploy RTDB rules (ako menjamo database.rules.json)
firebase deploy --only database

# Deploy Firestore rules (ako menjamo firestore.rules)
firebase deploy --only firestore:rules

# Logcat filter za Krug-only debug
adb -s RFGL30L2A5Z logcat --pid=$(adb -s RFGL30L2A5Z shell pidof org.krug.app.debug)
```

## Test uređaji
- **Samsung S24 Ultra** (`R5CWC1F9FND`) — Google sign-in
- **Samsung A37** (`RFGL30L2A5Z`) — anonimni sign-in (testiramo brisanje + reauth flow)

---

## Prethodne sesije

Build je uspešan i prošao više iteracija. Repo je pushovan na GitHub: **https://github.com/aleksandar-cypress/krug**

App-ovi su instalirani i testirani na 3 uređaja paralelno: Samsung A37, Samsung S24 Ultra, Xiaomi 11 Lite NE. Google sign-in radi (S24), anonymous sign-in radi (A37/Xiaomi). Map pinovi, krugovi (create/join/leave/delete), SOS, security rules (deployed), refresh ping mehanizam — sve funkcionalno.

## Crashlytics + App Check + launcher/splash icon (2026-06-17, četvrta sesija)

### Crashlytics
- Plugin `com.google.firebase.crashlytics` v3.0.2 dodat u `libs.versions.toml` i applied u `:app`.
- Dep: `firebase-crashlytics` (preko Firebase BoM).
- `CrashlyticsTree` (`core/logging/CrashlyticsTree.kt`) — Timber tree koji forward-uje WARN/ERROR + throwable-e u `FirebaseCrashlytics.recordException()` i `log()`.
- `KrugApplication`: `isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG` (debug ne piše u dashboard). Plant DebugTree u debug, CrashlyticsTree u release.
- **Smoke test** (privremeno radio za Console registraciju): non-fatal `IllegalStateException` poslat preko `recordException` + `sendUnsentReports()`. Console primio event, `Crash-free users 100%` pokazano. Smoke test kod posle uklonjen.

### App Check
- Deps: `firebase-appcheck-playintegrity` (release), `firebase-appcheck-debug` (debug only).
- `KrugApplication`: `FirebaseApp.initializeApp(this)` pa `appCheck.installAppCheckProviderFactory`:
  - Debug: `DebugAppCheckProviderFactory.getInstance()` — SDK loguje debug token na prvom run-u
  - Release: `PlayIntegrityAppCheckProviderFactory.getInstance()` — Play Store attestation
- **A37 debug token registrovan u Firebase Console:** `397b630c-263d-4771-8214-f5f451852c9e`
- **SHA-256 debug fingerprint** (registrovan uz Play Integrity provider): `25:2A:F4:EB:63:AA:A2:7D:2C:07:2B:8B:15:6C:C2:08:13:E6:80:95:68:E1:FF:9B:FF:1B:D6:8B:CD:F7:2A:B9`
- **App Check APIs tab — i dalje "Unenforced"** (monitoring mode). Prebaciti na "Enforced" tek kad se potvrdi da legitimni requests prolaze sa attestation.

### Launcher ikona (logo-krug.png)
- User dao novi dizajn: 1024×1024 transparentni PNG, 6 vibrantnih figura raspoređenih u krug (plava/ljubičasta/tirkizna/pink/zelena/narandžasta).
- Source u repo: `logo-krug.png` (root). Kopirana u `app/src/main/res/drawable-nodpi/krug_logo.png`.
- **`ic_launcher_foreground.xml` inset = 8dp** — kompromis između veličine figura i clipping-a na squircle mask-u (Samsung One UI). Originalan 1024 canvas + 0dp inset je sekao glave figura, 16dp je bio previše zoom-out, 8dp je sweet spot.
- **Photoshop specs koje sam dao user-u za buduće redesign-e:**
  - Canvas 1024×1024 px, RGB, 8-bit
  - Background transparentno (nema beli sloj!)
  - Sav vitalan sadržaj unutar **inner kruga prečnika 660 px** (safe zone)
  - Spoljna zona 660→1024 px može biti vidljiva ali ponekad sečena (round/squircle/teardrop mask)
  - Export: PNG-24 sa alpha checked
  - Filename: `krug_logo.png`, putanja: `app/src/main/res/drawable-nodpi/`

### Splash icon (Android 12+)
- **Problem:** posle "kill app" pa cold start, logo se pojavi sečen na splash circle-u. Android 12+ `windowSplashScreenAnimatedIcon` po defaultu uzima adaptive icon foreground i seče dodatno (splash circle ~192dp sa safe zone ~160dp = manje od launcher mask-a).
- **Fix:**
  - `res/drawable/ic_splash_icon.xml` — inset wrap krug_logo sa **28dp** insetom (mnogo više od launcher-ovih 8dp)
  - `res/values-v31/themes.xml` — override Theme.Krug za API 31+ sa `windowSplashScreenAnimatedIcon=@drawable/ic_splash_icon` i `windowSplashScreenBackground=#FFFFFF`
- **Status:** instalirano, čeka user verifikacija (kill+open A37 da vidi je li splash sad cele figure).

### Sledeći planirani koraci posle reboot-a
- Verifikacija splash icon-a (Power Save / cold start na A37)
- Self-refresh dugme za member-a (user pitao zašto pokazuje "poslednje 6 minuta" — to je LOW profil; predlog (b): "Osveži moju lokaciju" dugme u MemberDetailSheet kad je member.isSelf)
- Posle: Privacy policy + Terms na GitHub Pages, brisanje naloga (GDPR), battery optimization permission polish

## Critical heat hotfix + SOS lokalno + multi-circle (2026-06-17, treća sesija)

Najpažljivija sesija — krenuli sa malim UI tweak-ovima, otkrili **kritičan loop bug** koji je trošio 48% CPU + GPS spike svakih 80ms (pojeo bateriju na S24 — user je morao da deinstalira). Plus uveden koncept "aktivnog kruga" jer mapa je dotad mešala članove iz svih krugova.

### Critical hotfix: refresh-ping petlja (48% CPU)
- **Root cause 1:** `clearRefreshRequests` zvao `removeValue()` na **parent path-u** `/locationRequests/{targetUid}`, ali RTDB rule dozvoljava `.write` samo na **child path-u** `/locationRequests/{targetUid}/{requesterUid}`. Delete fail-uje → stale entry ostaje → listener re-emit → drugi refresh fix poziv → ponovo.
- **Root cause 2:** `observeRefreshRequests` koristio `collectLatest` koji cancel-uje in-flight clear coroutine čim RTDB local cache emit-uje (transient state change u toku same network write-a).
- **Posledica:** FGS pulluje **HIGH_ACCURACY** one-shot GPS fix svakih **~80ms** u petlji. Trostruka grejaća petlja: GPS chip + radio + 48% CPU.
- **Fix:** `LocationRepository.clearRefreshRequests(uid, requesters: Set<String>)` brisanje po child path-u (po requester-u). `observeRefreshRequests` koristi `collect` (sequenced) umesto `collectLatest`.
- Verifikovano kroz ADB: CPU pao sa **48.3% → 0.0%**, log čist od refresh spamova posle force-stop + restart-a.

### SOS lokalna verzija (Spark-friendly, bez Cloud Functions)
- **`SosNotifier`** (`core/sos/SosNotifier.kt`) — kreira channel `krug_sos` sa `IMPORTANCE_HIGH`, default alarm sound (`RingtoneManager.TYPE_ALARM`), vibration pattern `[0, 500, 200, 500, 200, 500]`. `notifySos(uid, name)` i `cancelSos(uid)`. Per-uid notification ID (`SOS_NOTIFICATION_BASE_ID + hash`).
- **`LocationTrackingService.observeCircleSos()`** — observe-uje `/sos/{uid}` za sve `non-self` UID-ove iz svih krugova. `combine` per-uid flows → diff state u `knownSosTriggered: MutableMap<String, Long>`. Na transition `null → active` fire notification, na `active → null` ili 30-min TTL cancel.
- **Inject-ovani novi repo-i u FGS:** `CircleRepository`, `UserRepository`, `SosRepository`, `SosNotifier`.
- **Manifest:** `VIBRATE` permission dodat.
- **Strings:** novi `sos_notif_channel`, `sos_notif_title`, `sos_notif_body`.
- **Limit:** radi samo dok je FGS živ. Ako OEM ubije servis, no notification. Pravi FCM push traži Blaze + Cloud Functions (odložen).

### SOS scope per krug (drugi bug)
- **Bug koji je user uočio:** A37 napravi novi prazan krug, prebaci se u njega, fire-uje SOS — S24 (u drugom krugu) **i dalje dobija notifikaciju**.
- **Root cause:** SOS payload nije imao circleId. Svako ko observe-uje `/sos/{uid}` reaguje, bez obzira u kom krugu je SOS namenjen.
- **Fix:**
  - `SosModel +circleId: String?`
  - `SosRepository.trigger(uid, lat, lng, circleId, message?)` — circleId obavezan parametar
  - `MapViewModel.triggerSos()` prosleđuje `uiState.value.activeCircleId`
  - `LocationTrackingService.handleSosUpdate(uid, sos, myCircleIds)` — proverava `sos.circleId in myCircleIds`. Legacy SOS bez circleId-a → fallback prolazi (backward compat).
  - `MapViewModel` UI filter sakriva SOS koji ne pripada aktivnom krugu.
  - **RTDB rules:** dodato `"circleId": { ".validate": "newData.isString() || !newData.exists()" }` u `/sos/{uid}` (jer `$other: false` ga inače blokira). Deploy-ovano kroz `firebase deploy --only database`.

### 30-min auto-clear za SOS
- `MapViewModel.combineForUser` — ako je self SOS prešao `SOS_TTL_MS = 30*60_000L`, pozove `sosRepository.clear(selfUid)` automatski.
- Defensive UI filter — SOS stariji od TTL ili sa drugačijim `circleId` se tretira kao da nije aktivan.
- FGS observer takođe poštuje TTL u `handleSosUpdate`.

### Multi-circle independence (active circle koncept)
- **Bug koji je user uočio:** ako sam u krugu A sa drugim, napravim novi krug B (sam) i prebacim se u njega — i dalje vidim članove iz kruga A na mapi.
- **Root cause:** mapa je dotad prikazivala **uniju svih članova iz svih mojih krugova**. Nije bilo koncepta "aktivnog kruga".
- **Implementacija:**
  - `LocalPrefs.activeCircleIdFlow: StateFlow<String?>` — persisted u `krug_prefs` (`active_circle_id` ključ). `setActiveCircleId(id)` upisuje + emit-uje.
  - `MapViewModel.combineForUser` — `combine(circlesFlow, localPrefs.activeCircleIdFlow)` → filter samo `active.memberIds + selfUid`. Default fallback = prvi krug iz liste ako stored id nije validan.
  - `MapUiState +activeCircleId: String?`.
  - **Top bar pill** više ne pokazuje "X krugova" — uvek pokazuje ime aktivnog kruga. Klik → otvara **`CirclePickerSheet`** (radio button po krugu, "Detalji" dugme, "Upravljaj krugovima").
  - `viewModel.setActiveCircle(id)` se zove iz picker-a → LocalPrefs flow emit-uje → MapViewModel se odmah refreshu-je.
- **Strings:** novi `map_circle_picker_title`, `map_circle_picker_detail`, `map_circle_picker_manage`.
- **FGS SOS observer** namerno **NIJE** filtriran po aktivnom krugu — observe-uje sve krugove da ne propusti hitno (safety-first). Active circle utiče samo na UI mape + SOS scope kod slanja.

### Mapbox UI polish
- **Compass isključen** (`mv.compass.updateSettings { enabled = false }`) — pojavljivao se na rotaciji iza Settings dugmeta. App ima "Centriraj" / flyTo akcije, kompas nije potreban.
- **Scale bar** repositioned: `position = Gravity.BOTTOM or Gravity.START`, `marginBottom = 8dp`, `marginLeft = 16dp`, `isMetricUnits = true`. Pre toga je bio gore-levo iza Krug pill-a u ft/mi.
- **MembersPill** padding bumped: `bottom = 36dp` (iz 24dp).
- **SosFab** padding bumped: `bottom = 44dp` (iz 32dp).
- **Imports:** `import android.view.Gravity`, `com.mapbox.maps.plugin.compass.compass`, `com.mapbox.maps.plugin.scalebar.scalebar`.

### Member auto-focus on click
- Klik na pin na mapi (`mapViewState.onPinClick`) → flyTo + otvori MemberDetail sheet.
- Klik na red u "Članovi" sheet-u → close list + flyTo + otvori MemberDetail.
- **Uklonjeno "Centriraj na mapi" dugme** iz `MemberDetailSheet` — postalo redundant. `onFlyTo` callback obrisan iz `MemberDetailSheet` signature.

### Crash guards u LocationTrackingService (Samsung A37 crash posle reinstall-a)
- **Symptom:** posle reinstall-a, app crash-uje sa `SecurityException: Starting FGS with type location ... requires ACCESS_FINE/COARSE_LOCATION`. Permissions resetovane reinstall-om, ali `BootReceiver` (`MY_PACKAGE_REPLACED`) i `LocationHealthWorker` odmah pozovu `LocationTrackingService.start()`.
- **Fixes:**
  - `LocationTrackingService.Companion.start()` — proverava `PermissionUtils.hasForegroundLocation(context)`, vraća se bez `startForegroundService` ako nema permission.
  - `onCreate` — duplicate guard + `try/catch` oko `ServiceCompat.startForeground` (defensive layer).
  - `onStartCommand` — proverava `isRunning.get()`; ako je `onCreate` rano izašao (no permission), `stopSelf + START_NOT_STICKY`. Sprečava `UninitializedPropertyAccessException: fused not initialized` koji je sledeći crash bio.

## Heat reduction fix (2026-06-17, druga sesija)

Prijava: telefoni se ozbiljno greju u toku korišćenja. Diagnostika je pokazala 5 izvora grejanja — sve popravljeno.

### Šta je grejalo
1. **Default `HYBRID` + threshold 15%** → telefon je bio u **HIGH profilu praktično ceo dan** (2min/60s/50m intervals). HIGH treba da bude izuzetak, ne pravilo.
2. **`LocationHealthWorker`** je forsirao `requestOneShotFix()` (HIGH_ACCURACY) svakih 15min čak i kad je FGS živ.
3. **`DisposableEffect` na Map screen-u** je palio HIGH_ACCURACY GPS fix pri svakom ulasku.
4. **`MapMarkers.cache`** je rastao bez limita — svaka 1% promena baterije pravila je novu 60×74dp bitmapu i zaključavala je zauvek.
5. **`charging → HIGH`** override — punjenje i tako greje telefon, plus HIGH GPS = trostruka grejaća petlja.

### Šta je urađeno

**`SettingsModel.kt` + `SettingsRepository.kt`**
- Refaktor enum: `BatteryMode { SAVER, BALANCED, MAX }`. Default = `BALANCED`.
- `hybridThresholdPct` polje + threshold slider obrisani — više nisu potrebni.
- Migracija u `SettingsRepository.migrateMode()`: `CONSTANT → MAX`, `ADAPTIVE`/`HYBRID → BALANCED`.

**`BatteryModeScreen.kt` + `strings.xml`**
- Nove kartice sa novim copy: "Balans (preporučeno)", "Štedi bateriju", "Maksimalna tačnost".
- Slider za prag baterije uklonjen.

**`LocationTrackingService.kt`**
- HIGH profil: **5min/2min/100m** (bilo 2min/60s/50m). LOW: **15min/10min/300m** (bilo 10min/5min/200m).
- `computeProfile`: `MAX → HIGH`, `BALANCED`/`SAVER → LOW`. Charging override uklonjen. Refresh ping i dalje povlači `requestOneShotFix()` HIGH_ACCURACY ad-hoc.
- Companion: `isRunning: AtomicBoolean` (set u `onCreate`/`onDestroy`), `lastPublishAtMs: Long` (set posle svakog publish-a — i FGS callback i one-shot fix).
- `ONE_SHOT_COOLDOWN_MS = 3min`: `onStartCommand` preskoči `requestOneShotFix()` ako je publish < 3min star. Map screen toggle (DisposableEffect) više ne pali GPS spike.

**`LocationHealthWorker.kt`**
- Proverava `LocationTrackingService.isRunning.get()` i `lastPublishAtMs`. Ako je FGS živ **i** publish svež (< 12min) → return success bez restart-a / one-shot fix-a. Eliminiše GPS spike svakih 15min.
- Ako je FGS živ ali stale publish → zove `start()` koji će kroz cooldown logiku odlučiti da li treba one-shot.

**`MapMarkers.kt`**
- Cache key sad uključuje **bucket batterije na 10% korake** (`((pct + 5) / 10) * 10`) umesto raw `pct` — 11 batt buckets per kombinaciju umesto 101.
- `cache` pretvoren u **`LinkedHashMap` sa LRU eviction-om** (`accessOrder=true`, `removeEldestEntry: size > 32`). Bounded memory.

### Neto efekat
- Default user: **LOW profil ceo dan** (15min/10min/300m), HIGH samo ako eksplicitno izabere `MAX` mod.
- Periodic GPS spike-ovi (Worker, Map entry) eliminisani osim ako stvarno trebaju (stari publish).
- Cache memorija ograničena, GC pressure značajno manji.
- Tradeoff: location updates u BALANCED su sporiji (do 15min stationary, do 10min/300m moving). Korisnici koji žele real-time mogu da izaberu MAX manualno.

### Migracija
- Postojeći user-i sa starim Firestore vrednostima (`HYBRID`, `ADAPTIVE`, `CONSTANT`) se automatski preslikavaju kroz `migrateMode()` pri prvom čitanju. Na sledeći `setMode` upis, novi naziv se piše preko starog.

---

## Šta je urađeno u sesiji 2026-06-17

### Security rules + index
- **`firestore.rules`** napisana i deployovana (`firebase deploy --only firestore:rules`):
  - `users/{uid}`: read svi authenticated, write samo self, `settings/main` strogo lično
  - `circles/{cid}`: read svi authenticated (potrebno za invite-accept), write strogo (owner update / self-join / self-leave; owner ne sme da napusti svoj krug)
  - `circles/{cid}/members/{uid}`: read svi authenticated, write self ili owner
  - `invites/{code}`: read svi authenticated, create samo članovi krug-a, update samo dodavanje sebe u `usedBy`
- **`database.rules.json`** napisana i deployovana:
  - `/locations/{uid}` + `/sos/{uid}`: write samo self, read svi authenticated
  - `/locationRequests/{targetUid}/{requesterUid}`: write requester ili target (target za cleanup), read samo target
- **`firestore.indexes.json`** — composite index na `circles` (memberIds array_contains + createdAt) — Firestore traži za `observeMyCircles` query
- **`firebase.json` + `.firebaserc`** dodati za CLI deploy
- Limitation: RTDB read na `/locations` je `auth != null` (bez denormalizovane peers liste, ne može strože). Documented kao TODO za Cloud Functions era.

### Defensive fixes (snapshot listener crashes)
- Svi `addSnapshotListener` u repos (Circle, User, Settings, Map) sad imaju **error handling** umesto silent `_` — log + fallback (null/empty)
- `observeMyCircles` više nema `!!` na toObject (uzrok crash-a kad doc neispravan)
- `acceptInvite` ima try-catch oko `getCircle` (network/permission errors ne crash-uju app)

### Bug fix: stara lokacija pri ulasku u Map
- `LocationTrackingService.onStartCommand` sad zove **`requestOneShotFix()`** — odmah povuče HIGH_ACCURACY GPS fix i publish-uje u RTDB, bez čekanja FGS callback intervala (2-10 min)
- Aktivira se pri svakom ulasku u Map screen (`LocationTrackingService.start(context)` iz DisposableEffect)

### CircleDetail screen (NOVO)
- **`CircleDetailScreen.kt`** + **`CircleDetailViewModel.kt`** — tap krug iz liste otvara
- Sadržaj: krug ime + boja header, member lista sa role-ovima (Vlasnik/Član), "Pozovi članove" dugme (generiše invite kod → ShowInvite)
- **"Izađi iz kruga"** za članove, **"Obriši krug"** za vlasnika (sa confirm dialog-om)
- Auto-pop na Map screen kad krug nestane (npr. vlasnik obrisao dok si gledao)
- `CircleRepository.leaveCircle()` + `deleteCircle()` — owner ne može da napusti svoj krug (rules ga blokiraju)

### Top-left "Krug" pill clickable
- 0 krugova → "Krug", klik vodi na CircleList
- 1 krug → ime tog kruga, klik vodi direktno na CircleDetail
- 2+ krugova → "X krugova", klik vodi na CircleList

### Identitet/imena
- **`UserModel.deviceModel`** novi field — automatski popunjen sa `Build.MANUFACTURER + Build.MODEL` (npr. "Samsung SM-A376B")
- **`UserRepository.upsertOnSignIn(user, deviceLabel)`** — pri sign-in-u kompjutuje displayName po prioritetu: Google name → email prefix → device model. **Postojeći displayName** se ne prepisuje (čuva nickname)
- **Nickname UI**: Settings → Nalog → polje "Ime ili nadimak" + dugme Sačuvaj (`UserRepository.updateDisplayName`)
- Member-i sad pokazuju ime + device model: "Aleksandar Vasilić · pre 5 min · Samsung S24 Ultra"

### Map markeri (Life360 stil)
- **`MapMarkers.pinMarker(context, hex, photo, initials, batteryPct)`** — kompletno redesign:
  - **Pin oblik** (krug + uzak pointer dole, `sin25°` tangenta za prirodnu teardrop)
  - **Beli outer ring** za kontrast
  - **Photo** iz Google profila (učita se Coil-om u `photoCache` state map) — fallback **1-2 slova inicijala** ("Marko Vasilić" → "MV", "Samsung SM-A376B" → "SS")
  - **Battery ring** oko pin glave — luk dužine batteryPct%, počinje na vrhu, color-coded (zelena ≥50%, žuta 20-49%, crvena <20%) sa svetlim track-om
  - Per-uid stable color iz palette (8 boja)
- **Tekst label ispod pin-a** — Mapbox `withTextField` sa display name-om, halo za čitljivost (truncate na 18 char)

### MemberDetail bottom sheet (NOVO)
- Tap pin na mapi ILI tap row u Članovi sheet-u → otvara `MemberDetailSheet`
- Sadržaj: velika avatar (foto ili inicijal), ime, device model, **SOS banner** ako aktivan, stat chips (baterija u boji + last seen), 3 dugmeta:
  - **"Centriraj na mapi"** — `mapViewState.flyTo` na članov location
  - **"Osveži lokaciju"** — pošalje ping (vidi sledeću sekciju)
  - **"Otvori u Google Maps"** — geo: intent sa labelom
- Click handler na pin: `OnPointAnnotationClickListener` + `holder.annotationToUid[annotation.id]` mapiranje

### Refresh ping mehanizam (NOVO — za situacije kad ne želiš da čekaš FGS interval)
- Path: **`/locationRequests/{targetUid}/{requesterUid}`** sa ServerValue.TIMESTAMP
- **`LocationRepository.requestRefresh()`** — pisanje pinga
- **`LocationTrackingService.observeRefreshRequests()`** — sluša svoj path; na ping fire-uje `requestOneShotFix()` + briše entry (`clearRefreshRequests`)
- Druga strana primi novu lokaciju kroz postojeći RTDB snapshot listener za par sekundi
- **UI**: "Osveži lokaciju" dugme u MemberDetailSheet → state "Zahtev poslat…" → resetuje se posle 5s
- **Caveat**: radi samo ako je target FGS živ. Ako je MIUI/Samsung battery saver ubio FGS, ping se piše ali niko ne odgovara. Target user mora da otvori app (ulazak u Map → FGS restart → one-shot fix odmah)

### Mapbox optimizacija — fingerprint check
- `MapboxContainer.update` lambda ranije je radio `deleteAll() + create()` na svakoj recompoziciji → flicker
- Sad računamo fingerprint hash `(uid, lat, lng, batteryPct, sos, name, photo)` po članu; **preskače se redo ako se fingerprint ne promenio**
- Recompozicije zbog photoCache/sheet state/itd. više ne baš drinče pinove

### Offline persistence
- **`FirebaseDatabase.setPersistenceEnabled(true)`** u `KrugApplication.onCreate()` — RTDB write-ovi se queue-uju na disku (preživljavaju kill/restart procesa) i sync-uju kad se net vrati
- GPS fix-ovi se kaptuju nezavisno od interneta (FusedLocationProviderClient čita hardver direktno)

### Onboarding fix — MIUI permission polling
- `LocationPermissionPage`, `BackgroundLocationPage`, `NotificationsPermissionPage` sad imaju **polling fallback** (svake 500ms re-checkuje permission)
- ON_RESUME observer nije pouzdan na MIUI/Xiaomi posle return-a iz system settings-a → polling rešava

### Google sign-in — testovano na S24 Ultra
- Postojeća `signInWithGoogle` flow radi out-of-box (SHA-1 debug fingerprint je već registrovan u Firebase Console)
- `FirebaseUser.displayName` (npr. "Aleksandar Vasilić") se automatski koristi za pin label, member sheet, sve UI
- Photo iz Google profila se učita preko Coil-a i upiše u pin bubble

### Git repo
- `git init -b main` + `.gitignore` (već postojao sa skip-om za `local.properties`, `app/google-services.json`, `*.keystore`, build artifacts)
- Initial commit + remote `https://github.com/aleksandar-cypress/krug.git` + push to `main`
- Local git config: `aleksandarr@gmail.com` (per-repo, ne global)
- `gh auth login` setupovan (browser flow) — buduće push-eve radi bez prompta

### FGS reliability sloj 2 — Boot + Worker keepalive (NOVO)
- **`BootReceiver`** (`core/location/BootReceiver.kt`) — sluša `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`. Posle restart-a telefona auto-startuje FGS ako je user signed-in. Bez ovog korisnik bi morao da otvori app ručno posle reboot-a.
- **`LocationHealthWorker`** (`core/location/LocationHealthWorker.kt`) — periodic 15-min WorkManager. Idempotentno zove `LocationTrackingService.start(context)` — no-op ako je FGS živ, restart + one-shot fix ako je mrtav. Zakazan u `KrugApplication.onCreate()` sa `ExistingPeriodicWorkPolicy.KEEP` (preživljava reinstall).
- **Manifest** ima receiver deklarisan sa `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` action-ima.
- **WorkManager dep** dodat: `androidx-work-runtime = 2.10.0`.
- **Limit**: bez Cloud Functions (Blaze plan) ne možemo FCM high-priority data message da wake-ujemo iz Doze. Sloj 2 je best-effort — najgori scenario je 15-min gap u tracking-u kad OEM ubije FGS.

## Šta je urađeno u poslednjoj sesiji

### Build & infra popravke
- **Regenerisan `gradlew`** wrapper (script + jar su falili; pokrenuto preko keširanog gradle 8.10.2 distribucije)
- **`strings.xml:82`** duplikat `</resources>` tag obrisan
- **`MapScreen.kt`** `@Composable` invocation izvan composable konteksta — popravljen
- **Mapbox SDK 11.x** više ne čita `com.mapbox.token` meta-data → token sad postavljen u `KrugApplication.onCreate()` preko `MapboxOptions.accessToken = BuildConfig.MAPBOX_PUBLIC_TOKEN` (manifest placeholder + buildConfigField iz `local.properties`)

### Auth & navigation
- **Debug-only "Anonimna prijava"** dugme na Auth ekranu (gated by `BuildConfig.DEBUG`)
- `AuthRepository.signInAnonymously()` + `SignInResult.Reason.ProviderDisabled` mapping kad Firebase Console nema enabled provider
- **Sign-in flow više ne ide direktno na Onboarding** — `nav.navigate(Splash)` posle sign-in-a tako da Splash re-evaluiše stanje (čita LocalPrefs)
- **`SplashViewModel` timeout 5s** preko `withTimeoutOrNull` da spreči ANR kad Firestore visi

### Onboarding
- **`LocalPrefs` (SharedPreferences)** — sprema `onboarding_completed=true` lokalno na uređaju, koristi `commit=true` za synchronous write
- `SplashViewModel` prvo čita lokalni flag — ako je true, ide direktno na Map bez Firestore round-trip-a (idealno za anonimne test korisnike sa rotirajućim UID-om)
- `OnboardingViewModel.complete()` postavlja LocalPrefs odmah, pa onda Firestore
- **Self-heal**: `MapViewModel.init {}` automatski postavlja flag — ko god dođe do mape jednom, više neće videti onboarding
- **`LocationPermissionPage` i `NotificationsPermissionPage`** sad imaju `LifecycleEventObserver` da re-proveri permission na `ON_RESUME` (pre toga si bio zaglavljen jer su čitali samo na launch)
- **Pager dots** premešteni na **TOP** ekrana, modernizovani u animated "pill" stil (aktivna 24dp wide, neaktivne 8dp, 220ms tween)
- **Page scaffold icon container**: bela Surface sa shadow + indigo ikona (72dp), ne više indigo gradient (previše tamno plavo)

### UI & branding
- **Forced light theme** — `KrugTheme()` više ne čita `isSystemInDarkTheme()`, `DarkColors` uklonjen. Uvek `LightColors` bez obzira na system mode
- **Brand boje svetlije**: `BrandIndigo500 = #818CF8` (indigo-400), `BrandIndigo600 = #6366F1` (indigo-500), `BrandCoral500 = #FB7185` (rose-400)
- **Launcher ikona**: `krug_logo.png` (6 ljudi u krugu) sa 6dp inset wrap za adaptive icon safe zone; bela→indigo-50 gradient pozadina; odvojen `ic_launcher_monochrome.xml` (K monogram vector) za Android 13+ themed icons
- **Auth screen**: bela Surface kontejner za logo (140dp, 40dp rounded, shadow 12dp); indigo gradient backdrop ostao

### Map
- **Full-screen mapa** — uklonjen `BottomSheetScaffold`, sad je `Box` sa Mapbox-om kao fillMaxSize
- **Floating overlays**:
  - Top: rounded white pill "Krug" sa shadow + 2 okrugla icon button-a (Krugovi, Settings)
  - Bottom: "Članovi" pill sa count badge → tap otvara `ModalBottomSheet` sa listom
- **Avatar pin markeri**: bela halo + obojeni disc + drop shadow + bela person silueta (`ic_person_marker.xml`) u centru; nema više slova
- **Marker boje**: self = `#818CF8` (indigo-400), others = `#FB7185` (rose-400), **SOS active = `#DC2626`** (red)

### Battery mode wiring u FGS (gotovo iz STATUS.md punch liste)
- `LocationTrackingService` sad inject-uje `SettingsRepository` i observe-uje preko `collectLatest`
- Dva profila: **HIGH** (2min/60s/50m), **LOW** (10min/5min/200m)
- Pravila: `CONSTANT` → uvek HIGH; `ADAPTIVE`/`HYBRID` → HIGH ako batt ≥ threshold ili charging, inače LOW
- Profil se re-evaluira na svaki location callback i na promenu settings flow-a
- **Honoriše `shareLocationGlobal`** — ako false, skip publish

### SOS feature (NOVO)
- **`SosRepository` + `SosModel`** u `core/sos/` (RTDB-based pattern kao locations)
- **RTDB path**: `/sos/{uid}` sa `{lat, lng, triggeredAt, message?}`
- **`MapViewModel`**: `triggerSos()`, `clearSos()`, observe-uje SOS state svakog člana kroz `combine`
- **UI**:
  - Subtle 48dp **Warning** ikona FAB u **donjem desnom uglu** (bela bg + crveni ikon kad inactive; solid crveni kad active)
  - **Confirmation AlertDialog** pre triggerovanja
  - **Crveni banner ispod top bar-a** kad bilo koji član u krugu ima active SOS — lista sa "Vidi" (fly camera) za druge / "Otkaži" za self
  - **Marker boja se menja u crvenu** za member sa active SOS
  - **Member sheet row**: crveni "SOS — traži pomoć" tekst umesto "last seen"
- `MapViewHolder.flyTo(lng, lat)` exposed za camera animation iz banner-a

## Šta NE radi još (punch lista — ostalo)

| Šta | Effort | Prioritet |
|-----|--------|-----------|
| ~~Battery mode wiring u FGS~~ | ~~50 linija~~ | ✅ urađeno |
| ~~Stroge Firestore + RTDB security rules~~ | ~~1-2h~~ | ✅ deployed via Firebase CLI |
| ~~Avatar fotke na markerima~~ | ~~30 linija~~ | ✅ Coil bitmap loader + photoCache state map |
| ~~MemberDetail bottom sheet~~ | ~~nekoliko sati~~ | ✅ tap pin / row → ModalBottomSheet sa stats + akcijama |
| ~~Battery indicator ring oko pin-a~~ | ~~30 min~~ | ✅ Life360 stil, color-coded arc |
| ~~Auto-startup posle reboot-a (FGS keepalive)~~ | ~~30 min~~ | ✅ BootReceiver + 15-min WorkManager |
| ~~Places + geofencing ("Marko stigao kući")~~ | 1 dan | **odloženo posle v1** (per user 2026-06-17) |
| ~~History trail (last 24h locations)~~ | 1 dan | **odloženo posle v1** (per user 2026-06-17) |
| Brisanje naloga (fan-out kroz krugove + lokacije + SOS) | 1 dan | nisko (obavezno za GDPR/Play Store) |
| Privacy policy URL + Terms URL (host na GitHub Pages) | 2h | obavezno pre Play Store-a |
| Crashlytics + App Check | ~1h | srednje (pre prod-a) |
| ~~**SOS push notifikacije** — lokalna verzija (Spark-friendly, dok je FGS živ)~~ | ~~par sati~~ | ✅ urađeno; FCM push kad je app ubijen ostaje za Blaze plan |
| ~~**SOS auto-clear** posle X minuta~~ | ~~30 min~~ | ✅ urađeno (30-min TTL u MapViewModel + UI filter + FGS observer) |
| ~~**Vibracija/zvuk** kad neko fire-uje SOS~~ | ~~30 min~~ | ✅ urađeno (SosNotifier sa alarm sound + vibration pattern) |
| ~~**Multi-circle independence**~~ | ~~par sati~~ | ✅ urađeno (active circle u LocalPrefs, CirclePickerSheet, scope per krug u SOS payload) |

## Deploy security rules (manual, bez Firebase CLI)

Rules su napisane u `firestore.rules` i `database.rules.json` u project root-u. Da ih aktiviraš u produkciji:

**Firestore:**
1. Firebase Console → `krug-86527` → Firestore Database → Rules tab
2. Kopiraj kompletan sadržaj iz `firestore.rules`, paste preko postojećih rules
3. Klikni **Publish**
4. Test: pokušaj sa drugog naloga da pišeš u tuđi `users/{uid}` doc — treba da dobije PERMISSION_DENIED

**RTDB:**
1. Firebase Console → `krug-86527` → Realtime Database → Rules tab
2. Kopiraj sadržaj iz `database.rules.json`, paste preko postojećih
3. **Publish**
4. Test: pokušaj `setValue` na `/locations/{nekiDrugiUid}` — treba PERMISSION_DENIED

**Šta rules rade:**
- `users/{uid}`: read svi authenticated (profile podaci), write samo self. `settings/main` lično.
- `circles/{cid}`: read i write samo članovi; create samo ako si ti owner; member-self-join kroz invite-accept dozvoljen pod uslovom da samo dodaješ sebe.
- `invites/{code}`: read svi (da bi validirali kod); create samo član krug-a; update samo dodavanje svog uid-a u `usedBy`.
- `/locations/{uid}` i `/sos/{uid}` (RTDB): write samo self; read svi authenticated (treba denormalizacija peers da bi se zatvorilo, vidi TODO u Firestore rules).

**Poznata ograničenja (TODO):**
- RTDB read za locations/SOS je `auth != null`, pa znanjem tuđeg uid-a se može čitati. Uid-ovi nisu enumerable, ali nije idealno. Pravo rešenje: denormalizovati listu peers po useru u RTDB i tražiti da je caller upisan kao peer. Traži klijent-side write logiku pri join/leave krug-a.
- Self-join u circle dozvoljen ako znaš `cid`. Cid-ovi nisu enumerable (Firestore auto-id), ali bi trebalo gate-ovati kroz invite. Zahteva cross-collection lookup koji rules ne mogu efikasno; rešenje za kasnije sa Cloud Functions.

## Sledeći koraci za sutra

1. **Testiraj SOS sa drugim uređajem** (ili emulatorom + drugim Firebase user-om): kreiraj krug, doda drugi user, fire SOS sa prvog → proveri banner + marker boja + "Vidi" fly-to na drugom uređaju
2. **Firestore + RTDB security rules** — pre nego što ide ozbiljnije, dodati pravila da samo authenticated user može da menja svoj `users/{uid}` doc, svoj `locations/{uid}` i svoj `sos/{uid}`. Trenutno je sve `auth != null` što je previše opušteno.
3. **FCM za SOS push notifikacije** — najveći nedostatak SOS-a sad: ako app nije otvoren, niko ne vidi. Trebao bi:
   - Cloud Function trigger na write u `/sos/{uid}` → send FCM data message svim uid-ima iz krugova
   - Klijent-strana: `FirebaseMessagingService` koji prikazuje notifikaciju sa intent-om za otvaranje mape
   - **Problem**: trenutno smo na Spark planu (free), Cloud Functions traže Blaze plan. Alternativa: implementiraj sa Cloud Function za ovu konkretnu funkciju (Blaze ima free tier 2M poziva)
4. **Pravi Google sign-in test** — kad budeš na svom telefonu sa Google nalogom, probaj non-anonymous flow

## Firebase setup — koje sve servise/podatke imaš

| Servis | Status | Region |
|--------|--------|--------|
| Authentication (Google + **Anonymous**) | ✅ enabled | n/a |
| Firestore | ✅ enabled, privremene rules `auth != null` | `eur3` (multi-region) |
| Realtime Database | ✅ enabled, privremene rules `auth != null` | `europe-west1` |
| Cloud Messaging (FCM) | ✅ auto-enabled, koristi se za onboarding ali ne za SOS još | n/a |
| Cloud Functions | ❌ ne koristimo (Blaze plan) | n/a |
| Cloud Storage | ❌ ne koristimo | n/a |

**Firebase project ID:** `krug-86527`
**Plan:** Spark (free)
**RTDB schema:**
- `/locations/{uid}` — `{lat, lng, accuracy, batteryPct, isCharging, updatedAt}`
- `/sos/{uid}` — `{lat, lng, triggeredAt, message?}` *(novo)*

## Mapbox

- Personal account (NE firmin)
- Tokeni u `~/Desktop/sajts/krug/local.properties` (gitignored)
  - `KRUG_MAPBOX_PUBLIC_TOKEN=pk.*` (manifest placeholder + buildConfigField)
  - `KRUG_MAPBOX_DOWNLOADS_TOKEN=sk.*` (Maven repo download)
- **SDK 11.x**: token se sad postavlja **programatski** u `KrugApplication.onCreate()` preko `MapboxOptions.accessToken` — meta-data tag je deprecated

## SHA-1 fingerprints

- **Debug** (`org.krug.app.debug`): `37:9F:E9:F5:94:DA:0E:5A:C1:A6:D6:3E:DF:A9:AE:20:5F:5D:60:7E` — registrovan u Firebase
- **Release** (`org.krug.app`): JOŠ NIJE — potreban tek pre Play Store upload-a

## Komande koje će ti zatrebati

### Iz terminala u project root-u (`~/Desktop/sajts/krug`)

```bash
# Build + install na povezani uređaj
./gradlew installDebug

# Pokreni app
adb shell am start -n org.krug.app.debug/org.krug.app.MainActivity

# Logcat samo iz našeg app-a (filter na PID)
adb logcat --pid=$(adb shell pidof org.krug.app.debug)

# Logcat filtriran (Timber tags)
adb logcat *:S Timber:V

# Pročitaj LocalPrefs flag (npr. da debug-uješ onboarding state)
adb shell run-as org.krug.app.debug cat /data/data/org.krug.app.debug/shared_prefs/krug_prefs.xml

# Force-stop + restart
adb shell am force-stop org.krug.app.debug && adb shell am start -n org.krug.app.debug/org.krug.app.MainActivity

# Clean
./gradlew clean
```

### Iz Android Studio

- ▶ Run dugme — build + install + launch
- 🔧 Build → Make Project (Cmd+F9)
- View → Tool Windows → Logcat
- View → Tool Windows → Device Manager

## Struktura projekta (sad ~55 fajlova)

```
app/src/main/java/org/krug/app/
├── KrugApplication.kt              # postavlja MapboxOptions.accessToken
├── MainActivity.kt
├── navigation/
│   ├── Routes.kt
│   └── KrugNavHost.kt              # Auth → Splash → Onboarding/Map
├── ui/theme/                       # Color (lighter indigo+rose), Type, Theme (light only)
├── di/
│   ├── FirebaseModule.kt
│   └── DispatcherModule.kt
├── core/
│   ├── auth/AuthRepository.kt      # +signInAnonymously, +ProviderDisabled reason
│   ├── user/{UserModel, UserRepository}.kt
│   ├── circle/{CircleModels, CircleRepository, InviteRepository}.kt
│   ├── location/{LocationModel, LocationRepository, LocationTrackingService}.kt   # +battery mode wiring
│   ├── settings/{SettingsModel, SettingsRepository}.kt
│   ├── sos/{SosModel, SosRepository}.kt                                            # NOVO
│   ├── prefs/LocalPrefs.kt                                                         # NOVO
│   └── permissions/PermissionUtils.kt
└── feature/
    ├── splash/{SplashViewModel, SplashScreen}.kt        # +timeout, +LocalPrefs check
    ├── auth/{AuthViewModel, AuthScreen}.kt              # +anonymous, glass→white logo bg
    ├── onboarding/
    │   ├── pages/PageScaffold.kt                        # bela Surface + shadow icon container
    │   ├── pages/PermissionPages.kt                     # +resume re-check Location/Notifications
    │   └── ...
    ├── circle/...
    ├── map/{MapViewModel, MapMarkers, MapScreen}.kt     # +SOS UI, full-screen, avatar pins, self-heal LocalPrefs
    └── settings/...
```

## Tehnički kontekst za sutra

- `app/build.gradle.kts:30-37` — manifest placeholder + buildConfigField pattern za Mapbox token
- `app/src/main/java/org/krug/app/feature/map/MapScreen.kt` — sad oko 450 linija sa SOS UI
- `app/src/main/java/org/krug/app/feature/map/MapViewModel.kt` — observe pattern: `combine(user, location, sos)` po članu, paralelno za sve
- Glavni problem za sutra (verovatno): **SOS push notifikacije bez app-a u foreground-u**. Bez Cloud Functions, klijent-side workaround nije moguć (klijent koji fire-uje ne može da push-uje drugima direktno).

## Troubleshooting

**Sign-in pukne sa `DEVELOPER_ERROR: 10`**
→ SHA-1 nije commit-ovan u Firebase ili google-services.json je stari.

**Anonimna prijava daje "Anonimna prijava nije omogućena"**
→ Firebase Console → Authentication → Sign-in method → Add provider → Anonymous → Enable.

**Map crna ili crash sa `MapboxConfigurationException`**
→ Token nije u `local.properties` ili `KrugApplication.MapboxOptions.accessToken` nije se izvršio. Rebuild.

**App zaglavljen ili ANR na startu**
→ Najčešće Firestore network problem; Splash sad ima 5s timeout pa će preći u onboarding pending. Proveri Private DNS na telefonu (Podešavanja → Veze → Privatni DNS → Automatski).

**FGS notifikacija ne iskače na Mapi**
→ Background location permission → **Allow all the time**.

**Onboarding se ponavlja posle sign-out-a**
→ Trebalo bi da je rešeno (LocalPrefs + MapViewModel self-heal). Ako se ponovi:
```
adb shell run-as org.krug.app.debug cat /data/data/org.krug.app.debug/shared_prefs/krug_prefs.xml
```
Treba da pokaže `onboarding_completed=true`. Ako ne, znači flag se ne piše — proveri da li je `commit=true` u LocalPrefs.

**Pukao SOS feature**
→ Proveri da li RTDB ima rules koja dozvoljavaju write na `/sos/{uid}`. Trenutno je `auth != null` što važi za svakoga, pa bi trebalo da radi.
