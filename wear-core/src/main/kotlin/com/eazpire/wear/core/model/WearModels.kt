package com.eazpire.wear.core.model

data class BalanceSnapshot(
    val eazBalance: Long = 0L,
    val rawJson: String = "",
)

data class FeedPost(
    val id: String,
    val authorName: String,
    val bodyText: String,
    val likeCount: Int = 0,
)

data class MoveToEarnStatus(
    val stepsToday: Long = 0L,
    val eazEarnedToday: Long = 0L,
    val dailyClaimAvailable: Boolean = false,
)

data class WearEarnStatus(
    val actions: List<String> = emptyList(),
    val claimedToday: Int = 0,
)

data class NetworkMember(
    val ownerId: String,
    val displayName: String,
    val level: Int = 0,
)

data class VerifyItem(
    val id: String,
    val title: String,
    val outcome: String,
)

data class ArtifactItem(
    val instanceId: String,
    val name: String,
    val rarity: String = "",
)

data class MoveSession(
    val id: String = "",
    val characterId: Long = 0L,
    val energyMinutes: Int = 0,
    val startedAt: Long = 0L,
    val endsAt: Long = 0L,
    val remainingMs: Long = 0L,
    val status: String = "",
)

data class DiscoveryStatus(
    val unlocked: Boolean = false,
    val totalCellsDiscovered: Long = 0L,
    val session: MoveSession? = null,
    val homeCityId: String = "",
)

data class DiscoverySyncResult(
    val cellsNew: Int = 0,
    val cellsDuplicate: Int = 0,
    val cellsRejected: Int = 0,
)
