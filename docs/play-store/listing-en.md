# Play Store listing: English locale (en-US)

> Direct copy into Play Console → Store presence → Main store listing → English (United States).
> Limits from Play Console. Last updated: 2026-06-25.

---

## App name (max 30 chars)

```
Krug: Family Circle
```
*(19 chars)*

**Why the compound name**: "Krug" means "circle" in Serbian, meaningless to English speakers on its own. "Family Circle" tagline gives an immediate hint of what the app is for, while keeping "Krug" as the brand anchor. Same approach as "Slack: Workspace" or "Strava: Run & Ride".

---

## Short description (max 80 chars)

```
Family location sharing. Real-time map. SOS alerts. No ads, no data sale.
```
*(73 chars)*

**Alternatives** (if you want a different angle):

- `Private family location sharing. SOS button. Battery-smart. No ads.` (67)
- `Where your loved ones are, in real time. SOS. Ad-free and private.` (66)
- `Family on the map. SOS in one tap. Privacy-first, no tracking.` (62)

---

## Full description (max 4000 chars)

```
Krug is a simple, private app for sharing your real-time location with family and close friends. Create a small "circle" of people you trust and see each other on a map.

No ads. No data brokers. No bloat.

🗺  WHAT IT DOES

• Real-time location: see where your circle members are on a map, with battery level and motion status
• SOS button: send an emergency signal with your location to all members in one tap
• Private circles: create a circle, send an invite, only members see each other
• Pause when you need privacy: toggle location sharing off in one tap; you stay in the circle but your position isn't visible
• Driving distance: see how far each member is by car

🔋 BATTERY-SMART TRACKING

Krug detects whether you're walking, driving, or still and adjusts how often it reads GPS:
• Driving → more frequent fixes, better accuracy
• Still → fewer fixes, less battery

A foreground service keeps sharing alive even when your phone is locked, but without unnecessary battery drain.

🔒 PRIVACY-FIRST

• Your location is visible ONLY to members of your circles, no one else
• No ads, no tracking, no selling data to advertisers or data brokers
• Data stored in EU regions (Google Firebase, Belgium + EU multi-region)
• HTTPS/TLS for all communication
• Turn off sharing or delete your account at any time
• No social profile, no feed, no gamification

📍 HOW IT WORKS

1. Install Krug and sign in with Google (or anonymously)
2. Create your circle ("Family", "Friends", "Work")
3. Invite members with a 6-digit code or link
4. See each other on the map

🆘 SOS: IMPORTANT

The SOS button alerts MEMBERS OF YOUR CIRCLE, not emergency services. In a real emergency, call your local emergency number (112 in Europe, 911 in the US, etc.) BEFORE or in addition to using Krug SOS.

✨ WHY KRUG INSTEAD OF SOMETHING ELSE

• No ads: this is a privacy-focused project, not a data-mining business
• Made in Serbia, Serbian as the first language (English fully supported)
• Minimal design: focused on location sharing, not 50 extras you won't use
• Smart battery: activity-aware tracking that doesn't drain when there's no need
• Open development: code is public on GitHub

📋 PERMISSIONS AND WHY

• Location (precise and background): core feature of location sharing
• Notifications: SOS alerts from members and sharing status
• Activity Recognition: optional, for smarter battery usage
• Battery optimization exemption: so your OEM phone doesn't kill location sharing

You can revoke any permission via Android system settings.

🧪 BETA STATUS

This is an early version (v0.1.0). Features are actively being developed. Your feedback is precious. Tell us what you like, what's broken, and what's missing.

📧 CONTACT

aleksandarr@gmail.com

🌐 PRIVACY AND TERMS

Privacy Policy: https://krugapp.com/privacy
Terms of Service: https://krugapp.com/terms
```

*(approximately 2,800 chars)*

---

## What's new (max 500 chars) for v0.1.0

```
First public release of Krug.

• Real-time location sharing within private circles
• SOS button: emergency signal to all circle members
• Battery-smart tracking (walking/driving/still detection)
• Pause sharing when you need privacy
• Driving distance between members
• Serbian and English language support

Tell us what's missing or what needs fixing: aleksandarr@gmail.com
```

*(approximately 430 chars)*

---

## Translation notes

- "Krug" stays untranslated (it's the brand)
- The phrase "Family Circle" in the app name is a tagline, not a translation
- "Loved ones" is OK occasionally but "family and close friends" is clearer for EN audience
- Avoid "tracker" / "tracking" alone (negative connotation); use "location sharing"

## Keywords that should appear naturally (Play Store SEO)

- family location sharing ✓
- private circle ✓
- map ✓
- SOS ✓
- battery ✓
- real-time ✓
- privacy ✓
- no ads ✓

## TODO before submitting

- [ ] Buy domain (krugapp.com) and deploy privacy.html + terms.html
- [ ] Update URLs in full description once domain is live
- [ ] Update AboutScreen.kt PRIVACY_URL / TERMS_URL to new URLs
- [ ] Verify character counts in Play Console preview (special chars don't add up in Play Console; chars not bytes)
- [ ] Optional: translate privacy.html + terms.html to EN (currently only Serbian; Play Store accepts SR-only privacy URL but English testers benefit from translation)

---

## Notes on per-locale strategy

Play Console allows different store listings per language. The strategy here:

| Locale | Listing |
|--------|---------|
| `sr` (Serbian) | Clean "Krug" name + Serbian description |
| `en-US` (English, US) | "Krug: Family Circle" name + English description |
| `en-GB` (UK) | Inherits from en-US, same content |
| Other locales | Default to en-US until translated |

Users in Serbian device locale see SR listing. Users elsewhere see EN. Same APK, different store presentation.
