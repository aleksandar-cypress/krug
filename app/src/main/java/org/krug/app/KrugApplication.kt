package org.krug.app

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import com.mapbox.common.MapboxOptions
import dagger.hilt.android.HiltAndroidApp
import org.krug.app.core.location.LocationHealthWorker
import timber.log.Timber

@HiltAndroidApp
class KrugApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // RTDB offline persistence: queue location/SOS write-ova na disk dok je telefon bez neta.
        // Mora se pozvati pre prvog FirebaseDatabase.getInstance() poziva.
        runCatching { FirebaseDatabase.getInstance().setPersistenceEnabled(true) }
            .onFailure { Timber.w(it, "RTDB persistence already configured") }
        if (BuildConfig.MAPBOX_PUBLIC_TOKEN.isNotBlank()) {
            MapboxOptions.accessToken = BuildConfig.MAPBOX_PUBLIC_TOKEN
        } else {
            Timber.e("MAPBOX_PUBLIC_TOKEN is blank — set KRUG_MAPBOX_PUBLIC_TOKEN in local.properties")
        }
        // Periodic 15-min keepalive — Android može da uspava FGS na agresivnim OEM-ima;
        // worker pokušava da ga restartuje. ExistingPeriodicWorkPolicy.KEEP — ako je već
        // zakazano, ne diramo (preživljava restart app-a).
        LocationHealthWorker.schedule(this)
    }
}
