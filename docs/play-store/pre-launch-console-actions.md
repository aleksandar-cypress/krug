# Pre-launch Console akcije (manual)

Sve što app kod ne može sam, mora ručno u Firebase/Play Console pre nego što internal
testing track ide na public release.

## 1. App Check enforcement (Firestore + RTDB + FCM)

Trenutno status: **Unenforced (monitoring)** — App Check sluša ali ne odbija requests.
Pre javnog launch-a, prebaciti na **Enforced** da samo legitimni app instance (Play
Integrity attestation) mogu da čitaju/pišu u backend.

### Pre prebacivanja

1. Build release AAB i instaliraj na 2-3 uređaja (S24, A37, Xiaomi)
2. Otvori Firebase Console → **App Check** → APIs tab
3. Sluša ~24h: u "Verified requests" treba da bude >99% za svaki API
4. Ako ima "Unverified requests" — debug:
   - Da li debug build (Debug provider) negde mimo radi
   - Da li je release SHA-256 fingerprint registrovan u Play Integrity provider
   - Da li telefon ima ažuriran Google Play Services

### Prebacivanje na Enforced

1. Firebase Console → **App Check** → APIs tab
2. Za svaku tabelu klikni **⋮ → Enforce**:
   - **Cloud Firestore** → Enforce
   - **Realtime Database** → Enforce
   - **Cloud Messaging** (kad FCM bude u upotrebi)
3. Monitor Crashlytics 24-48h za PERMISSION_DENIED bursts

### Rollback ako nešto pukne

Console → APIs → ⋮ → Unenforce (instantaneous, no propagation delay).

## 2. Firestore + RTDB rules deploy

Provera da production rules iz repo-a su deployed:

```bash
firebase deploy --only firestore:rules
firebase deploy --only database
```

Console → Firestore Database → Rules tab — proveri da timestamp se poklapa sa zadnjim
deploy-em.

## 3. Play Console Data Safety form

Sve odgovore već imamo u `STATUS.md` (sekcija E "Data safety form audit"). Preneti u
Play Console → App content → Data safety:

- Does your app collect or share required user data? → **YES**
- All data encrypted in transit? → **YES**
- Users can request data deletion? → **YES**
- Data types: tabela iz STATUS.md prebaciti red po red

## 4. Internal testing track upload

1. Play Console → Release → Testing → Internal testing
2. Create new release
3. Upload signed AAB iz `app/build/outputs/bundle/release/app-release.aab`
4. Release notes (SR + EN) iz `docs/play-store/listing-*.md` "What's new" sekcija
5. Add testers:
   - Aleksandar (`krugappteam@gmail.com`)
   - Jelena
   - Plus 3-5 prijatelja/porodice
6. Share opt-in URL iz Play Console-a sa testerima

## 5. Privacy + Terms URL

Posle domain kupovine (`krugapp.com`):

1. Cloudflare/Porkbun DNS → Firebase Hosting (besplatno)
2. `firebase init hosting` → `docs/` directory
3. `firebase deploy --only hosting`
4. Provera: `krugapp.com/privacy` i `krugapp.com/terms` otvaraju ispravan HTML
5. Update `AboutScreen.kt` URL-ove sa `aleksandar-cypress.github.io/krug/...` na `krugapp.com/...`
6. Rebuild AAB sa novim URL-ovima
7. Play Console → Store listing → Privacy policy URL → `https://krugapp.com/privacy`

## 6. SHA-256 release fingerprint u Play Integrity

Verifikacija da je release SHA-256 (`67:FE:3A:7B:...`) dodat u Firebase Console → App
Check → Play Integrity provider. Bez ovog, release build dobija "Unverified requests"
u App Check dashboard-u.
