# Krug — Status & Continue Guide

Snimljeno na kraju sesije.

## Gde smo stali (2026-06-22, dvanaesta sesija — UI polish + Samsung S24 dodato u flotu)

Repo public: **https://github.com/aleksandar-cypress/krug**, poslednji commit `8c53d49`.
Firebase rules: Firestore + RTDB deployovane.
**Flota uređaja**: A37 (SM-A376B), Xiaomi Mi 11 (21081111RG), Samsung S24 Ultra (SM-S928B) — sve tri sa najnovijim build-om.

**Health stanja**: build i dalje uredan (debug + release), 36 unit testova zelenih, FGS + Crashlytics arhitektura nepromenjena.

## Dvanaesta sesija (2026-06-22) — UI polish + brand sheets + bug fixes

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
- Member nickname per circle ("Mama" u family, "Mira" u prijateljskom)
- Empty members CTA — "Niko se nije pridružio — pošalji pozivnicu" u MembersSheet kad si jedini
- Battery saver banner na Map kad user u SAVER modu
- Crashlytics breadcrumbs za key akcije
- Improved offline banner — "Offline — poslednje ažuriranje pre X min"
- Release signing + Play Store internal testing track (production korak)
- Google reauth flow za delete-account (one-step umesto sign-out/sign-in/retry)
- FCM SOS push za ubijeni app scenario (treba Blaze plan ~$1-3/mesec)
- Places/Geofencing v1 (per-place "Obavesti članove" toggle, child mode tie-in)
- Sound test za SOS u Settings (verifikacija pre stvarne hitnoće)
- History trail (24h breadcrumbs po članu)
- Refresh boost spam cap (zaštita ako peer spamuje "Osveži lokaciju")
- "Podrži razvoj" link kad budemo imali više korisnika (Buy Me a Coffee/PayPal)

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
- Member nickname per circle ("Mama" u family, "Mira" u prijateljskom)
- Empty members CTA — "Niko se nije pridružio — pošalji pozivnicu" u MembersSheet kad si jedini
- Battery saver banner na Map kad u SAVER modu
- Crashlytics breadcrumbs za key akcije
- Improved offline banner — "Offline — poslednje ažuriranje pre X min"
- Release signing + Play Store internal testing track
- Google reauth flow za delete-account
- FCM SOS push (treba Blaze plan ~$1-3/mesec)
- Places/Geofencing (per-place "Obavesti članove" toggle, default = on za isChild)
- Sound test za SOS u Settings
- History trail (last 24h breadcrumbs)
- Member trail / Places — uz Blaze ($)
- "Podrži razvoj" link (čekamo više korisnika)

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
- Member nickname per circle ("Mama" u family, "Mira" u prijateljskom)
- Release signing + Play Store internal testing
- Google reauth flow za delete-account
- "Podrži razvoj" link (čekamo više korisnika)
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
