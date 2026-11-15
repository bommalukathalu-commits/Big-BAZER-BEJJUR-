package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    onAuthError: (String) -> Unit = {},
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = resolveWebClientId(context)
    val activity = context.findActivity()
    if (clientId == null || activity == null) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: NoCredentialException) {
            onUnauthenticated()
        } catch (_: GetCredentialCancellationException) {
            onUnauthenticated()
        } catch (e: Exception) {
            Log.w("Auth", "Auto sign-in failed", e)
            val sha1 = getAppSigningSha1(context)
            onAuthError("Auto Sign-In failed (${e::class.java.simpleName}): ${e.localizedMessage ?: "Unknown error"}\nSHA-1: $sha1")
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = resolveWebClientId(context)
    if (clientId == null) {
        onAuthError("Firebase google-services.json (default_web_client_id) is missing in this build.")
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("Unable to find Activity context for Google Sign-In.")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val primaryRequest = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val result = try {
                credentialManager.getCredential(activity, primaryRequest)
            } catch (primaryErr: GetCredentialCancellationException) {
                throw primaryErr
            } catch (primaryErr: GetCredentialException) {
                Log.w("Auth", "Primary GetSignInWithGoogleOption failed, trying GetGoogleIdOption fallback", primaryErr)
                val fallbackOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackOption)
                    .build()
                try {
                    credentialManager.getCredential(activity, fallbackRequest)
                } catch (fallbackErr: GetCredentialCancellationException) {
                    throw fallbackErr
                } catch (_: Exception) {
                    throw primaryErr
                }
            }

            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            val msg = e.localizedMessage?.takeIf { it.isNotBlank() }
            if (msg != null && !msg.contains("cancelled", ignoreCase = true) && !msg.contains("canceled", ignoreCase = true)) {
                val sha1 = getAppSigningSha1(context)
                onAuthError("Sign-In interrupted ($msg)\nSHA-1: $sha1")
            } else {
                onAuthCancelled()
            }
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            val sha1 = getAppSigningSha1(context)
            val detail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Sign in failed"
            onAuthError("${e::class.java.simpleName}: $detail\nSHA-1: $sha1")
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

fun getAuthDiagnosticSummary(context: Context): String {
    val clientOk = resolveWebClientId(context) != null
    val sha1 = getAppSigningSha1(context)
    return "Config: ${if (clientOk) "OK" else "MISSING"} • SHA-1: $sha1"
}

@Suppress("DEPRECATION")
fun getAppSigningSha1(context: Context): String = runCatching {
    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
    } else {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNATURES
        )
    }
    val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
    } else {
        pkgInfo.signatures?.firstOrNull()?.toByteArray()
    } ?: return "Unavailable"
    val digest = MessageDigest.getInstance("SHA1").digest(certBytes)
    digest.joinToString(":") { "%02X".format(it) }
}.getOrElse { "Unavailable" }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DiscouragedApi")
private fun resolveWebClientId(context: Context): String? {
    val resId = context.resources.getIdentifier(
        "default_web_client_id",
        "string",
        context.packageName
    )
    if (resId == 0) return null
    return runCatching { context.getString(resId) }.getOrNull()?.takeIf { it.isNotBlank() }
}
