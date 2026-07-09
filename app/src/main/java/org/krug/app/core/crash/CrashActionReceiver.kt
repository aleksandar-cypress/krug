package org.krug.app.core.crash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import timber.log.Timber

/**
 * Prima „Dobro sam" i „Pošalji odmah" action-e iz crash countdown notifikacije.
 * Emit-uje na `CrashActionBus` koji LocationTrackingService kolekcuje da otkaže
 * scheduled SOS ili ga forsira odmah.
 */
@AndroidEntryPoint
class CrashActionReceiver : BroadcastReceiver() {

    @Inject lateinit var bus: CrashActionBus

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            CrashAlertNotifier.ACTION_CRASH_CANCEL -> {
                Timber.d("CrashActionReceiver: user cancelled crash countdown")
                bus.emitSync(CrashAction.Cancel)
            }
            CrashAlertNotifier.ACTION_CRASH_SEND_NOW -> {
                Timber.d("CrashActionReceiver: user forced immediate SOS")
                bus.emitSync(CrashAction.SendNow)
            }
        }
    }
}

/**
 * Signal koji CrashActionReceiver šalje LocationTrackingService-u da promeni
 * ponašanje pending SOS job-a.
 */
sealed class CrashAction {
    object Cancel : CrashAction()
    object SendNow : CrashAction()
}

/**
 * Singleton bus preko kog broadcast receiver komunicira sa FGS-om (rade u istom procesu
 * ali imaju posebne instance). MutableSharedFlow sa replay=0 + tryEmit-om — signal je
 * uvek relevantan u trenutku, nema smisla replay-ovati.
 */
@javax.inject.Singleton
class CrashActionBus @Inject constructor() {
    private val _actions = MutableSharedFlow<CrashAction>(replay = 0, extraBufferCapacity = 4)
    val actions = _actions

    fun emitSync(action: CrashAction) {
        _actions.tryEmit(action)
    }
}
