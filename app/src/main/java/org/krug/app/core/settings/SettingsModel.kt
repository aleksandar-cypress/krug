package org.krug.app.core.settings

enum class BatteryMode {
    /** Uvek LOW profil — najmanje grejanja, ali sporiji updates. */
    SAVER,
    /** Default — LOW kao baseline. SOS/refresh ping povlači sveži fix on-demand. */
    BALANCED,
    /** Uvek HIGH profil — najbrži updates, ali grejanje + drain. */
    MAX,
}

data class UserSettings(
    val batteryMode: BatteryMode = BatteryMode.BALANCED,
    val shareLocationGlobal: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val language: String = "sr",
    /**
     * Temporary sharing — kad je set, deljenje se automatski gasi u tom trenutku
     * (System.currentTimeMillis()). null = trajno deljenje (default). FGS na svaki
     * fix proveri i ako je isteklo, postavi shareLocationGlobal=false + očisti flag.
     * Peer UI prikazuje countdown banner kad je aktivan.
     */
    val shareUntilMs: Long? = null,
    /**
     * Notifikacije o Places (geofence enter/exit). Default true; user može da isključi
     * iz Settings da izbegne spam ako ima puno mesta ili velikog kruga sa aktivnim članovima.
     */
    val placeNotifsEnabled: Boolean = true,
    /**
     * Silent hours — u ovom vremenskom intervalu (npr 23:00–07:00) ne notif-uju se
     * Place event-i. `null` = feature isključen. Format: "HH:MM-HH:MM" 24h.
     */
    val silentHours: String? = null,
    /**
     * Battery alerts — notif kada baterija člana kruga padne ispod 20% (i nije na
     * punjaču). Default true. Rate-limit per member ~12h (LocationTrackingService).
     */
    val batteryAlertsEnabled: Boolean = true,
    /**
     * Speeding alerts — user-ov opt-in da se emituju event-i kruzima kad on prekorači
     * `speedingThresholdKmh`. Default false (user mora eksplicitno da uključi jer je ovo
     * "publish o meni" ne "receive od drugih"). Prijem tuđih speeding event-a je uvek
     * pokazan (nema separate receive toggle) — poštuje samo silent hours.
     */
    val speedingAlertsEnabled: Boolean = false,
    /**
     * Prag brzine iznad kog se emituje speeding event (na sopstvenim fix-ima). Default
     * 120 km/h — tipično highway ograničenje +10. Detektuje se posle 5s neprekidno
     * iznad ovog praga.
     */
    val speedingThresholdKmh: Int = 120,
    /**
     * Crash detection — akcelerometar-based sumnja na sudar. Kad detektor okine,
     * pokazuje se countdown notifikacija; ako user ne otkaže u 10s, auto SOS ide krugu.
     * Default false zbog false-positive rizika i baterijskog utroška sensor-a.
     */
    val crashDetectionEnabled: Boolean = false,
)
