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
)
