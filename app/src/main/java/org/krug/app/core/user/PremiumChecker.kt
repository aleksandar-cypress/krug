package org.krug.app.core.user

import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Centralno mesto za "da li je trenutni user premium?".
 *
 * Zašto singleton umesto observe u svakom feature-u:
 *  - Više ViewModel-a će guardovati akcije premium flag-om. Ako svaki radi svoj
 *    `observeUser(currentUid).map { it?.isPremium }`, imamo N paralelnih Firestore
 *    listener-a na isti dokument. Singleton bind-uje jedan listener i deli StateFlow.
 *  - Sync check (`isPremiumNow`) je koristan za guard u callback-u koji nije coroutine
 *    (npr. click handler pre pokretanja async akcije).
 *
 * Trenutno stanje (1.1.5): `isPremium` je `false` za sve, admin ručno može postaviti
 * `true` kroz Firestore konzolu za privatno testiranje pre nego što Play Billing bude
 * integrisan (planirano v1.2+). Klijent NE sme da self-update polje (firestore.rules
 * odbija). Kad Cloud Function iz receipt validation-a upiše `isPremium = true`,
 * ovaj Flow će emit-ovati novo stanje i UI će se sam osveži (feature dugmad se otključavaju).
 *
 * `premiumUntil` handling: ako je expiry u prošlosti, tretiramo user-a kao NE-premium
 * čak i ako je `isPremium == true`. Time izbegavamo zavisnost od Cloud Function-a
 * da odmah flip-uje flag na expiry momenat.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class PremiumChecker @Inject constructor(
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uidFlow = MutableStateFlow(auth.currentUser?.uid)

    init {
        // FirebaseAuth ne izlaže Flow za auth state change-ove direktno kao Firestore.
        // Registrujemo listener i push-ujemo trenutni uid u internal StateFlow. Kad
        // user sign-in/sign-out-uje, ovaj emit trigger-uje re-subscribe na novi user doc.
        auth.addAuthStateListener { fa ->
            _uidFlow.value = fa.currentUser?.uid
        }
    }

    /**
     * Emit-uje `true` kad je trenutni user premium i entitlement je aktivan
     * (`premiumUntil` u budućnosti ili null za lifetime). `false` u svim ostalim
     * slučajevima uključujući signed-out state.
     */
    val isPremiumFlow: StateFlow<Boolean> = _uidFlow
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(false)
            else userRepository.observeUser(uid)
                .map { user -> user?.isEntitledToPremium() ?: false }
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Sync check bez suspending — koristi trenutnu vrednost StateFlow-a. */
    val isPremiumNow: Boolean get() = isPremiumFlow.value
}

/**
 * Kombinuje flag i expiry check. Extension na model da može biti unit-test-ovana
 * bez Firestore/Auth setup-a.
 */
fun UserModel.isEntitledToPremium(now: Long = System.currentTimeMillis()): Boolean {
    if (!isPremium) return false
    val until = premiumUntil?.time ?: return true // null = lifetime / no expiry
    return until > now
}
