package org.krug.app.core.sos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton bus za "fokusiraj SOS pin" event iz notifikacije.
 *
 * SOS notifikacija (`SosNotifier.notifySos`) postavlja intent extra
 * `EXTRA_FOCUS_SOS_UID`. MainActivity izvlači taj extra u onCreate / onNewIntent
 * i emituje u ovaj bus. MapScreen collect-uje state, fly-to na člana sa tim uid-om
 * i otvara MemberDetailSheet. Pošto se launchMode=singleTask, novi notif tap dok je
 * Map otvoren ide kroz onNewIntent (ne kroz onCreate), pa bus mora biti singleton da
 * MapScreen instanca može da reaguje na novi event bez restarta.
 *
 * Posle obrade poziva se `consume()` da fresh restart ne re-trigger-uje stari fokus.
 */
object SosFocusBus {
    private val _pendingUid = MutableStateFlow<String?>(null)
    val pendingUid: StateFlow<String?> = _pendingUid.asStateFlow()

    fun request(uid: String) {
        _pendingUid.value = uid
    }

    fun consume() {
        _pendingUid.value = null
    }
}
