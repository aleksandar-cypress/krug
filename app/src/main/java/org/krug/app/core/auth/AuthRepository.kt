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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.krug.app.R
import org.krug.app.core.user.UserRepository
import timber.log.Timber

sealed interface SignInResult {
    data class Success(val user: FirebaseUser) : SignInResult
    data class Failure(val reason: Reason, val cause: Throwable? = null) : SignInResult

    enum class Reason { Cancelled, NoGoogleAccount, Network, ProviderDisabled, Unknown }
}

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val firebaseAuth: FirebaseAuth,
    private val userRepository: UserRepository,
) {
    val currentUser: FirebaseUser? get() = firebaseAuth.currentUser

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

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

        val idToken: String = try {
            val response = credentialManager.getCredential(activityContext, request)
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
            userRepository.upsertOnSignIn(user, deviceLabel())
            SignInResult.Success(user)
        } catch (e: Exception) {
            Timber.e(e, "Firebase sign-in failed")
            SignInResult.Failure(SignInResult.Reason.Network, e)
        }
    }

    suspend fun signInAnonymously(): SignInResult {
        return try {
            val authResult = firebaseAuth.signInAnonymously().await()
            val user = authResult.user
                ?: return SignInResult.Failure(SignInResult.Reason.Unknown)
            userRepository.upsertOnSignIn(user, deviceLabel())
            SignInResult.Success(user)
        } catch (e: Exception) {
            Timber.e(e, "Anonymous sign-in failed")
            val reason = if (e.message.orEmpty().contains("restricted to administrators", ignoreCase = true)) {
                SignInResult.Reason.ProviderDisabled
            } else {
                SignInResult.Reason.Network
            }
            SignInResult.Failure(reason, e)
        }
    }

    suspend fun signOut(activityContext: Context) {
        firebaseAuth.signOut()
        runCatching {
            CredentialManager.create(activityContext)
                .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
        }.onFailure { Timber.w(it, "clearCredentialState failed") }
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
}
