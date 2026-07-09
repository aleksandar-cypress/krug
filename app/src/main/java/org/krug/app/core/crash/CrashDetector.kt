package org.krug.app.core.crash

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import timber.log.Timber

/**
 * Detektuje potencijalni sudar na osnovu 3-osnog linear-acceleration sensor-a
 * (gravity filtrirana) i konteksta „user je bio u vožnji". Ne pouzdajemo se samo
 * u peak-G jer telefon koji padne sa stola takođe generiše ~3-4g impulse. Kombinujemo
 * sa nedavnom brzinom (>= DRIVE_CONTEXT_MIN_MPS u poslednjih DRIVE_CONTEXT_WINDOW_MS)
 * da izbegnemo false-positive iz svakodnevnih pokreta.
 *
 * Kad detekcija okine, poziva `onCrash` callback — LocationTrackingService pravi
 * heads-up notif sa countdown-om i „I'm OK" action-om.
 */
@Singleton
class CrashDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) : SensorEventListener {

    private val sensorManager: SensorManager? by lazy {
        ContextCompat.getSystemService(context, SensorManager::class.java)
    }
    private val sensor: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    }

    private var onCrashCallback: (() -> Unit)? = null
    private var registered = false

    @Volatile private var lastDriveMs: Long = 0L
    @Volatile private var lastTriggeredAtMs: Long = 0L

    fun start(onCrash: () -> Unit) {
        if (registered) return
        val mgr = sensorManager ?: run {
            Timber.w("CrashDetector: SensorManager unavailable")
            return
        }
        val s = sensor ?: run {
            Timber.w("CrashDetector: LINEAR_ACCELERATION sensor unavailable")
            return
        }
        onCrashCallback = onCrash
        mgr.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
        registered = true
        Timber.d("CrashDetector: started")
    }

    fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        onCrashCallback = null
        registered = false
        Timber.d("CrashDetector: stopped")
    }

    /**
     * Location updater ovo poziva kad user vozi (speed >= threshold). Cache-uje
     * lastDriveMs — bez ovog signal-a, detektor pretpostavlja da user ne vozi i
     * ne trigeruje crash.
     */
    fun onDrivingSpeed(speedMps: Float, nowMs: Long) {
        if (speedMps >= DRIVE_CONTEXT_MIN_MPS) {
            lastDriveMs = nowMs
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        if (magnitude < CRASH_G_THRESHOLD_MS2) return
        val now = System.currentTimeMillis()
        // Kontekst: user je bio u vožnji unutar poslednjih DRIVE_CONTEXT_WINDOW_MS.
        if (now - lastDriveMs > DRIVE_CONTEXT_WINDOW_MS) {
            Timber.d(
                "CrashDetector: high-G ignored (no recent drive, mag=%.1f)", magnitude,
            )
            return
        }
        // Rate-limit — jedan trigger na TRIGGER_COOLDOWN_MS (izbegava double-fire pri
        // kompleksnom sudaru koji generiše više peak-ova).
        if (now - lastTriggeredAtMs < TRIGGER_COOLDOWN_MS) return
        lastTriggeredAtMs = now
        Timber.w(
            "CrashDetector: possible crash detected (mag=%.1f m/s², sinceDrive=%dms)",
            magnitude, now - lastDriveMs,
        )
        onCrashCallback?.invoke()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /**
         * Prag magnituda linear-acceleration-a za sumnju na crash. 40 m/s² ≈ 4g.
         * Realni crashevi su znatno jači (10-100g na tuf-u), ali dizajniramo za
         * niski prag da hvatamo i „minor collisions". False-positive filtriramo
         * kontekstom (driving window).
         */
        const val CRASH_G_THRESHOLD_MS2 = 40.0

        /** ~11 km/h — user je jasno u pokretu (ne šeta). */
        const val DRIVE_CONTEXT_MIN_MPS = 3.0f

        /** Prozor „nedavno u vožnji" — 30s. */
        const val DRIVE_CONTEXT_WINDOW_MS = 30_000L

        /** Cooldown između uzastopnih trigger-a. */
        const val TRIGGER_COOLDOWN_MS = 2 * 60_000L
    }
}
