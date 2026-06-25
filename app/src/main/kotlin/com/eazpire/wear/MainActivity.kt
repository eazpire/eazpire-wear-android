package com.eazpire.wear

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.eazpire.wear.auth.CreatorSessionHandoff
import com.eazpire.wear.auth.PendingQrClaimStore
import com.eazpire.wear.auth.SessionProbeResult
import com.eazpire.wear.auth.SessionResolver
import com.eazpire.wear.core.api.WearPlayerApi
import com.eazpire.wear.core.auth.SecureTokenStore
import com.eazpire.wear.core.brand.BrandAssetsRepository
import com.eazpire.wear.sync.WearPlayerAuthSync
import com.eazpire.wear.theme.EazWearColors
import com.eazpire.wear.theme.EazWearHubBackground
import com.eazpire.wear.theme.EazWearScreenBackground
import com.eazpire.wear.theme.EazWearTheme
import com.eazpire.wear.ui.AuthScreen
import com.eazpire.wear.ui.FeedScreen
import com.eazpire.wear.ui.HubScreen
import com.eazpire.wear.ui.MintGateScreen
import com.eazpire.wear.ui.MoveScreen
import com.eazpire.wear.ui.QrScanScreen
import com.eazpire.wear.ui.SquadScreen
import com.eazpire.wear.ui.VaultScreen
import com.eazpire.wear.ui.WalletScreen
import com.eazpire.wear.ui.WearBootSplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicBoolean

private const val BOOT_MIN_MS = 850L

private enum class AppScreen { Booting, Auth, QrScan, MintGate, Main }

