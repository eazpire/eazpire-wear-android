package com.eazpire.wear

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.eazpire.wear.auth.CreatorSessionHandoff
import com.eazpire.wear.auth.PendingQrClaimStore
import com.eazpire.wear.auth.SessionProbeResult
import com.eazpire.wear.auth.SessionResolver
import com.eazpire.wear.core.api.WearPlayerApi
import com.eazpire.wear.core.auth.SecureTokenStore
import com.eazpire.wear.health.StepSyncHelper
import com.eazpire.wear.sync.WearPlayerAuthSync
import com.eazpire.wear.theme.EazWearColors
import com.eazpire.wear.theme.EazWearTheme
import com.eazpire.wear.ui.AuthScreen
import com.eazpire.wear.ui.FeedScreen
import com.eazpire.wear.ui.HubScreen
import com.eazpire.wear.ui.MintGateScreen
import com.eazpire.wear.ui.MoveScreen
import com.eazpire.wear.ui.QrScanScreen
import com.eazpire.wear.ui.SquadScreen
import com.eazpire.wear.ui.VaultScreen
import com.eazpire.wear.ui.VerifyScreen
import com.eazpire.wear.ui.WearBootSplashScreen
import kotlinx.coroutines.delay

private enum class AppPhase { Booting, Auth, QrMintGate, QrScan, Main }

class MainActivity : ComponentActivity() {
    private lateinit var tokenStore: SecureTokenStore
    private lateinit var creatorHandoff: CreatorSessionHandoff

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        tokenStore = SecureTokenStore.get(this)
        creatorHandoff = CreatorSessionHandoff(this)
        setContent {
            val callbackUri = remember { intent?.data?.toString() }
            val context = LocalContext.current
            EazWearTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var phase by remember { mutableStateOf(AppPhase.Booting) }
                    var splashProgress by remember { mutableIntStateOf(0) }
                    var sessionProbe by remember {
                        mutableStateOf(SessionProbeResult.NoSession)
                    }

                    LaunchedEffect(Unit) {
                        val started = System.currentTimeMillis()
                        sessionProbe = if (tokenStore.isLoggedIn()) {
                            SessionProbeResult.LoggedIn
                        } else if (SessionResolver.isCreatorInstalled(context)) {
                            SessionProbeResult.HasExternalSession
                        } else {
                            SessionProbeResult.NoSession
                        }
                        splashProgress = 16
                        val elapsed = System.currentTimeMillis() - started
                        if (elapsed < 850L) delay(850L - elapsed)
                        phase = if (tokenStore.isLoggedIn()) AppPhase.Main else AppPhase.Auth
                    }

                    when (phase) {
                        AppPhase.Booting -> WearBootSplashScreen(targetProgress = splashProgress)
                        AppPhase.Auth -> AuthScreen(
                            tokenStore = tokenStore,
                            sessionProbeResult = sessionProbe,
                            creatorHandoff = creatorHandoff,
                            onAuthSuccess = { phase = AppPhase.Main },
                            onOpenQrFlow = { phase = AppPhase.QrMintGate },
                            oauthCallbackUri = callbackUri,
                        )
                        AppPhase.QrMintGate -> QrMintGateRoute(
                            tokenStore = tokenStore,
                            onBack = { phase = AppPhase.Auth },
                            onReadyToScan = { phase = AppPhase.QrScan },
                        )
                        AppPhase.QrScan -> QrScanScreen(
                            onTokenScanned = { token ->
                                if (tokenStore.isLoggedIn()) {
                                    val ownerId = tokenStore.getOwnerId().orEmpty()
                                    val jwt = tokenStore.getJwt()
                                    if (!ownerId.isBlank() && !jwt.isNullOrBlank()) {
                                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            WearPlayerApi(jwt = jwt).artifactsClaimQr(token, ownerId)
                                        }
                                    }
                                    phase = AppPhase.Main
                                } else {
                                    PendingQrClaimStore.save(context, token)
                                    phase = AppPhase.Auth
                                }
                            },
                            onCancel = { phase = AppPhase.Auth },
                        )
                        AppPhase.Main -> WearMainShell(
                            tokenStore = tokenStore,
                            onSignOut = {
                                tokenStore.clear()
                                WearPlayerAuthSync.clear(this@MainActivity)
                                sessionProbe = SessionProbeResult.NoSession
                                phase = AppPhase.Auth
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data != null) {
            recreate()
        }
    }
}

@Composable
private fun QrMintGateRoute(
    tokenStore: SecureTokenStore,
    onBack: () -> Unit,
    onReadyToScan: () -> Unit,
) {
    var checking by remember { mutableStateOf(true) }
    var hasArtifact by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        checking = true
        hasArtifact = if (tokenStore.isLoggedIn()) {
            val ownerId = tokenStore.getOwnerId().orEmpty()
            val jwt = tokenStore.getJwt()
            if (ownerId.isNotBlank() && !jwt.isNullOrBlank()) {
                val inv = WearPlayerApi(jwt = jwt).artifactsInventory(ownerId)
                WearPlayerApi(jwt = jwt).countMintedArtifacts(inv) >= 1
            } else {
                false
            }
        } else {
            false
        }
        checking = false
        if (hasArtifact) onReadyToScan()
    }

