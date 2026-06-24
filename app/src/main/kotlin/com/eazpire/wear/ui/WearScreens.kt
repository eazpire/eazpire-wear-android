package com.eazpire.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazpire.wear.discovery.ArtifactArScreen
import com.eazpire.wear.discovery.DiscoveryExploreControlsRow
import com.eazpire.wear.discovery.DiscoveryExploreMapView
import com.eazpire.wear.discovery.DiscoveryExploreState
import com.eazpire.wear.discovery.DiscoveryExploreStatsBar
import com.eazpire.wear.discovery.DiscoveryInitialLocationEffect
import com.eazpire.wear.discovery.GeoPoint
import com.eazpire.wear.discovery.MapArtifactViewState
import com.eazpire.wear.discovery.isWithinArtifactRange
import com.eazpire.wear.discovery.placeArtifactNearUser
import com.eazpire.wear.discovery.placeSecondArtifactNearFirst
import com.eazpire.wear.core.model.MapArtifactDefaults
import com.eazpire.wear.core.model.MapArtifactProduct
import com.eazpire.wear.theme.EazWearColors
import java.util.Locale
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
fun MoveScreen(api: WearPlayerApi) {
    var steps by remember { mutableStateOf("—") }
    var eazToday by remember { mutableStateOf("—") }
    var exploring by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mapArtifacts by remember { mutableStateOf<List<MapArtifactProduct>>(emptyList()) }
    var artifactAnchors by remember { mutableStateOf<Map<String, GeoPoint>>(emptyMap()) }
    var showAr by remember { mutableStateOf(false) }
    var arArtifact by remember { mutableStateOf<MapArtifactProduct?>(null) }
    var arArtifactLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var artifactHint by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val liveExploring by DiscoveryExploreState.exploring.collectAsState()
    val paused by DiscoveryExploreState.paused.collectAsState()
    val currentLocation by DiscoveryExploreState.currentLocation.collectAsState()
    val trackPoints by DiscoveryExploreState.trackPoints.collectAsState()
    val sessionDistanceM by DiscoveryExploreState.sessionDistanceM.collectAsState()
    val sessionSteps by DiscoveryExploreState.sessionSteps.collectAsState()

    LaunchedEffect(currentLocation, mapArtifacts) {
        val user = currentLocation ?: return@LaunchedEffect
        if (mapArtifacts.size < 2 || artifactAnchors.size >= 2) return@LaunchedEffect
        val first = placeArtifactNearUser(user.lat, user.lng, user.altitudeM)
        val second = placeSecondArtifactNearFirst(first)
        artifactAnchors = mapOf(
            mapArtifacts[0].id to first,
            mapArtifacts[1].id to second,
        )
    }

    fun load() {
        scope.launch {
            error = null
            runCatching {
                val m = api.moveToEarnStatusModel()
                steps = m.stepsToday.toString()
                eazToday = m.eazEarnedToday.toString()
                val d = api.discoveryStatusModel()
                val apiExploring = d.session != null
                exploring = liveExploring || apiExploring
                if (apiExploring && !liveExploring) {
                    DiscoveryExploreState.markExploring(true)
                }
                if (mapArtifacts.isEmpty()) {
                    mapArtifacts = api.resolveMapArtifactProducts()
                }
            }.onFailure { error = it.message }
        }
    }

    LaunchedEffect(Unit) { load() }

    DiscoveryInitialLocationEffect(enabled = !exploring)

    LaunchedEffect(liveExploring) {
        exploring = liveExploring || exploring
    }

    LaunchedEffect(exploring) {
        while (exploring) {
            kotlinx.coroutines.delay(30_000)
            load()
        }
    }

    val distanceKm = String.format(Locale.US, "%.2f km", sessionDistanceM / 1000.0)
    val displaySteps = if (liveExploring) sessionSteps.toString() else steps

    val mapArtifactViews = mapArtifacts.mapNotNull { product ->
        val location = artifactAnchors[product.id] ?: return@mapNotNull null
        MapArtifactViewState(
            id = product.id,
            location = location,
            modelUrl = product.modelUrl.orEmpty(),
            name = product.name,
            inRange = isWithinArtifactRange(currentLocation, location),
            autoAnimate = MapArtifactDefaults.isAnimatedGlb(product.modelUrl),
            onClick = {
                arArtifact = product
                arArtifactLocation = location
                showAr = true
            },
            onOutOfRangeClick = {
                artifactHint = "too_far"
            },
        )
    }

    val nearestInRange = mapArtifactViews.firstOrNull { it.inRange }

    if (showAr && arArtifact != null) {
        ArtifactArScreen(
            artifact = arArtifact!!,
            userLocation = currentLocation,
            artifactLocation = arArtifactLocation,
            onClose = {
                showAr = false
                arArtifact = null
                arArtifactLocation = null
            },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DiscoveryExploreMapView(
            currentLocation = currentLocation,
            trackPoints = trackPoints,
            mapArtifacts = mapArtifactViews,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DiscoveryExploreStatsBar(
                steps = displaySteps,
                distanceKm = if (sessionDistanceM > 0) distanceKm else "0.00 km",
                eazToday = eazToday,
            )
            if (mapArtifactViews.isNotEmpty() && currentLocation != null) {
                val statusArtifact = nearestInRange ?: mapArtifactViews.first()
                Text(
                    text = when {
                        nearestInRange != null -> stringResource(
                            R.string.artifact_map_in_range,
                            statusArtifact.name,
                        )
                        else -> stringResource(
                            R.string.artifact_map_out_of_range,
                            statusArtifact.name,
                        )
                    },
                    color = if (nearestInRange != null) EazWearColors.HubOrange else EazWearColors.HubMuted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (nearestInRange != null) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EazWearColors.HubPanel.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            if (artifactHint == "too_far") {
                Text(
                    stringResource(R.string.artifact_map_tap_denied),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (error != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { load() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }

        DiscoveryExploreControlsRow(
            exploring = exploring,
            paused = paused,
            onExploreChange = { active ->
                exploring = active
                if (!active) load()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
