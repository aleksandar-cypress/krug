# Play Store listing: Serbian locale (sr)

> Direktno kopiraj u Play Console → Store presence → Main store listing → Serbian.
> Limiti su iz Play Console-a. Datum poslednjeg ažuriranja: 2026-06-25.

---

## App name (max 30 znakova)

```
Krug
```
*(4 znaka)*

---

## Short description (max 80 znakova)

```
Lokacija porodice u realnom vremenu. SOS. Bez reklama i prodaje podataka.
```
*(73 znaka)*

**Alternative** (ako želiš drugi naglasak):

- `Privatno deljenje lokacije sa porodicom. SOS hitno. Bez reklama.` (64)
- `Deljenje lokacije porodice. Battery-smart. Bez reklama ni naloga.` (64)
- `Vaša porodica na mapi. SOS u jednom kliku. Privatno i bez reklama.` (66)

---

## Full description (max 4000 znakova)

```
Krug je jednostavna aplikacija za deljenje lokacije sa porodicom i prijateljima u realnom vremenu. Kreiraš mali "krug" ljudi kojima veruješ i vidite jedni druge na mapi.

Bez reklama. Bez prodaje podataka. Bez nepotrebnih funkcija.

🗺  ŠTA RADI

• Lokacija u realnom vremenu: vidi gde su članovi tvog kruga na mapi, sa nivoom baterije i statusom kretanja
• SOS dugme: pošalji hitan signal sa lokacijom svim članovima jednim klikom
• Privatni krugovi: kreiraš krug, šalješ pozivnicu, samo članovi vide jedni druge
• Pauza kad ti treba privatnost: isključiš deljenje lokacije jednim klikom, član ostaje u krugu ali tvoja pozicija nije vidljiva
• Putna razdaljina: koliko ti treba kolima do nekog člana

🔋 PAMETNO ZA BATERIJU

Krug detektuje da li hodaš, voziš ili miruješ i prilagođava koliko često čita GPS:
• Vožnja → češći fix, bolja preciznost
• Mirovanje → ređi fix, manje baterije

Foreground service garantuje da deljenje radi i kad je telefon zaključan, ali bez nepotrebnog trošenja baterije.

🔒 PRIVATNOST PRE SVEGA

• Tvoju lokaciju vide ISKLJUČIVO članovi tvojih krugova, niko drugi
• Bez reklama, bez praćenja, bez prodaje podataka oglašivačima ili data brokerima
• Podaci se čuvaju u EU regionu (Google Firebase, region Belgija + multi-region EU)
• HTTPS/TLS za svu komunikaciju
• Možeš u svakom trenutku da isključiš deljenje ili obrišeš nalog
• Bez profila društvenih mreža, bez "feed-a", bez game-ifikacije

📍 KAKO RADI

1. Instaliraj Krug i prijavi se Google nalogom (ili anonimno)
2. Kreiraj svoj krug ("Porodica", "Prijatelji", "Posao")
3. Pozovi članove sa 6-cifrenim kodom ili linkom
4. Vidite se na mapi

🆘 SOS: VAŽNO

SOS dugme šalje signal ČLANOVIMA TVOG KRUGA, ne hitnim službama. U slučaju stvarne opasnosti pozovi 112 (jedinstveni broj za hitne situacije) ili 192/193/194 (policija/vatrogasci/hitna pomoć).

✨ ZAŠTO KRUG, A NE NEKA DRUGA APLIKACIJA

• Nema reklama: projekat je posvećen privatnosti, ne monetizaciji preko podataka
• Pravljen u Srbiji, srpski jezik kao prvi (i engleski podržan)
• Minimalan dizajn: fokus na lokaciju, ne na 50 dodatnih funkcija koje ti ne trebaju
• Pametna baterija: aktivnost-aware tracking koji ne troši bateriju kad nema potrebe
• Open development: kod je javno vidljiv na GitHub-u

📋 PERMISIJE I ZAŠTO

• Lokacija (precizna i u pozadini): osnovna funkcija deljenja lokacije
• Obaveštenja: SOS signali članova i status deljenja
• Prepoznavanje aktivnosti: opciono, za pametniju potrošnju baterije
• Izuzeće od optimizacije baterije: da OEM telefon ne ubije deljenje lokacije

Sve permisije se mogu povući u sistemskim podešavanjima Android-a.

🧪 BETA STATUS

Ovo je rana verzija (v0.1.0). Funkcije se aktivno razvijaju. Tvoja povratna informacija je dragocena, javi nam šta ti se sviđa, šta ne radi, i šta ti fali.

📧 KONTAKT

aleksandarr@gmail.com

🌐 PRIVATNOST I USLOVI

Politika privatnosti: https://krugapp.com/privacy.html
Uslovi korišćenja: https://krugapp.com/terms.html
```

*(oko 2700 znakova)*

---

## What's new (max 500 znakova) za v0.1.0

```
Prva javna verzija Krug aplikacije.

• Deljenje lokacije u realnom vremenu sa privatnim krugovima
• SOS dugme sa hitnim signalom svim članovima kruga
• Pametna baterija (vožnja/mirovanje detekcija)
• Pauza deljenja kad ti treba privatnost
• Putna razdaljina između članova
• Srpski i engleski jezik

Javite nam šta vam fali ili šta treba popraviti: aleksandarr@gmail.com
```

*(oko 460 znakova)*

---

## Translation notes

- "Krug" se NE prevodi (to je brand)
- "Circle" → "krug" (slobodno u tekstu)
- "Tracker" izbegavamo (negativna konotacija); koristimo "deljenje lokacije"
- "Family" → "porodica" (osnovno); "loved ones" → "voljeni ljudi" (retko)

## Keywords koje treba da se pojave u tekstu (za Play Store SEO)

- deljenje lokacije ✓
- porodica ✓
- mapa ✓
- SOS ✓
- krug ✓
- baterija ✓
- privatno / privatnost ✓

## TODO pre submit-a

- [ ] Kupiti domain (krugapp.com) i deploy-ovati privacy.html + terms.html
- [ ] Update URL-ove u full description-u kad domain stigne
- [ ] Update AboutScreen.kt PRIVACY_URL / TERMS_URL na nove URL-ove
- [ ] Verifikovati char count u Play Console preview-u (UTF-8 ć/š/đ/č troše 2 byte-a, mada Play Console računa karaktere ne byte-ove)
