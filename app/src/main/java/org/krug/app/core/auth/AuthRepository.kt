package org.krug.app.core.auth

import android.content.Context
import android.os.Build
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.krug.app.R
import org.krug.app.core.user.UserRepository
import timber.log.Timber

sealed interface SignInResult {
    data class Success(val user: FirebaseUser) : SignInResult
    data class Failure(val reason: Reason, val cause: Throwable? = null) : SignInResult

    enum class Reason {
        Cancelled,
        NoGoogleAccount,
        Network,
        ProviderDisabled,
        /** Firebase Auth javlja da je nalog disabled u Console-u ili obrisan. */
        AccountDisabled,
        /** ID token nije validan ili je istekao — retry sa fresh credential-om. */
        InvalidCredential,
        Unknown,
    }
}

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val firebaseAuth: FirebaseAuth,
    private val userRepository: UserRepository,
    private val deviceRegistry: org.krug.app.core.device.DeviceRegistry,
) {
    val currentUser: FirebaseUser? get() = firebaseAuth.currentUser

    // Singleton scope za shared auth flow — živi koliko i AuthRepository singleton (proces).
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Jedan AuthStateListener za ceo proces — ranije je svaki collector pravio svoj
     * callbackFlow i registrovao zasebnog Firebase listener-a (6+ duplikata kroz VM-ove
     * + CrashlyticsContext). shareIn sa WhileSubscribed drži tačno jedan listener dok ima
     * collector-a, gasi 5s posle poslednjeg unsubscribe-a. replay=1 daje novim collector-ima
     * trenutnu vrednost odmah (bez čekanja sledećeg auth event-a).
     */
    private val authStateFlow: SharedFlow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.shareIn(repoScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    fun observeAuthState(): Flow<FirebaseUser?> = authStateFlow

    suspend fun signInWithGoogle(activityContext: Context): SignInResult {
        val credentialManager = CredentialManager.create(activityContext)
        val webClientId = appContext.getString(R.string.default_web_client_id)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build(),
            )
            .build()

        // Hard timeout — CredentialManager.getCredential može da visi (Google Play Services
        // problem, network stuck, dijalog zaboravljen otvoren). Bez timeout-a, user vidi
        // beskonačan spinner. 15s je dovoljno za normalan dialog interaction + odgovor.
        val idToken: String = try {
            val response = withTimeoutOrNull(CREDENTIAL_TIMEOUT_MS) {
                credentialManager.getCredential(activityContext, request)
            } ?: run {
                Timber.w("Google credential request timed out after %dms", CREDENTIAL_TIMEOUT_MS)
                return SignInResult.Failure(SignInResult.Reason.Network)
            }
            val credential = response.credential
            if (credential !is CustomCredential || credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return SignInResult.Failure(SignInResult.Reason.Unknown)
            }
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GetCredentialException) {
            Timber.w(e, "Google credential request failed")
            return SignInResult.Failure(mapCredentialError(e), e)
        }

        return try {
            val authResult = firebaseAuth
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .await()
            val user = authResult.user
                ?: return SignInResult.Failure(SignInResult.Reason.Unknown)
            refreshDatabaseAuth(user)
            userRepository.upsertOnSignIn(user, deviceLabel())
            syncFcmToken(user.uid)
            Timber.i("Sign-in: google, uid=%s", user.uid)
            SignInResult.Success(user)
        } catch (e: Exception) {
            Timber.e(e, "Firebase sign-in failed")
            SignInResult.Failure(mapFirebaseAuthError(e), e)
        }
    }

    /**
     * Posle sign-in-a, fetch trenutni FCM token i uploaduj u Firestore. Ovo je za
     * scenario kada je onNewToken() fajrovao PRE nego što je user bio signed-in
     * (fresh install, prvi launch pre auth) — token bi bio poznat Firebase-u ali ne
     * bi bio u našem Firestore user record-u. Cloud Functions čita fcmToken iz
     * Firestore-a; bez ovog helper-a, push-evi ne bi radili do prvog token rotation-a
     * koji Firebase periodično radi (~6 meseci).
     *
     * runCatching + best-effort — ako token fetch ne uspe (rare), sign-in ne pada;
     * onNewToken će nadoknaditi kad Firebase sledeći put rotira token.
     */
    private suspend fun syncFcmToken(uid: String) {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.isNotBlank()) {
                userRepository.updateFcmToken(uid, token)
                // Multi-device: registruj ovaj uređaj sa svojim tokenom u devices
                // subcollection. Cloud Function-i mogu da fanout-uju push na sve
                // registrovane uređaje (ne samo poslednji koji se ulogovao).
                deviceRegistry.registerDevice(uid, token, deviceLabel())
                Timber.i("FCM token synced post sign-in for uid=%s (len=%d)", uid, token.length)
            }
        }.onFailure { Timber.w(it, "syncFcmToken failed for uid=%s", uid) }
    }

    /**
     * Force token refresh + RTDB connection bounce. Bez ovog, posle
     * delete-account → re-sign-in, RTDB klijent može da drži stari token
     * obrisanog korisnika i odbija sve write-ove kao "Permission denied".
     */
    private suspend fun refreshDatabaseAuth(user: FirebaseUser) {
        runCatching {
            user.getIdToken(true).await()
            val db = FirebaseDatabase.getInstance()
            db.goOffline()
            db.goOnline()
        }.onFailure { Timber.w(it, "refreshDatabaseAuth failed") }
    }

    suspend fun signInAnonymously(): SignInResult {
        return try {
            // Hard timeout — Firebase Auth servis može da visi (network stuck, server down).
            // Konzistentno sa Google sign-in CREDENTIAL_TIMEOUT_MS (15s).
            val authResult = withTimeoutOrNull(CREDENTIAL_TIMEOUT_MS) {
                firebaseAuth.signInAnonymously().await()
            } ?: run {
                Timber.w("Anonymous sign-in timed out after %dms", CREDENTIAL_TIMEOUT_MS)
                return SignInResult.Failure(SignInResult.Reason.Network)
            }
            val user = authResult.user
                ?: return SignInResult.Failure(SignInResult.Reason.Unknown)
            // Posle delete-account → sign-in, RTDB klijent može da drži stari (sad
            // nevažeći) auth token i odbija upis sa "Permission denied". Bounce-uj
            // konekciju da se prihvati novi token.
            refreshDatabaseAuth(user)
            userRepository.upsertOnSignIn(user, deviceLabel())
            syncFcmToken(user.uid)
            Timber.i("Sign-in: anonymous, uid=%s", user.uid)
            SignInResult.Success(user)
        } catch (e: Exception) {
            Timber.e(e, "Anonymous sign-in failed")
            SignInResult.Failure(mapFirebaseAuthError(e), e)
        }
    }

    /**
     * Mapira FirebaseAuthException → SignInResult.Reason. Prati Firebase error codes
     * (https://firebase.google.com/docs/reference/admin/error-handling) sa fallback-om
     * na message-based detekciju za stara izdanja SDK-a koje ne setuju error code.
     */
    private fun mapFirebaseAuthError(e: Exception): SignInResult.Reason {
        val msg = e.message.orEmpty().lowercase()
        val errorCode = (e as? com.google.firebase.FirebaseException)?.let {
            (it as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
        }
        return when {
            errorCode == "ERROR_USER_DISABLED" -> SignInResult.Reason.AccountDisabled
            errorCode == "ERROR_USER_NOT_FOUND" -> SignInResult.Reason.AccountDisabled
            errorCode == "ERROR_INVALID_CREDENTIAL" -> SignInResult.Reason.InvalidCredential
            errorCode == "ERROR_INVALID_USER_TOKEN" -> SignInResult.Reason.InvalidCredential
            // Anonymous provider explicitno isključen u Console-u.
            "restricted to administrators" in msg ||
                "anonymous accounts are not enabled" in msg ||
                errorCode == "ERROR_OPERATION_NOT_ALLOWED" -> SignInResult.Reason.ProviderDisabled
            "network" in msg || "timeout" in msg -> SignInResult.Reason.Network
            "disabled" in msg || "not found" in msg -> SignInResult.Reason.AccountDisabled
            "invalid" in msg && "credential" in msg -> SignInResult.Reason.InvalidCredential
            else -> SignInResult.Reason.Network
        }
    }

    suspend fun signOut(activityContext: Context) {
        val outgoingUid = firebaseAuth.currentUser?.uid
        Timber.i("Sign-out requested for uid=%s", outgoingUid ?: "(none)")
        // Unregister ovaj uređaj iz registry-ja pre sign-out-a — bez ovog, tuđe device-e
        // koje su i dalje aktivne bi trošile push kvote na tokene koji ne bi mogli da se
        // odjave (auth već ovaj token više nije validan Firebase-u).
        outgoingUid?.let { deviceRegistry.unregisterDevice(it) }
        // Bounce RTDB konekciju PRE signOut-a — drops aktivne ValueEventListenere koji
        // su zakačeni sa starim auth token-om. Bez ovog, listeneri mogu da prožive
        // tranziciju (Firebase ih ne raskida automatski na auth change) i pokušaju
        // read sa stale token-om što vraća "Permission denied".
        runCatching { FirebaseDatabase.getInstance().goOffline() }
            .onFailure { Timber.w(it, "RTDB goOffline before signOut failed") }
        firebaseAuth.signOut()
        runCatching {
            CredentialManager.create(activityContext)
                .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
        }.onFailure { Timber.w(it, "clearCredentialState failed") }
        // Ostavljamo RTDB offline; kad korisnik ponovo signIn, refreshDatabaseAuth()
        // poziva goOnline() pa konekcija oživi sa novim token-om.
    }

    /**
     * GDPR — obriši Firebase Auth user. Vraća true ako je uspešno, false ako Firebase
     * traži recent re-login (FirebaseAuthRecentLoginRequiredException). U tom slučaju
     * pozivalac treba da odradi `reauthenticateWithGoogle` pa pozove ovaj metod opet.
     * Pozivalac treba da uradi cleanup Firestore/RTDB pre ovog poziva.
     */
    suspend fun deleteAccount(): Boolean {
        val u = firebaseAuth.currentUser ?: return true
        return try {
            u.delete().await()
            true
        } catch (e: Exception) {
            Timber.w(e, "deleteAccount: Firebase auth delete failed (likely needs reauth)")
            false
        }
    }

    /**
     * Re-credential za sensitive operacije (delete-account) na Google nalozima.
     * Firebase odbija `user.delete()` ako je sign-in stariji od ~5min — uzima fresh
     * Google ID token preko istog CredentialManager flow-a kao i sign-in, pa zove
     * `reauthenticate(...)` na trenutnom user-u (bez signOut/signIn cycle-a).
     */
    suspend fun reauthenticateWithGoogle(activityContext: Context): SignInResult.Reason? {
        val user = firebaseAuth.currentUser
            ?: return SignInResult.Reason.Unknown
        if (user.isAnonymous) {
            // Anonimni nemaju Google provider — reauth nije relevantan, no-op.
            return null
        }
        val credentialManager = CredentialManager.create(activityContext)
        val webClientId = appContext.getString(R.string.default_web_client_id)
        // setFilterByAuthorizedAccounts(true) + AutoSelect: ako user već ima vezan
        // Google nalog kroz CredentialManager, prikazaće chooser pre-filtriran na taj nalog
        // da reauth ne ode na pogrešan email (account mismatch → Firebase odbija).
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(true)
                    .setAutoSelectEnabled(true)
                    .build(),
            )
            .build()

        val idToken: String = try {
            val response = withTimeoutOrNull(CREDENTIAL_TIMEOUT_MS) {
                credentialManager.getCredential(activityContext, request)
            } ?: run {
                Timber.w("Reauth credential request timed out after %dms", CREDENTIAL_TIMEOUT_MS)
                return SignInResult.Reason.Network
            }
            val credential = response.credential
            if (credential !is CustomCredential || credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return SignInResult.Reason.Unknown
            }
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GetCredentialException) {
            Timber.w(e, "Reauth credential request failed")
            return mapCredentialError(e)
        }

        return try {
            user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
            Timber.i("Reauth: google, uid=%s", user.uid)
            null
        } catch (e: Exception) {
            Timber.w(e, "Firebase reauthenticate failed")
            mapFirebaseAuthError(e)
        }
    }

    private fun deviceLabel(): String {
        val brand = Build.MANUFACTURER.replaceFirstChar { it.uppercaseChar() }
        val model = Build.MODEL
        return if (model.startsWith(brand, ignoreCase = true)) model else "$brand $model"
    }

    private fun mapCredentialError(e: GetCredentialException): SignInResult.Reason {
        val msg = e.message.orEmpty().lowercase()
        return when {
            "cancel" in msg -> SignInResult.Reason.Cancelled
            "no credentials" in msg || "no accounts" in msg -> SignInResult.Reason.NoGoogleAccount
            "network" in msg -> SignInResult.Reason.Network
            else -> SignInResult.Reason.Unknown
        }
    }

    private companion object {
        const val CREDENTIAL_TIMEOUT_MS = 15_000L
    }
}
