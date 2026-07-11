package org.krug.app.core.places

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import org.krug.app.core.prefs.LocalPrefs
import timber.log.Timber

/**
 * Wrapper oko Google Play Services GeofencingClient.
 *
 * Request ID format: `{circleId}:{placeId}` — BroadcastReceiver iz njega izvlači
 * circle context da bi znao gde da upiše event.
 *
 * Ograničenja:
 *  - Max 100 geofence-a po app instanci (Google-ov limit).
 *  - Radius min 10m (praktično 50m zbog GPS accuracy).
 *  - Zahteva ACCESS_BACKGROUND_LOCATION za pouzdano trigger-ovanje kad je app killed.
 *  - Custom ROM-ovi (Xiaomi/Huawei) mogu zakačiti battery optimizer i suppress-ovati
 *    geofence event-e; korisnika treba uputiti na "Whitelist Krug" u battery settings.
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localPrefs: LocalPrefs,
) {
    private val client: GeofencingClient by lazy {
        LocationServices.getGeofencingClient(context)
    }

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEOFENCE_TRANSITION
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /**
     * Registruje batch places-a. Poziva se pri app start-u (preko LocationTrackingService)
     * i kad se lista places-a promeni (nov place, obrisan, edit).
     *
     * Ako user nije dao BACKGROUND_LOCATION permission — logs warning i ne registruje
     * (GeofencingClient bi bacio SecurityException).
     */
    @SuppressLint("MissingPermission")
    suspend fun registerAll(entries: List<GeofenceEntry>): Boolean {
        if (!hasRequiredPermissions()) {
            Timber.w("GeofenceManager: missing location permissions, skip register")
            return false
        }
        if (entries.isEmpty()) {
            Timber.d("GeofenceManager: no entries to register")
            return true
        }
        val geofences = entries.map { entry ->
            Geofence.Builder()
                .setRequestId("${entry.circleId}:${entry.placeId}")
                .setCircularRegion(entry.lat, entry.lng, entry.radius.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
                )
                .setNotificationResponsiveness(30_000)
                .build()
        }
        // INITIAL_TRIGGER = 0: NE fire-uj ENTER event samo zato što je user unutar geofence-a
        // pri registraciji. Bez ovog: svaki put kad se places lista promeni (kreiran nov
        // place, edit, snapshot re-fire zbog reconnect-a), sve geofences se re-registruju
        // i ENTER fire-uje za sve place-ove gde je user trenutno unutra. Rezultat: spam
        // notifikacija ostalim članovima kruga ("Aleksandar stigao kod kuće" x4 iako se
        // Aleksandar nije pomerio). Enter/exit će se fire-ovati SAMO kad user fizički
        // pređe granicu, što je jedina korisna semantika.
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(geofences)
            .build()
        return try {
            client.addGeofences(request, pendingIntent).await()
            val now = System.currentTimeMillis()
            lastRegisteredAtMs = now
            // Persist da grace preživi proces death: BroadcastReceiver-i su statički
            // entry point-i i Play Services zna da fire spurious event pre nego što
            // se LocationTrackingService podigne (dakle pre poziva registerAll()).
            // Bez persist-a, in-memory `lastRegisteredAtMs=0` znači grace ne štiti
            // od prvog broadcast-a posle app reopen-a (bug: Jelena restart → phantom
            // ENTER drugom članu).
            localPrefs.lastGeofenceRegisterMs = now
            Timber.i("GeofenceManager: registered %d geofences (startup grace begins)", geofences.size)
            true
        } catch (e: Exception) {
            Timber.e(e, "GeofenceManager: addGeofences failed")
            false
        }
    }

    suspend fun removeAll(): Boolean {
        return try {
            client.removeGeofences(pendingIntent).await()
            Timber.i("GeofenceManager: removed all geofences")
            true
        } catch (e: Exception) {
            Timber.e(e, "GeofenceManager: removeGeofences failed")
            false
        }
    }

    suspend fun removeSpecific(requestIds: List<String>): Boolean {
        if (requestIds.isEmpty()) return true
        return try {
            client.removeGeofences(requestIds).await()
            Timber.i("GeofenceManager: removed %d specific geofences", requestIds.size)
            true
        } catch (e: Exception) {
            Timber.e(e, "GeofenceManager: removeGeofences (specific) failed")
            false
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return fine && bg
    }

    companion object {
        const val ACTION_GEOFENCE_TRANSITION = "org.krug.app.GEOFENCE_TRANSITION"

        /**
         * Timestamp poslednje geofence re-registracije. Koristi ga BroadcastReceiver
         * za "startup grace" filter — Play Services često firira spurious reconciliation
         * event-e u prvih 60-120s posle registracije jer joj treba vreme da uskladi
         * cache-ovan geofence state sa aktuelnom lokacijom uređaja. Ovi event-i imaju
         * fresh accuracy i fresh timestamp, pa ih ne hvataju accuracy/age filter-i.
         */
        @Volatile var lastRegisteredAtMs: Long = 0L
        const val STARTUP_GRACE_MS = 2L * 60_000L
    }
}

data class GeofenceEntry(
    val circleId: String,
    val placeId: String,
    val lat: Double,
    val lng: Double,
    val radius: Int,
)
