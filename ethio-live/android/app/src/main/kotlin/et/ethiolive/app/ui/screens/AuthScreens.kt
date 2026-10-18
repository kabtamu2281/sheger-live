package et.ethiolive.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import et.ethiolive.app.AppContainer
import et.ethiolive.app.BuildConfig
import et.ethiolive.app.R
import et.ethiolive.app.data.Account
import et.ethiolive.app.data.BackendConfig
import et.ethiolive.app.ui.components.PrimaryButton
import et.ethiolive.app.ui.components.errorMessage
import et.ethiolive.app.ui.gifts.GiftArt
import et.ethiolive.app.ui.theme.Brand
import et.ethiolive.core.Validation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private enum class AuthMethod { PHONE, EMAIL }
private enum class EmailMode { SIGN_IN, SIGN_UP, RECOVER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(container: AppContainer, activity: Activity) {
    val scope = rememberCoroutineScope()
    var method by remember { mutableStateOf(AuthMethod.PHONE) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    // Phone
    var phone by remember { mutableStateOf("") }
    var codeSentTo by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    // Email
    var emailMode by remember { mutableStateOf(EmailMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var recoveryCodeSent by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf("") }

    val msgCheckEmail = stringResource(R.string.auth_check_email)
    val msgPasswordReset = stringResource(R.string.auth_password_reset_done)

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true; error = null; info = null
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        GiftArt("adey", 64.dp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.auth_welcome), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.auth_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AuthMethod.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = method == m,
                    onClick = { method = m; error = null; info = null },
                    shape = SegmentedButtonDefaults.itemShape(i, AuthMethod.entries.size),
                ) { Text(stringResource(if (m == AuthMethod.PHONE) R.string.auth_phone else R.string.auth_email)) }
            }
        }
        Spacer(Modifier.height(20.dp))

