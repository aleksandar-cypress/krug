package org.krug.app

import android.app.Application
import android.os.StrictMode
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.mapbox.common.MapboxOptions
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.krug.app.core.location.LocationHealthWorker
import org.krug.app.core.logging.CrashlyticsContext
import org.krug.app.core.logging.CrashlyticsTree
import timber.log.Timber

@HiltAndroidApp
class KrugApplication : Application() {

    @Inject lateinit var crashlyticsContext: CrashlyticsContext

    // Process-wide scope za Crashlytics kontekst observere — žive dok i app process.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // StrictMode samo u debug — hvata disk/network I/O na main thread i resource
        // leak-ove rano, ne loguje u release (overhead + lažni pozitivi iz Firebase init-a).
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectActivityLeaks()
                    .detectFileUriExposure()
                    .penaltyLog()
                    .build(),
            )
        }
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

        // Firestore cache size cap — bez ovog, persistence cache može da raste do
        // 100MB+ na low-end uređajima sa puno krugova/članova. 50MB je razuman tradeoff
        // (puno za offline, neće preopteretiti malu memoriju).
        runCatching {
            FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(50L * 1024 * 1024)
                        .build(),
                )
                .build()
        }.onFailure { Timber.w(it, "Firestore cache size config failed (already initialized?)") }

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

        // Crashlytics user kontekst — bind tek posle FirebaseApp.initializeApp + Hilt
        // injection-a. Bind je idempotentan (distinctUntilChanged drop-uje no-op-ove).
        crashlyticsContext.bind(appScope)
        Timber.i("App start (build=%s, ver=%s)", BuildConfig.BUILD_TYPE, BuildConfig.VERSION_NAME)
    }
}
