package org.krug.app.core.location

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.krug.app.core.settings.SettingsRepository
import timber.log.Timber

/**
 * Periodic 15-min backup — sluša samo kad je FGS *mrtav*. Ako je živ, no-op (sprečava
 * forsiranje GPS spike-a svakih 15 min — to je bio glavni izvor grejanja).
 *
 * Dodatno:
 * - Proverava `shareLocationGlobal` pre nego što okine FGS start — sprečava waste-uj
 *   wakeup-a kad je user pauzirao deljenje
 * - Detektuje kill-loop: ako je prethodni FGS umro u manje od FGS_KILL_LOOP_THRESHOLD_MS
 *   od start-a, signaliziramo Timber.w (Crashlytics non-fatal sa kontekstom)
 * - Detektuje silent start failure na A14+ kad nemamo background location permission ili
 *   smo van foreground exemption window-a — start vrati success, ali isRunning ostane false
 */
class LocationHealthWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        if (user == null) {
            Timber.d("LocationHealthWorker: no user, skipping")
            return Result.success()
        }

        // Settings check — ako je user pauzirao sharing, FGS bi se digao i odmah skipovao
        // svaki publish. Bolje da se ne diže uopšte.
        val settingsRepo = EntryPointAccessors
            .fromApplication(applicationContext, WorkerEntryPoint::class.java)
            .settingsRepository()
        val shareEnabled = withTimeoutOrNull(3_000L) {
            settingsRepo.observe(user.uid).first().shareLocationGlobal
        } ?: true // default true — bolje da ga digne nego da silent skip ako settings ne stigne
        if (!shareEnabled) {
            Timber.d("LocationHealthWorker: sharing paused, skipping FGS start")
            return Result.success()
        }

        if (LocationTrackingService.isRunning.get()) {
            val sincePublish = System.currentTimeMillis() - LocationTrackingService.lastPublishAtMs
            if (LocationTrackingService.lastPublishAtMs != 0L &&
                sincePublish < LocationTrackingService.PUBLISH_FRESHNESS_MS
            ) {
                Timber.d("LocationHealthWorker: FGS alive, publish ${sincePublish}ms ago — no-op")
                return Result.success()
            }
            Timber.d("LocationHealthWorker: FGS alive but stale publish — letting start() refresh")
        }

        // Kill-loop detection — ako je prethodni FGS umro pre nego što je stigao da publish-uje
        // (lifetime < 60s), to je signal da nam OS ubija FGS (Doze, OEM whitelist nije dao,
        // permission revoked). Logujemo kao W → Crashlytics non-fatal sa kontekstom.
        val lastLifetimeMs = LocationTrackingService.lastFgsLifetimeMs
        if (lastLifetimeMs in 1..FGS_KILL_LOOP_THRESHOLD_MS) {
            Timber.w(
                "FGS kill-loop suspected: last lifetime %dms (threshold %dms)",
                lastLifetimeMs, FGS_KILL_LOOP_THRESHOLD_MS,
            )
        }

        return try {
            val wasAlive = LocationTrackingService.isRunning.get()
            LocationTrackingService.start(applicationContext)
            // Silent failure detection — start() je no-op ako permission nedostaje;
            // dajemo 2s da onCreate odradi, pa proveravamo isRunning. Ako i dalje false,
            // verovatno A14+ background restrikcija ili nedostaje permission.
            kotlinx.coroutines.delay(2_000L)
            if (!LocationTrackingService.isRunning.get()) {
                Timber.w(
                    "FGS start no-op (wasAlive=%s) — vrlo verovatno permission ili A14+ background restriction",
                    wasAlive,
                )
                Result.retry()
            } else {
                Timber.i("LocationHealthWorker: FGS started (was=%s)", if (wasAlive) "alive" else "dead")
                Result.success()
            }
        } catch (e: Exception) {
            Timber.w(e, "LocationHealthWorker: failed")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "krug_location_health"
        /** Lifetime kraći od ovog signal-izuje kill-loop pattern. */
        private const val FGS_KILL_LOOP_THRESHOLD_MS = 60_000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LocationHealthWorker>(
                15, TimeUnit.MINUTES,
            )
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
