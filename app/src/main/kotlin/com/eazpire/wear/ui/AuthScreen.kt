package com.eazpire.wear.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazpire.wear.R
import com.eazpire.wear.auth.AuthBrowserLauncher
import com.eazpire.wear.auth.AuthErrorMessages
import com.eazpire.wear.auth.AuthException
import com.eazpire.wear.auth.CreatorSessionHandoff
import com.eazpire.wear.auth.OAuthPkceStore
import com.eazpire.wear.auth.PkceUtils
import com.eazpire.wear.auth.PlayReviewAuthService
import com.eazpire.wear.auth.SessionProbeResult
import com.eazpire.wear.auth.SessionResolver
import com.eazpire.wear.auth.ShopifyAuthService
import com.eazpire.wear.core.auth.SecureTokenStore
import com.eazpire.wear.sync.WearPlayerAuthSync
import com.eazpire.wear.theme.EazWearColors
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    tokenStore: SecureTokenStore,
    sessionHandoff: CreatorSessionHandoff,
    sessionProbeResult: SessionProbeResult,
    showQrButton: Boolean,
    onAuthSuccess: () -> Unit,
    onJoinWithQr: () -> Unit,
    oauthCallbackUri: String? = null,
    autoJoinTrigger: Int = 0,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val authService = remember { ShopifyAuthService() }
    val reviewAuthService = remember { PlayReviewAuthService() }
    var codeVerifier by remember { mutableStateOf<String?>(null) }
    var savedState by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var callbackHandled by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var reviewEmail by remember { mutableStateOf("") }
    var reviewCode by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        reviewEmail = context.getString(R.string.play_review_default_email)
    }

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

    fun joinNow() {
        scope.launch {
            isLoading = true
            error = null
            callbackHandled = false
            try {
                if (SessionResolver.tryCreatorHandoff(sessionHandoff, tokenStore)) {
                    WearPlayerAuthSync.push(context, tokenStore)
                    onAuthSuccess()
                    return@launch
                }
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

    LaunchedEffect(autoJoinTrigger) {
        if (autoJoinTrigger > 0) joinNow()
    }

    fun submitReviewLogin() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val result = reviewAuthService.exchangeReviewCredentials(reviewEmail, reviewCode)
                tokenStore.saveJwt(result.jwt, result.ownerId)
                WearPlayerAuthSync.push(context, tokenStore)
                showReviewDialog = false
                reviewCode = ""
                onAuthSuccess()
            } catch (e: Exception) {
                error = AuthErrorMessages.fromThrowable(e)
            } finally {
                isLoading = false
            }
        }
    }

    if (showReviewDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) showReviewDialog = false },
            title = { Text(stringResource(R.string.play_review_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.play_review_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reviewEmail,
                        onValueChange = { reviewEmail = it },
                        label = { Text(stringResource(R.string.play_review_email_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reviewCode,
                        onValueChange = { reviewCode = it },
                        label = { Text(stringResource(R.string.play_review_code_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { submitReviewLogin() }, enabled = !isLoading && reviewCode.isNotBlank()) {
                    Text(stringResource(R.string.play_review_submit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReviewDialog = false }, enabled = !isLoading) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.eazpire_wear_logo),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(72.dp),
        )
        Spacer(modifier = Modifier.height(32.dp))
        Surface(
            color = EazWearColors.AuthCard,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, EazWearColors.PanelBorder, RoundedCornerShape(20.dp)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.welcome_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = EazWearColors.AuthCardMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(28.dp))
                if (isLoading) {
                    CircularProgressIndicator(color = EazWearColors.Orange)
                } else {
                    Button(
                        onClick = { joinNow() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { showReviewDialog = true })
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EazWearColors.Orange,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.join_now),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (showQrButton) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onJoinWithQr,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.join_with_qr),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                error?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
