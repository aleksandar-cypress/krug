package org.krug.app.core.circle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton bus za invite deep-link (`krug://invite/{code}`).
 *
 * Flow:
 *  1. User klikne link iz WhatsApp/SMS/email — Android otvara MainActivity sa
 *     `Intent.ACTION_VIEW` + data URI.
 *  2. MainActivity.handleInviteDeepLink() parsira `code` iz path-a i emituje u ovaj bus.
 *  3. KrugNavHost / Map screen collect-uje bus i, ako je user auth-ovan, navigira na
 *     EnterCode sa prefilledCode-om. Ako nije auth-ovan, code čeka u bus-u dok user
 *     ne završi Auth flow → posle Splash.onReady → nav na EnterCode.
 *
 * Posle nav-a, poziva se consume() da fresh restart ne re-trigger-uje stari invite.
 */
object InviteFocusBus {
    private val _pendingCode = MutableStateFlow<String?>(null)
    val pendingCode: StateFlow<String?> = _pendingCode.asStateFlow()

    fun request(code: String) {
        _pendingCode.value = code
    }

    fun consume() {
        _pendingCode.value = null
    }
}
