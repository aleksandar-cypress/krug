package org.krug.app.core.logging

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.krug.app.core.auth.AuthRepository
import org.krug.app.core.prefs.LocalPrefs

/**
 * Setuje user kontekst u Crashlytics kad god se promeni auth state ili aktivni krug.
 * Bez ovoga, crash-evi u prodakciji nemaju ko/odakle informaciju — Crashlytics ih svrsta
 * pod anonimnog korisnika bez kruga, što čini debugging mukotrpnim.
 *
 * Custom keys (vidljivi na Crashlytics dashboard-u uz svaki crash):
 * - `anonymous` — bool, da li je sign-in anonimni
 * - `hasEmail` — bool, da li user ima email vezan za nalog
 * - `activeCircleId` — uid trenutno aktivnog kruga, prazno ako nema
 *
 * `setUserId` postavlja Crashlytics built-in user identifier — postaje sortable kolona.
 */
@Singleton
class CrashlyticsContext @Inject constructor(
    private val authRepository: AuthRepository,
    private val localPrefs: LocalPrefs,
) {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    /** Bind-uje observere na app-wide scope (KrugApplication). */
    fun bind(scope: CoroutineScope) {
        authRepository.observeAuthState()
            .distinctUntilChanged { a, b -> a?.uid == b?.uid }
            .onEach { user ->
                if (user != null) {
                    crashlytics.setUserId(user.uid)
                    crashlytics.setCustomKey("anonymous", user.isAnonymous)
                    crashlytics.setCustomKey("hasEmail", !user.email.isNullOrBlank())
                } else {
                    crashlytics.setUserId("")
                    crashlytics.setCustomKey("anonymous", false)
                    crashlytics.setCustomKey("hasEmail", false)
                }
            }
            .launchIn(scope)

        // activeCircleIdFlow je StateFlow — već dedup-uje, ne treba distinctUntilChanged.
        localPrefs.activeCircleIdFlow
            .onEach { circleId ->
                crashlytics.setCustomKey("activeCircleId", circleId.orEmpty())
            }
            .launchIn(scope)
    }
}
