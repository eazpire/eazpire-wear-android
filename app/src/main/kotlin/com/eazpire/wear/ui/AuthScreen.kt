package com.eazpire.wear.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eazpire.wear.R
import com.eazpire.wear.auth.AuthBrowserLauncher
import com.eazpire.wear.auth.AuthErrorMessages
import com.eazpire.wear.auth.AuthException
import com.eazpire.wear.auth.OAuthPkceStore
import com.eazpire.wear.auth.PkceUtils
import com.eazpire.wear.auth.ShopifyAuthService
import com.eazpire.wear.core.auth.SecureTokenStore
import com.eazpire.wear.sync.WearPlayerAuthSync
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    tokenStore: SecureTokenStore,
    onAuthSuccess: () -> Unit,
    oauthCallbackUri: String? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val authService = remember { ShopifyAuthService() }
    var codeVerifier by remember { mutableStateOf<String?>(null) }
    var savedState by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var callbackHandled by remember { mutableStateOf(false) }

    fun handleCallback(url: String) {
        if (callbackHandled) return
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code") ?: return
        val state = uri.getQueryParameter("state") ?: return
        callbackHandled = true
        val verifier = when {
            state == savedState && codeVerifier != null -> {
                OAuthPkceStore.clear(context)
                codeVerifier!!
            }
            else -> OAuthPkceStore.consume(context, state)
        } ?: run {
            error = AuthErrorMessages.fromThrowable(AuthException("Invalid state"))
            callbackHandled = false
            return
        }
        scope.launch {
            isLoading = true
            error = null
            try {
                val tokens = authService.exchangeCodeForTokens(code, verifier)
                val bearer = tokens.accessToken.ifBlank { tokens.idToken }
                val result = authService.exchangeShopifyTokenForJwt(bearer, tokens.idToken.ifBlank { null })
                tokenStore.saveJwt(result.jwt, result.ownerId)
                WearPlayerAuthSync.push(context, tokenStore)
                codeVerifier = null
                savedState = null
                callbackHandled = false
                onAuthSuccess()
            } catch (e: Exception) {
                callbackHandled = false
                error = AuthErrorMessages.fromThrowable(e)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(oauthCallbackUri) {
        oauthCallbackUri?.let { handleCallback(it) }
    }

    fun startLogin() {
        scope.launch {
            isLoading = true
            error = null
            callbackHandled = false
            try {
                val endpoints = authService.discoverEndpoints()
                val verifier = PkceUtils.generateCodeVerifier()
                val state = PkceUtils.generateState()
                codeVerifier = verifier
                savedState = state
                OAuthPkceStore.save(context, state, verifier)
                val url = authService.buildAuthorizationUrl(endpoints.authorizationEndpoint, verifier, state)
                AuthBrowserLauncher.launchOAuth(context, url)
            } catch (e: Exception) {
                error = AuthErrorMessages.fromThrowable(e)
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.welcome_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { startLogin() }) {
                Text(stringResource(R.string.sign_in))
            }
        }
        error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
