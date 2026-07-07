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

      // 1) Fetch target user's FCM token
      const firestore = getFirestore();
      const userDoc = await firestore
          .collection("users")
          .doc(targetUid)
          .get();

      if (!userDoc.exists) {
        logger.warn("Target user doc not found", {targetUid});
        return;
      }

      const fcmToken = userDoc.get("fcmToken");
      if (!fcmToken) {
        logger.warn(
            "Target user has no fcmToken (needs 1.1.4+ client)",
            {targetUid},
        );
        return;
      }

      // 2) Send high-priority FCM data message
      // Data-only (bez notification field): app dobija onMessageReceived čak i u
      // background/Doze. Sa notification field, Android bi mozda auto-prikazao sistemsku
      // notifikaciju i ne bi budio proces — sto nam ne treba, mi hocemo silent wakeup
      // koji forsira FGS one-shot fix.
      const message = {
        token: fcmToken,
        data: {
          type: "refresh",
          requesterUid: requesterUid,
          timestamp: String(timestamp || Date.now()),
        },
        android: {
          priority: "high",
          // TTL 5min: ako je uredjaj offline duze od ovog, push se odbaci (nema smisla
          // buditi FGS 2h kasnije za refresh koji je user vec zaboravio)
          ttl: 5 * 60 * 1000,
        },
      };

      try {
        const response = await getMessaging().send(message);
        logger.info(
            "FCM push sent",
            {targetUid, response, tokenLen: fcmToken.length},
        );
      } catch (error) {
        // Common error codes:
        //  messaging/registration-token-not-registered → user je uninstall-ovao ili token
        //    je stariji od 6 meseci. Obrisemo iz Firestore-a da ne trosimo kvote.
        //  messaging/invalid-argument → malformed token, obrisi.
        logger.error("FCM send failed", {targetUid, error: error.message, code: error.code});

        const code = error.code || "";
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-argument" ||
          code === "messaging/invalid-registration-token"
        ) {
          logger.info("Clearing invalid FCM token from Firestore", {targetUid});
          await firestore
              .collection("users")
              .doc(targetUid)
              .update({fcmToken: null});
        }
      }
    },
);