class MainActivity : ComponentActivity() {
    private lateinit var tokenStore: SecureTokenStore
    private lateinit var sessionHandoff: CreatorSessionHandoff
    private val oauthCallbackUriState = mutableStateOf<String?>(null)
    private val keepSplashOnScreen = AtomicBoolean(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen.get() }
        super.onCreate(savedInstanceState)
        // API 35+: edge-to-edge default; insets via Compose root (systemBarsPadding) — same as Creator app.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFormat(PixelFormat.RGBA_8888)
        window.decorView.setBackgroundColor(AndroidColor.parseColor("#07080D"))
        window.statusBarColor = AndroidColor.parseColor("#07080D")
        window.navigationBarColor = AndroidColor.parseColor("#05060A")
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        tokenStore = SecureTokenStore.get(this)
        sessionHandoff = CreatorSessionHandoff(this)
        oauthCallbackUriState.value = intent?.data?.toString()
        setContent {
            val oauthCallbackUri = oauthCallbackUriState.value
            EazWearTheme {
                WearApp(
                    tokenStore = tokenStore,
                    sessionHandoff = sessionHandoff,
                    oauthCallbackUri = oauthCallbackUri,
                    onContentReady = { keepSplashOnScreen.set(false) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { oauthCallbackUriState.value = it.toString() }
    }
}

@Composable
private fun WearApp(
    tokenStore: SecureTokenStore,
    sessionHandoff: CreatorSessionHandoff,
    oauthCallbackUri: String?,
    onContentReady: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(AppScreen.Booting) }
    var sessionProbe by remember { mutableStateOf(SessionProbeResult.NoSession) }
    var bootProgress by remember { mutableIntStateOf(4) }
    var autoJoinTrigger by remember { mutableIntStateOf(0) }

    val activity = context as? ComponentActivity

    // Release Android 12 system splash immediately so WearBootSplashScreen (web-matching) is visible.
    LaunchedEffect(Unit) {
        BrandAssetsRepository.get(context).refreshIfStale()
        onContentReady()
    }

    SideEffect {
        activity?.window?.let { window ->
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            when (screen) {
                AppScreen.Booting,
                AppScreen.Auth,
                -> {
                    window.statusBarColor = AndroidColor.parseColor("#07080D")
                    window.navigationBarColor = AndroidColor.parseColor("#05060A")
                    window.decorView.setBackgroundColor(AndroidColor.parseColor("#07080D"))
                    controller.isAppearanceLightStatusBars = false
                    controller.isAppearanceLightNavigationBars = false
                }
                else -> {
                    window.statusBarColor = AndroidColor.parseColor("#07080D")
                    window.navigationBarColor = AndroidColor.parseColor("#05060A")
                    window.decorView.setBackgroundColor(AndroidColor.parseColor("#07080D"))
                    controller.isAppearanceLightStatusBars = false
                    controller.isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    suspend fun resolveLoggedInDestination(): AppScreen = withContext(Dispatchers.IO) {
        val ownerId = tokenStore.getOwnerId().orEmpty()
        if (ownerId.isBlank()) return@withContext AppScreen.Auth

        PendingQrClaimStore.peek(context)?.let { token ->
            val api = WearPlayerApi(jwt = tokenStore.getJwt())
            val claim = api.artifactsClaimQr(token, ownerId)
            if (claim.optBoolean("ok", false)) {
                PendingQrClaimStore.consume(context)
            }
        }

        val api = WearPlayerApi(jwt = tokenStore.getJwt())
        val inventory = api.artifactsInventory(ownerId)
        if (api.countMintedArtifacts(inventory) < 1) AppScreen.MintGate else AppScreen.Main
    }

    fun onAuthenticated() {
        scope.launch {
            screen = resolveLoggedInDestination()
            if (screen == AppScreen.Main) {
                WearPlayerAuthSync.push(context, tokenStore)
            }
        }
    }

    LaunchedEffect(Unit) {
        val startedAt = System.currentTimeMillis()
        bootProgress = 8
        try {
            withTimeout(12_000L) {
                val probe = SessionResolver.probeWithCreator(context, tokenStore, sessionHandoff)
                sessionProbe = probe
                bootProgress = 16

                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < BOOT_MIN_MS) delay(BOOT_MIN_MS - elapsed)

                screen = when (probe) {
                    SessionProbeResult.LoggedIn -> {
                        val dest = resolveLoggedInDestination()
                        if (dest == AppScreen.Main) WearPlayerAuthSync.push(context, tokenStore)
                        dest
                    }
                    SessionProbeResult.HasExternalSession,
                    SessionProbeResult.NoSession,
                    -> AppScreen.Auth
                }
            }
        } catch (_: TimeoutCancellationException) {
            screen = AppScreen.Auth
            sessionProbe = SessionProbeResult.NoSession
        }
    }

    when (screen) {
        AppScreen.Booting -> WearBootSplashScreen(targetProgress = bootProgress)
        AppScreen.Auth -> EazWearScreenBackground {
            AuthScreen(
            tokenStore = tokenStore,
            sessionHandoff = sessionHandoff,
            sessionProbeResult = sessionProbe,
            showQrButton = sessionProbe == SessionProbeResult.NoSession,
            onAuthSuccess = { onAuthenticated() },
            onJoinWithQr = { screen = AppScreen.QrScan },
            oauthCallbackUri = oauthCallbackUri,
            autoJoinTrigger = autoJoinTrigger,
            )
        }
        AppScreen.QrScan -> EazWearScreenBackground {
            QrScanScreen(
            onTokenScanned = { token ->
                PendingQrClaimStore.save(context, token)
                screen = AppScreen.Auth
                autoJoinTrigger += 1
            },
            onCancel = { screen = AppScreen.Auth },
            )
        }
        AppScreen.MintGate -> EazWearScreenBackground {
            MintGateScreen(
            onBack = {
                scope.launch {
                    screen = if (tokenStore.isLoggedIn()) AppScreen.Main else AppScreen.Auth
                }
            },
            )
        }
        AppScreen.Main -> EazWearHubBackground {
            WearMainShell(
            tokenStore = tokenStore,
            onSignOut = {
                tokenStore.clear()
                WearPlayerAuthSync.clear(context)
                sessionProbe = SessionProbeResult.NoSession
                screen = AppScreen.Auth
            },
            )
        }
    }
}

private enum class WearTab { Hub, Feed, Wallet, Squad, Vault, Move }

@Composable
private fun WearMainShell(tokenStore: SecureTokenStore, onSignOut: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val ownerId = tokenStore.getOwnerId().orEmpty()
    val api = remember(tokenStore.getJwt()) { WearPlayerApi(jwt = tokenStore.getJwt()) }
    val tabs = WearTab.entries

    Scaffold(
        containerColor = EazWearColors.HubBg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TextButton(onClick = onSignOut, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    stringResource(R.string.sign_out),
                    color = EazWearColors.HubMuted,
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = EazWearColors.HubPanel,
                contentColor = EazWearColors.HubText,
            ) {
                tabs.forEachIndexed { index, wearTab ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EazWearColors.HubOrange,
                            selectedTextColor = EazWearColors.HubOrange,
                            unselectedIconColor = EazWearColors.HubMuted,
                            unselectedTextColor = EazWearColors.HubMuted,
                            indicatorColor = EazWearColors.HubOrange.copy(alpha = 0.18f),
                        ),
                        icon = {
                            Icon(
                                when (wearTab) {
                                    WearTab.Hub -> Icons.Default.Home
                                    WearTab.Feed -> Icons.Default.Feed
                                    WearTab.Wallet -> Icons.Default.AccountBalanceWallet
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
                                    WearTab.Wallet -> stringResource(R.string.tab_wallet)
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
        val moveTabIndex = tabs.indexOf(WearTab.Move)
        var moveTabMounted by remember { mutableStateOf(tab == moveTabIndex) }
        LaunchedEffect(tab) {
            if (tab == moveTabIndex) moveTabMounted = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Keep Move mounted after first visit so MapView is not recreated on every tab switch.
            if (moveTabMounted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (tab == moveTabIndex) 0f else -1f)
                        .alpha(if (tab == moveTabIndex) 1f else 0f),
                ) {
                    MoveScreen(api, ownerId)
                }
            }

            if (tab != moveTabIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f),
                ) {
                    when (tabs[tab]) {
                        WearTab.Hub -> HubScreen(api, ownerId, context)
                        WearTab.Feed -> FeedScreen(api)
                        WearTab.Wallet -> WalletScreen(api, ownerId)
                        WearTab.Squad -> SquadScreen(api, ownerId)
                        WearTab.Vault -> VaultScreen(api, ownerId)
                        WearTab.Move -> Unit
                    }
                }
            }
        }
    }
}
