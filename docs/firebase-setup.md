# Firebase Setup — korak po korak

Pratiti redom. Sve sa **privatnim Gmail-om** (ne firminim).

---

## 1. Napravi Firebase projekat

1. Otvori https://console.firebase.google.com
2. **Add project** → ime: `Krug`
3. **Google Analytics**: ostavi enabled (besplatno, korisno kasnije)
4. **Analytics account**: kreiraj novi ili odaberi default → **Create project**
5. Sačekaj ~30s da se projekat napravi

**Provera plan-a**: dole levo klikni "Spark" → potvrdi da si na **Spark (Free)** planu. Ako te traži da pređeš na Blaze, **odbij** — Spark je dovoljan za MVP.

---

## 2. Registruj **debug** Android app

Krug ima dva applicationId-a (debug + release) jer u `build.gradle.kts` imamo `applicationIdSuffix = ".debug"`. Oba moraju biti registrovana zasebno u Firebase-u.

1. U Firebase Console projektu klikni **Android ikonu** (Add app)
2. **Package name**: `org.krug.app.debug`
3. **App nickname**: `Krug Debug`
4. **SHA-1**: ostavi prazno za sada (vraćamo se ispod) → **Register app**
5. Skini **`google-services.json`** → premesti u:
   ```
   ~/Desktop/sajts/krug/app/google-services.json
   ```
6. Sledeći koraci u dijalogu (SDK setup) — preskoči, već je sve u našem Gradle-u → **Continue → Continue → Continue to console**

---

## 3. Registruj **release** Android app

1. U Project Overview → **Add app** → Android
2. **Package name**: `org.krug.app`
3. **App nickname**: `Krug Release`
4. SHA-1 prazno → **Register app**
5. **Skini novi `google-services.json`** — taj fajl sada ima OBA app-a u sebi → zameni postojeći u `app/google-services.json`
6. Skip rest → Continue to console

> Posle ovog koraka `app/google-services.json` sadrži oba paketa. Tako je ispravno.

---

## 4. Dodaj SHA-1 (za Google Sign-In)

Google sign-in NEĆE raditi bez SHA-1 fingerprint-a. Treba ti debug SHA-1, a kasnije i release.

### Debug SHA-1

Iz terminala:

```bash
keytool -list -v -alias androiddebugkey \
  -keystore ~/.android/debug.keystore \
  -storepass android -keypass android | grep SHA1
```

Kopiraj liniju `SHA1: AA:BB:CC:...`

U Firebase Console:
1. Project Overview → klik na ⚙️ (gear icon) → **Project settings**
2. Scroll dole → **Your apps** → odaberi **Krug Debug**
3. **Add fingerprint** → paste-uj SHA-1 → Save

### Release SHA-1

Tek kada budeš pravio production keystore (za Play Store). Za sada **preskoči**.

> Posle dodavanja SHA-1, ponovo skini `google-services.json` — sada sadrži updated OAuth client config. **Zameni postojeći fajl** u `app/`.

---

## 5. Uključi Authentication

1. Levi sidebar → **Build → Authentication**
2. **Get started**
3. Tab **Sign-in method** → klikni **Google**
4. **Enable** toggle → ON
5. **Project support email**: tvoj privatni Gmail
6. **Save**

(Ne uključuj Email/Password još — radimo to u sledećoj iteraciji.)

### Test korisnik

1. Tab **Users** je za sada prazan. To je OK — automatski će se popuniti kada se prvi put prijaviš kroz app.

---

## 6. Uključi Firestore

1. Levi sidebar → **Build → Firestore Database**
2. **Create database**
3. **Location**: `eur3 (europe-west)` — multi-region, najbliži, **NEPROMENLJIVO** posle kreiranja
4. **Start in production mode** (lockdown) → **Create**

### Privremena security rule — sve dozvoli prijavljenima

Tab **Rules** → zameni sa:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

→ **Publish**

> Ovo je **privremeno** za development. Pre Play Store launch-a vraćamo se i pišemo strogi rules — videti `docs/security-rules.md` (TODO).

---

## 7. Uključi Realtime Database

1. Levi sidebar → **Build → Realtime Database**
2. **Create Database**
3. **Location**: `Belgium (europe-west1)` — **NEPROMENLJIVO**
4. **Start in locked mode** → **Enable**

### Privremena rule — sve dozvoli prijavljenima

Tab **Rules**:

```json
{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null"
  }
}
```

→ **Publish**

---

## 8. Cloud Messaging (FCM)

Već je automatski uključen kada si registrovao Android app. Ne radi se ništa dodatno.

---

## 9. Provera google-services.json

Otvori `~/Desktop/sajts/krug/app/google-services.json` i potvrdi:

```json
{
  "project_info": { ... },
  "client": [
    { "client_info": { "android_client_info": { "package_name": "org.krug.app.debug" } }, ... },
    { "client_info": { "android_client_info": { "package_name": "org.krug.app" } }, ... }
  ]
}
```

Oba paketa moraju biti u `client` array-u. Ako fali release ili debug — vrati se na korake 2 ili 3.

Takođe, posle koraka 4 (SHA-1), za debug klijent u `oauth_client` treba da postoji entry sa `client_type: 1` (Android, sadrži cert hash). Ako nema → nedostaje SHA-1.

---

## 10. Prvi build

```bash
cd ~/Desktop/sajts/krug
gradle wrapper --gradle-version 8.10.2   # samo prvi put
./gradlew assembleDebug
```

Ako sve prođe, instaliraj na uređaj:

```bash
./gradlew installDebug
```

Pokreni app → Splash → Auth → klik "Continue with Google" → trebalo bi da iskoči native Google account picker → izabereš nalog → app pređe na Onboarding screen.

U Firebase Console **Authentication → Users** trebalo bi da se pojavi novi red.
U **Firestore → users** trebalo bi da postoji dokument sa tvojim UID-em.

---

## Šta NIJE potrebno u MVP-u

- ❌ App Check
- ❌ Cloud Functions (Blaze plan)
- ❌ Cloud Storage
- ❌ Hosting
- ❌ Remote Config
- ❌ Crashlytics (možeš dodati kasnije, free je)

---

## Troubleshooting

**"Sign in failed: 10" (DEVELOPER_ERROR)**
→ SHA-1 nije dodat ili `google-services.json` nije osvežen posle dodavanja SHA-1.
→ Re-skini `google-services.json` posle koraka 4 i rebuild.

**"Sign in failed: 12500"**
→ Google Play Services nije instaliran/zastareo na emulator-u.
→ Koristi emulator sa Google Play image-om (ne samo Google APIs).

**"PERMISSION_DENIED" iz Firestore-a**
→ Korisnik nije ulogovan ili rules nisu publish-ovani.
→ Re-publish privremeni rule iz koraka 6.

**Build error: "google-services.json not found"**
→ Fajl mora biti u `app/google-services.json`, ne u root-u projekta.

**Build error: "default_web_client_id not found"**
→ `google-services.json` ne sadrži OAuth web client.
→ U Firebase Console → Authentication → Sign-in method → Google → Enable (korak 5). Onda re-skini json.
