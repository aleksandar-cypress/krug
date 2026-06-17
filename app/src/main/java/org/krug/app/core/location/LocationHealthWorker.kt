package org.krug.app.core.location

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * Periodic 15-min backup — sluša samo kad je FGS *mrtav*. Ako je živ, no-op (sprečava
 * forsiranje GPS spike-a svakih 15 min — to je bio glavni izvor grejanja).
 */
class LocationHealthWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        if (user == null) {
            Timber.d("LocationHealthWorker: no user, skipping")
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
        return try {
            LocationTrackingService.start(applicationContext)
            Timber.d("LocationHealthWorker: triggered FGS start (was ${if (LocationTrackingService.isRunning.get()) "alive" else "dead"})")
            Result.success()
        } catch (e: Exception) {
            Timber.w(e, "LocationHealthWorker: failed")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "krug_location_health"

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
