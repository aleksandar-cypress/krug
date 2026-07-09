/**
 * Krug Cloud Functions — FCM push relay.
 *
 * Zašto ovo postoji: RTDB `locationRequests/{targetUid}/{requesterUid}` write je
 * signal da neko traži refresh location-a od `targetUid`. Bez ove funkcije:
 *   - Target-ov FGS uhvata write kroz RTDB listener SAMO ako je proces živ + budan
 *   - Ako je target u Doze/App Standby, RTDB listener je queue-ovan → write je nevidljiv
 *     dok se telefon ne probudi (može biti sat vremena kasnije)
 *   - Rezultat: "Slobodan ispada offline" — Aleksandar klikne Refresh, ništa se ne desi
 *
 * Sa ovom funkcijom:
 *   - Funkcija trigger-uje odmah na RTDB write
 *   - Fetchuje FCM token iz Firestore `users/{targetUid}.fcmToken`
 *   - Šalje high-priority FCM data message (Google-owned service, zaobilazi Doze)
 *   - Target Krug prima push preko KrugMessagingService, forsira FGS one-shot fix
 *   - Aleksandar odmah vidi Slobodana na svežoj poziciji
 *
 * High-priority throttle: Google ograničava ~1/10min per app. Za Krug scenario (par
 * refresh-a dnevno po članu) to je daleko od limita.
 */

const {onValueCreated} = require("firebase-functions/v2/database");
const {logger} = require("firebase-functions/v2");
const {initializeApp} = require("firebase-admin/app");
const {getMessaging} = require("firebase-admin/messaging");
const {getFirestore} = require("firebase-admin/firestore");

initializeApp();

exports.onLocationRefreshRequest = onValueCreated(
    {
      // RTDB je u europe-west1 (google-services.json firebase_url pokazuje ka
      // europe-west1.firebasedatabase.app). Funkcija MORA biti u istom regionu kao
      // RTDB instance da bi trigger radio — bez ovog, deploy fail-uje sa
      // "pattern cannot match any databases in region us-central1".
      region: "europe-west1",
      ref: "/locationRequests/{targetUid}/{requesterUid}",
      instance: "krug-86527-default-rtdb",
    },
    async (event) => {
      const targetUid = event.params.targetUid;
      const requesterUid = event.params.requesterUid;
      const timestamp = event.data.val();

      logger.info(
          "Refresh request received",
          {targetUid, requesterUid, timestamp},
      );

      const firestore = getFirestore();

      // 1) Multi-device: fetch svih tokena iz users/{uid}/devices subcollection-a.
      //    Ako subcollection ne postoji (stariji klijenti pre 1.2.0), fallback na
      //    legacy users/{uid}.fcmToken single-token model.
      const devicesSnap = await firestore
          .collection("users")
          .doc(targetUid)
          .collection("devices")
          .get();

      const deviceTokens = [];
      devicesSnap.forEach((doc) => {
        const t = doc.get("fcmToken");
        if (t) deviceTokens.push({deviceId: doc.id, token: t});
      });

      // Legacy fallback — ako nema devices subcollection-a.
      if (deviceTokens.length === 0) {
        const userDoc = await firestore
            .collection("users")
            .doc(targetUid)
            .get();
        if (!userDoc.exists) {
          logger.warn("Target user doc not found", {targetUid});
          return;
        }
        const legacyToken = userDoc.get("fcmToken");
        if (!legacyToken) {
          logger.warn("Target has no FCM tokens anywhere", {targetUid});
          return;
        }
        deviceTokens.push({deviceId: "legacy", token: legacyToken});
      }

      logger.info("Fanout to devices", {targetUid, count: deviceTokens.length});

      // 2) Send high-priority FCM data message na SVAKI device-token paralelno.
      // Data-only (bez notification field): app dobija onMessageReceived čak i u
      // background/Doze. Sa notification field, Android bi mozda auto-prikazao sistemsku
      // notifikaciju i ne bi budio proces.
      const results = await Promise.all(deviceTokens.map(async ({deviceId, token}) => {
        const message = {
          token: token,
          data: {
            type: "refresh",
            requesterUid: requesterUid,
            timestamp: String(timestamp || Date.now()),
          },
          android: {
            priority: "high",
            ttl: 5 * 60 * 1000,
          },
        };
        try {
          const response = await getMessaging().send(message);
          return {deviceId, ok: true, response};
        } catch (error) {
          const code = error.code || "";
          // Cleanup samo za invalid/expired tokene (ne za mrežne greške).
          if (
            code === "messaging/registration-token-not-registered" ||
            code === "messaging/invalid-argument" ||
            code === "messaging/invalid-registration-token"
          ) {
            if (deviceId === "legacy") {
              await firestore.collection("users").doc(targetUid)
                  .update({fcmToken: null});
            } else {
              await firestore.collection("users").doc(targetUid)
                  .collection("devices").doc(deviceId).delete();
            }
            logger.info("Removed invalid token", {targetUid, deviceId});
          }
          return {deviceId, ok: false, error: error.message, code};
        }
      }));

      const successCount = results.filter((r) => r.ok).length;
      logger.info("Fanout result", {
        targetUid,
        total: results.length,
        success: successCount,
        failed: results.length - successCount,
      });
    },
);
