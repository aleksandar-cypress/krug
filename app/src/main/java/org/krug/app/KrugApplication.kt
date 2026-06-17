package org.krug.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.mapbox.common.MapboxOptions
import dagger.hilt.android.HiltAndroidApp
import org.krug.app.core.location.LocationHealthWorker
import org.krug.app.core.logging.CrashlyticsTree
import timber.log.Timber

@HiltAndroidApp
class KrugApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Logging: u debug ide u logcat (DebugTree), u release ide u Crashlytics.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashlyticsTree())
        }
        // Crashlytics: prikupi samo u release. Debug crash-evi ostaju u logcat-u
        // da ne zatrpamo dashboard test-tipom.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG

        // App Check: blokira ne-autentifikovane pozive Firebase API-jima izvan našeg APK-a.
        // Mora se install-ovati pre prvog korišćenja Firebase servisa.
        FirebaseApp.initializeApp(this)
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            // Debug build: SDK loguje debug token u logcat na prvom run-u.
            // Token treba dodati u Firebase Console → App Check → Apps → Debug tokens.
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance(),
            )
        } else {
            // Release: Play Integrity attestation (radi automatski preko Play Store-a).
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
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