    when {
        checking -> WearBootSplashScreen(targetProgress = 8)
        hasArtifact -> Box(Modifier.fillMaxSize())
        else -> MintGateScreen(onBack = onBack)
    }
}

private enum class WearTab { Hub, Feed, Verify, Squad, Vault, Move }

@Composable
private fun WearMainShell(tokenStore: SecureTokenStore, onSignOut: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val ownerId = tokenStore.getOwnerId().orEmpty()
    val api = remember(tokenStore.getJwt()) { WearPlayerApi(jwt = tokenStore.getJwt()) }
    val stepSync = remember { StepSyncHelper(context) }
    val tabs = WearTab.entries

    Scaffold(
        containerColor = EazWearColors.Background,
        topBar = {
            TextButton(onClick = onSignOut, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    stringResource(R.string.sign_out),
                    color = EazWearColors.TextSubtle,
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = EazWearColors.Panel,
                contentColor = EazWearColors.TextPrimary,
            ) {
                tabs.forEachIndexed { index, wearTab ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EazWearColors.Orange,
                            selectedTextColor = EazWearColors.Orange,
                            unselectedIconColor = EazWearColors.TextMuted,
                            unselectedTextColor = EazWearColors.TextMuted,
                            indicatorColor = EazWearColors.Orange.copy(alpha = 0.18f),
                        ),
                        icon = {
                            Icon(
                                when (wearTab) {
                                    WearTab.Hub -> Icons.Default.Home
                                    WearTab.Feed -> Icons.Default.Feed
                                    WearTab.Verify -> Icons.Default.Verified
                                    WearTab.Squad -> Icons.Default.Groups
                                    WearTab.Vault -> Icons.Default.Inventory2
                                    WearTab.Move -> Icons.Default.DirectionsWalk
                                },
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(
                                when (wearTab) {
                                    WearTab.Hub -> stringResource(R.string.tab_hub)
                                    WearTab.Feed -> stringResource(R.string.tab_feed)
                                    WearTab.Verify -> stringResource(R.string.tab_verify)
                                    WearTab.Squad -> stringResource(R.string.tab_squad)
                                    WearTab.Vault -> stringResource(R.string.tab_vault)
                                    WearTab.Move -> stringResource(R.string.tab_move)
                                },
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tabs[tab]) {
                WearTab.Hub -> HubScreen(api, ownerId, context)
                WearTab.Feed -> FeedScreen(api)
                WearTab.Verify -> VerifyScreen(api, ownerId)
                WearTab.Squad -> SquadScreen(api, ownerId)
                WearTab.Vault -> VaultScreen(api, ownerId)
                WearTab.Move -> MoveScreen(api, stepSync)
            }
        }
    }
}
