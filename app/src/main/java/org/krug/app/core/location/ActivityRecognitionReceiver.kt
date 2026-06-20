package org.krug.app.core.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import timber.log.Timber

/**
 * Prima Activity Recognition transition broadcast-e od Google Play Services.
 * Update-uje `LocationTrackingService.detectedActivity` companion var, pa kratko
 * "poke-uje" FGS sa `EXTRA_ACTIVITY_CHANGED` flag-om — service reconfiguriše profile
 * (npr. WALKING → češći interval, STILL → ređi) bez čekanja sledećeg location callback-a.
 *
 * Niska confidence (< 60) ignorišemo da ne menjamo profile pri nesigurnim detekcijama.
 */
class ActivityRecognitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || !ActivityRecognitionResult.hasResult(intent)) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val mostProbable = result.mostProbableActivity ?: return
        if (mostProbable.confidence < MIN_CONFIDENCE) {
            Timber.d("ActivityRecognition: low confidence ${mostProbable.confidence}, ignore")
            return
        }
        val previous = LocationTrackingService.detectedActivity
        LocationTrackingService.detectedActivity = mostProbable.type
        if (previous != mostProbable.type) {
            Timber.d("ActivityRecognition: ${activityName(previous)} → ${activityName(mostProbable.type)} (conf ${mostProbable.confidence})")
            // Poke FGS — neka odmah primeni novi profil bez čekanja sledećeg callback-a.
            val poke = Intent(context, LocationTrackingService::class.java)
                .putExtra(LocationTrackingService.EXTRA_ACTIVITY_CHANGED, true)
            runCatching { ContextCompat.startForegroundService(context, poke) }
                .onFailure { Timber.d("ActivityRecognition poke failed: %s", it.message) }
        }
    }

    private fun activityName(type: Int): String = when (type) {
        DetectedActivity.IN_VEHICLE -> "VEHICLE"
        DetectedActivity.ON_BICYCLE -> "BICYCLE"
        DetectedActivity.ON_FOOT -> "ON_FOOT"
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.RUNNING -> "RUNNING"
        DetectedActivity.STILL -> "STILL"
        DetectedActivity.TILTING -> "TILTING"
        DetectedActivity.UNKNOWN -> "UNKNOWN"
        else -> "OTHER($type)"
    }

    companion object {
        private const val MIN_CONFIDENCE = 60
    }
}
