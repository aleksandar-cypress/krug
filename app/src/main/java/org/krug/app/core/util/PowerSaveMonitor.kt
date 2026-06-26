package org.krug.app.core.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

@Singleton
class PowerSaveMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun isCurrentlyOnSaver(): Boolean = pm.isPowerSaveMode

    private val saverFlow: Flow<Boolean> = callbackFlow {
        trySend(isCurrentlyOnSaver())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(isCurrentlyOnSaver())
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        context.registerReceiver(receiver, filter)
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.distinctUntilChanged()

    /**
     * True kad je sistemski Battery Saver mod aktivan. Reactive preko
     * ACTION_POWER_SAVE_MODE_CHANGED broadcast-a. UI banner u MapScreen-u
     * obaveštava korisnika da se lokacija ažurira ređe dok je Saver on.
     */
    val isOnSaver: StateFlow<Boolean> = saverFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = isCurrentlyOnSaver(),
    )
}