        when (method) {
            AuthMethod.PHONE -> {
                if (codeSentTo == null) {
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.auth_phone_label)) },
                        placeholder = { Text("+251 9…") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    val normalized = Validation.normalizePhone(phone)
                    PrimaryButton(stringResource(R.string.auth_send_code), loading = busy, enabled = normalized != null, onClick = {
                        run { container.auth.sendPhoneCode(normalized!!); codeSentTo = normalized }
                    })
                } else {
                    Text(stringResource(R.string.auth_code_sent_to, codeSentTo!!), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code, onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.auth_code_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(stringResource(R.string.auth_verify), loading = busy, enabled = code.length == 6, onClick = {
                        run { container.auth.verifyPhoneCode(codeSentTo!!, code) }
                    })
                    TextButton(onClick = { codeSentTo = null; code = "" }) { Text(stringResource(R.string.auth_change_number)) }
                }
            }
            AuthMethod.EMAIL -> {
                OutlinedTextField(
                    value = email, onValueChange = { email = it.trim() },
                    label = { Text(stringResource(R.string.auth_email_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (emailMode != EmailMode.RECOVER || recoveryCodeSent) {
                    if (emailMode == EmailMode.RECOVER) {
                        OutlinedTextField(
                            value = recoveryCode, onValueChange = { recoveryCode = it.filter(Char::isDigit).take(6) },
                            label = { Text(stringResource(R.string.auth_code_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text(stringResource(if (emailMode == EmailMode.RECOVER) R.string.auth_new_password else R.string.auth_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        supportingText = if (emailMode != EmailMode.SIGN_IN) ({ Text(stringResource(R.string.auth_password_rule)) }) else null,
                    )
                }
                Spacer(Modifier.height(16.dp))
                val emailOk = Validation.isEmail(email)
                val passwordOk = Validation.passwordProblem(password) == null
                when (emailMode) {
                    EmailMode.SIGN_IN -> PrimaryButton(stringResource(R.string.auth_sign_in), loading = busy, enabled = emailOk && password.isNotEmpty(), onClick = {
                        run { container.auth.signInEmail(email, password) }
                    })
                    EmailMode.SIGN_UP -> PrimaryButton(stringResource(R.string.auth_create_account), loading = busy, enabled = emailOk && passwordOk, onClick = {
                        run { container.auth.signUpEmail(email, password); info = msgCheckEmail }
                    })
                    EmailMode.RECOVER -> if (!recoveryCodeSent) {
                        PrimaryButton(stringResource(R.string.auth_send_reset_code), loading = busy, enabled = emailOk, onClick = {
                            run { container.auth.sendRecoveryCode(email); recoveryCodeSent = true }
                        })
                    } else {
                        PrimaryButton(stringResource(R.string.auth_reset_password), loading = busy, enabled = recoveryCode.length == 6 && passwordOk, onClick = {
                            run { container.auth.resetPasswordWithCode(email, recoveryCode, password); info = msgPasswordReset }
                        })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        emailMode = if (emailMode == EmailMode.SIGN_IN) EmailMode.SIGN_UP else EmailMode.SIGN_IN
                        recoveryCodeSent = false; error = null; info = null
                    }) { Text(stringResource(if (emailMode == EmailMode.SIGN_IN) R.string.auth_need_account else R.string.auth_have_account)) }
                    if (emailMode == EmailMode.SIGN_IN) TextButton(onClick = { emailMode = EmailMode.RECOVER; error = null }) {
                        Text(stringResource(R.string.auth_forgot_password))
                    }
                }
            }
        }

        if (BackendConfig.googleSignInConfigured) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.auth_google), enabled = !busy, onClick = {
                run { googleSignIn(activity, container) }
            })
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(errorMessage(it), color = MaterialTheme.colorScheme.error)
        }
        info?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Brand.Emerald)
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.auth_terms_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private suspend fun googleSignIn(activity: Activity, container: AppContainer) {
    val rawNonce = UUID.randomUUID().toString()
    val hashed = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray()).joinToString("") { "%02x".format(it) }
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setNonce(hashed)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    try {
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        container.auth.signInWithGoogleIdToken(credential.idToken, rawNonce)
    } catch (_: GetCredentialCancellationException) {
        // User closed the sheet; nothing to report.
    }
}

/** Second signup step: username, display name and date of birth (the server enforces the minimum age). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteSignupScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf<LocalDate?>(null) }
    var acceptedTerms by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val today = LocalDate.now()
    val usernameProblem = if (username.isEmpty()) null else Validation.usernameProblem(username)
    val ageProblem = birthDate?.let { Validation.signupAgeProblem(it, today) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Text(stringResource(R.string.signup_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.signup_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = username, onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(24) },
            label = { Text(stringResource(R.string.username)) }, prefix = { Text("@") }, singleLine = true,
            isError = usernameProblem != null,
            supportingText = { Text(stringResource(if (usernameProblem != null) R.string.err_username_invalid else R.string.username_rule)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = displayName, onValueChange = { displayName = it.take(40) },
            label = { Text(stringResource(R.string.display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        SecondaryButton(
            birthDate?.let { stringResource(R.string.birth_date_value, it.toString()) } ?: stringResource(R.string.birth_date_pick),
            onClick = { showPicker = true },
        )
        Text(stringResource(R.string.birth_date_why), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp))
        if (ageProblem != null) Text(stringResource(R.string.err_age_ineligible), color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = acceptedTerms, onCheckedChange = { acceptedTerms = it })
            Text(stringResource(R.string.signup_accept_terms), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
        PrimaryButton(
            stringResource(R.string.signup_finish),
            loading = busy,
            enabled = username.length >= 3 && usernameProblem == null && displayName.isNotBlank() && birthDate != null && ageProblem == null && acceptedTerms,
            onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        container.auth.completeSignup(username, displayName.trim(), birthDate!!, currentLanguage().tag)
                        container.refreshAccount()
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e }
                    busy = false
                }
            },
        )
        TextButton(onClick = { scope.launch { container.signOut() } }) { Text(stringResource(R.string.sign_out)) }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = birthDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            yearRange = (today.year - 100)..today.year,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { birthDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}

/** Suspended or banned accounts see the reason and can appeal; nothing else is reachable. */
@Composable
fun RestrictedAccountScreen(container: AppContainer, account: Account) {
    val scope = rememberCoroutineScope()
    var notices by remember { mutableStateOf<List<et.ethiolive.app.data.ModerationNotice>>(emptyList()) }
    var appealText by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { notices = runCatching { container.profiles.myModerationActions() }.getOrDefault(emptyList()) }
    val latest = notices.firstOrNull()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Text(stringResource(if (account.profile.accountStatus == "banned") R.string.restricted_banned else R.string.restricted_suspended),
            style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        latest?.let {
            Text(stringResource(R.string.restricted_reason, it.reason))
            it.expiresAt?.let { until -> Text(stringResource(R.string.restricted_until, until.take(16).replace('T', ' '))) }
        }
        Spacer(Modifier.height(20.dp))
        if (latest != null && !sent) {
            OutlinedTextField(appealText, { appealText = it.take(1000) }, label = { Text(stringResource(R.string.appeal_message)) },
                minLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            PrimaryButton(stringResource(R.string.appeal_submit), enabled = appealText.trim().length >= 10, onClick = {
                scope.launch {
                    error = null
                    try { container.profiles.appeal(latest.id, appealText.trim()); sent = true } catch (e: Exception) { error = e }
                }
            })
        }
        if (sent) Text(stringResource(R.string.appeal_sent), color = Brand.Emerald)
        error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = { scope.launch { container.signOut() } }) { Text(stringResource(R.string.sign_out)) }
    }
}
