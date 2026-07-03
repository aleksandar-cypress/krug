package org.krug.app.core.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.sos.SosRepository
import timber.log.Timber

/**
 * Car session lifecycle vlasnik. Kreira prvi screen (MapCarScreen) i hostuje
 * screen back-stack za trajanje projekcije. Kad se auto disconnect-uje ili
 * user gasi Auto na dashboard-u, session se prekida.
 *
 * SOS narator: TTS glasovna notifikacija kad neko iz kruga trigger-uje SOS dok
 * je user u autu. Start-uje se na onCreate lifecycle event, stop na onDestroy.
 */
class KrugCarSession : Session() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SessionEntryPoint {
        fun firebaseAuth(): FirebaseAuth
        fun circleRepository(): CircleRepository
        fun sosRepository(): SosRepository
    }

    private var narrator: SosCarNarrator? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                Timber.i("KrugCarSession: lifecycle CREATED — start SosCarNarrator")
                val ep = EntryPointAccessors.fromApplication(
                    carContext.applicationContext,
                    SessionEntryPoint::class.java,
                )
                narrator = SosCarNarrator(
                    context = carContext,
                    auth = ep.firebaseAuth(),
                    circleRepository = ep.circleRepository(),
                    sosRepository = ep.sosRepository(),
                ).also { it.start() }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                Timber.i("KrugCarSession: lifecycle DESTROYED — stop SosCarNarrator")
                narrator?.stop()
                narrator = null
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen {
        Timber.i("KrugCarSession: onCreateScreen intent=%s", intent.action)
        return MapCarScreen(carContext)
    }
}
