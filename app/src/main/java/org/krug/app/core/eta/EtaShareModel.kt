package org.krug.app.core.eta

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Aktivan ETA share: user je krenuo na destinaciju i deli live procenu vremena
 * dolaska sa svojim krugom. Jedan aktivan share po user-u po krugu (docId = userId).
 *
 * LocationTrackingService update-uje `etaMinutes`/`remainingKm` na svaki fix (throttle-ovano
 * ~60s da izbegne recompute-ove za sitne pomeraje). Kad user pređe unutar `arrivalRadiusM`
 * destinacije, `arrivedAt` se popunjava i share ostaje read-only history 15min pre brisanja.
 */
data class EtaShareModel(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val destinationLat: Double = 0.0,
    val destinationLng: Double = 0.0,
    val destinationLabel: String = "",
    val etaMinutes: Int = 0,
    val remainingKm: Double = 0.0,
    val currentLat: Double = 0.0,
    val currentLng: Double = 0.0,
    @ServerTimestamp val startedAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null,
    /** Non-null kad user stigne — signalizuje "stigao/la" notif observers-ima. */
    val arrivedAt: Date? = null,
)
