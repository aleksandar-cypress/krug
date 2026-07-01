package org.krug.app.core.location

// RTDB POJO for /locations/{uid}. updatedAt is a server-set ms-since-epoch.
// `charging` (ne `isCharging`) — Kotlin `is` prefix konfundovala Firebase ClassMapper,
// generišući "No setter/field for isCharging" warning na svakom read-u.
data class LocationModel(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracy: Float = 0f,
    val batteryPct: Int = -1,
    val charging: Boolean = false,
    /** Korisnik je pauzirao deljenje (Privacy → Switch off). Klijent prikazuje "Privatni mod" odmah. */
    val paused: Boolean = false,
    /** GPS bearing/heading u stepenima [0..360], 0=sever. 0 ako uređaj nema bearing fix. */
    val bearing: Float = 0f,
    /** GPS speed u m/s. 0 ako uređaj nema speed fix ili miruje. Koristi se za course-up nav mod. */
    val speed: Float = 0f,
    val updatedAt: Long = 0L,
    /**
     * Server-side presence flag. FGS registruje onDisconnect handler koji auto-postavi
     * false kad klijent izgubi vezu (tunel, dead battery, force-stop) ~30s posle discon.
     * Peer-i tako razlikuju "offline" (bez veze) od "paused" (deliberatno) i "stale fix"
     * (isporučen fix ali star). Default true za legacy record-e bez ovog polja.
     */
    val online: Boolean = true,
)
