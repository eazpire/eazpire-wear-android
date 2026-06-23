package com.eazpire.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eazpire.wear.R
import com.eazpire.wear.core.api.WearPlayerApi
import com.eazpire.wear.sync.WearPlayerAuthSync
import kotlinx.coroutines.launch

@Composable
fun HubScreen(api: WearPlayerApi, ownerId: String, context: android.content.Context) {
    var balance by remember { mutableStateOf("—") }
    var username by remember { mutableStateOf("—") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val bal = api.balanceSnapshot(ownerId)
                balance = bal.eazBalance.toString()
                val user = api.accountUsername(ownerId)
                username = user.optString("username", user.optString("display_name", ownerId))
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(ownerId) { load() }

    when {
        loading -> WearLoading()
        error != null -> WearError(error!!) { load() }
        else -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.welcome_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            WearStatCard(stringResource(R.string.eaz_balance), balance)
            WearStatCard("Username", username)
            Button(
                onClick = { WearPlayerAuthSync.push(context, com.eazpire.wear.core.auth.SecureTokenStore.get(context)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.pair_watch)) }
        }
    }
}

@Composable
fun FeedScreen(api: WearPlayerApi) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val json = api.feedList()
                lines = api.parseFeedPosts(json).map { "${it.authorName}: ${it.bodyText.take(120)}" }
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    when {
        loading -> WearLoading()
        error != null -> WearError(error!!) { load() }
        else -> WearSimpleList(lines, stringResource(R.string.no_posts))
    }
}

@Composable
fun VerifyScreen(api: WearPlayerApi, ownerId: String) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val json = api.verifyCompleted(ownerId)
                lines = api.parseVerifyItems(json).map { "${it.title} (${it.outcome})" }
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(ownerId) { load() }

    when {
        loading -> WearLoading()
        error != null -> WearError(error!!) { load() }
        else -> WearSimpleList(lines, "No verify items")
    }
}

@Composable
fun SquadScreen(api: WearPlayerApi, ownerId: String) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val json = api.network(ownerId)
                lines = api.parseNetwork(json).map { "${it.displayName} · L${it.level}" }
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(ownerId) { load() }

    when {
        loading -> WearLoading()
        error != null -> WearError(error!!) { load() }
        else -> WearSimpleList(lines, stringResource(R.string.no_network))
    }
}

@Composable
fun VaultScreen(api: WearPlayerApi, ownerId: String) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val json = api.artifactsInventory(ownerId)
                lines = api.parseArtifacts(json).map { "${it.name} · ${it.rarity}" }
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(ownerId) { load() }

    when {
        loading -> WearLoading()
        error != null -> WearError(error!!) { load() }
        else -> WearSimpleList(lines, stringResource(R.string.no_artifacts))
    }
}

@Composable
fun MoveScreen(api: WearPlayerApi, stepSync: com.eazpire.wear.health.StepSyncHelper) {
    var status by remember { mutableStateOf("—") }
    var steps by remember { mutableStateOf("—") }
    var discoveryCells by remember { mutableStateOf("—") }
    var exploring by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var syncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val m = api.moveToEarnStatusModel()
                status = "EAZ today: ${m.eazEarnedToday} · claim: ${m.dailyClaimAvailable}"
                steps = m.stepsToday.toString()
                val d = api.discoveryStatusModel()
                discoveryCells = d.totalCellsDiscovered.toString()
                exploring = d.session != null
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    when {
        loading -> WearLoading()
        error != null -> WearError(error!!) { load() }
        else -> Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WearStatCard(stringResource(R.string.steps_today), steps)
            WearStatCard(stringResource(R.string.discovery_cells), discoveryCells)
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.eazpire.wear.discovery.DiscoveryExploreControls(exploring = exploring) { active ->
                exploring = active
                load()
            }
            Button(
                onClick = {
                    scope.launch {
                        syncing = true
                        runCatching {
                            val local = stepSync.readStepsToday()
                            api.moveToEarnSyncSteps(local)
                            load()
                        }.onFailure { error = it.message }
                        syncing = false
                    }
                },
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (syncing) stringResource(R.string.loading) else stringResource(R.string.sync_steps)) }
        }
    }
}
