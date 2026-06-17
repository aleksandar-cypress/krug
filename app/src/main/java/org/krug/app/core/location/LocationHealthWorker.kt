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
 * Periodic 15-min backup — proverava da li je user signed-in i restartuje FGS.
 * Ako je FGS već živ, `startForegroundService` je no-op (onStartCommand re-fire-uje
 * one-shot fix svejedno, što je dobro — sveži publish svakih 15 min minimum).
 *
 * Ne garantuje da će se izvršiti tačno na 15 min — Android može da odloži ako je telefon
 * u Doze-u. Ali kroz par sati, ovaj worker će pokrenuti FGS pre ili kasnije.
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
        return try {
            LocationTrackingService.start(applicationContext)
            Timber.d("LocationHealthWorker: triggered FGS start")
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
