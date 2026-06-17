# Krug — Status & Continue Guide

Snimljeno na kraju sesije.

## Gde smo stali (2026-06-17)

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
