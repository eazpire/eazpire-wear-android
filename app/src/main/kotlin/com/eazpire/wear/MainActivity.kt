package com.eazpire.wear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eazpire.wear.core.api.WearPlayerApi
import com.eazpire.wear.core.auth.SecureTokenStore
import com.eazpire.wear.health.StepSyncHelper
import com.eazpire.wear.sync.WearPlayerAuthSync
import com.eazpire.wear.theme.EazWearColors
import com.eazpire.wear.theme.EazWearTheme
import com.eazpire.wear.ui.AuthScreen
import com.eazpire.wear.ui.FeedScreen
import com.eazpire.wear.ui.HubScreen
import com.eazpire.wear.ui.MoveScreen
import com.eazpire.wear.ui.SquadScreen
import com.eazpire.wear.ui.VaultScreen
import com.eazpire.wear.ui.VerifyScreen

class MainActivity : ComponentActivity() {
    private lateinit var tokenStore: SecureTokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        tokenStore = SecureTokenStore.get(this)
        setContent {
            val callbackUri = remember { intent?.data?.toString() }
            EazWearTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    color = EazWearColors.Background,
                ) {
                    var loggedIn by remember { mutableStateOf(tokenStore.isLoggedIn()) }
                    if (!loggedIn) {
                        AuthScreen(
                            tokenStore = tokenStore,
                            onAuthSuccess = {
                                loggedIn = true
                                WearPlayerAuthSync.push(this, tokenStore)
                            },
                            oauthCallbackUri = callbackUri,
                        )
                    } else {
                        WearMainShell(
                            tokenStore = tokenStore,
                            onSignOut = {
                                tokenStore.clear()
                                WearPlayerAuthSync.clear(this)
                                loggedIn = false
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

private enum class WearTab { Hub, Feed, Verify, Squad, Vault, Move }

@Composable
private fun WearMainShell(tokenStore: SecureTokenStore, onSignOut: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
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
                containerColor = EazWearColors.Panel.copy(alpha = 0.95f),
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
