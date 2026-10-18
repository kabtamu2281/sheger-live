package et.ethiolive.app.data

import et.ethiolive.core.AppException
import et.ethiolive.core.ServerError
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

enum class AuthState { LOADING, SIGNED_OUT, SIGNED_IN }

/** Real Supabase authentication: phone OTP, email + password, Google ID token. No local or fake sessions. */
class AuthRepository(private val backend: Backend) {
    private val auth get() = backend.client.auth

    val state: Flow<AuthState> = auth.sessionStatus.map {
        when (it) {
            is SessionStatus.Authenticated -> AuthState.SIGNED_IN
            is SessionStatus.NotAuthenticated -> AuthState.SIGNED_OUT
            is SessionStatus.RefreshFailure -> AuthState.SIGNED_IN // tokens retry automatically; keep the user in
            else -> AuthState.LOADING
        }
    }

    suspend fun sendPhoneCode(phoneE164: String) = backend.call {
        auth.signInWith(OTP) { phone = phoneE164; createUser = true }
    }

    suspend fun verifyPhoneCode(phoneE164: String, code: String) = backend.call {
        auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = phoneE164, token = code)
    }

    suspend fun signUpEmail(address: String, pass: String) = backend.call {
        auth.signUpWith(Email, redirectUrl = "ethiolive://auth/confirmed") { email = address; password = pass }
    }

    suspend fun signInEmail(address: String, pass: String) = backend.call {
        auth.signInWith(Email) { email = address; password = pass }
    }

    suspend fun sendRecoveryCode(address: String) = backend.call {
        auth.resetPasswordForEmail(address, redirectUrl = "ethiolive://auth/recovery")
    }

    suspend fun resetPasswordWithCode(address: String, code: String, newPassword: String) = backend.call {
        auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = address, token = code)
        auth.updateUser { password = newPassword }
    }

    suspend fun signInWithGoogleIdToken(token: String, nonceValue: String?) = backend.call {
        auth.signInWith(IDToken) {
            idToken = token
            provider = Google
            nonce = nonceValue
        }
    }

    suspend fun signOut() = backend.call { auth.signOut() }

    suspend fun completeSignup(username: String, displayName: String, birthDate: LocalDate, language: String) {
        backend.rpcUnit(
            "complete_signup",
            params(
                "p_username" to username,
                "p_display_name" to displayName,
                "p_date_of_birth" to birthDate.toString(),
                "p_language" to language,
                "p_terms_version" to TERMS_VERSION,
            ),
        )
    }

    /** Null when the account exists in auth but has not finished the profile step. */
    suspend fun myAccount(): Account? = try {
        backend.rpc<Account>("my_account")
    } catch (e: AppException) {
        if (e.error == ServerError.PROFILE_REQUIRED) null else throw e
    }

    companion object {
        const val TERMS_VERSION = "2026-10-17"
    }
}
