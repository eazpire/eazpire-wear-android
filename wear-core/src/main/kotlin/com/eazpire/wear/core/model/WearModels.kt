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

/** Artifact shown on the Move discovery map (2D artwork; optional GLB when available). */
data class MapArtifactProduct(
    val id: String,
    val name: String,
    val imageUrl: String,
    val modelUrl: String? = null,
    val slotType: String = "",
)

object MapArtifactDefaults {
    /**
     * Neon Pulse Oversized Tee — real Shopify product from worker feedSeedDemo
     * (FALLBACK_DEMO_PRODUCTS). Used when showcase/inventory API has no artwork yet.
     */
    const val DEMO_PRODUCT_ID = "fallback-demo-tee-1"
    const val SHOP_CARD_PRODUCT_ID = "fallback-demo-shop-card"

    /** Bundled GLB for map preview + AR (assets folder, no network). */
    const val DEMO_GLB_ASSET = "artifacts/demo-artifact.glb"

    /** Bundled animated shop-card GLB (embedded glTF animation). */
    const val SHOP_CARD_ANIMATED_GLB_ASSET = "artifacts/shop-card-animated.glb"

    fun isAnimatedGlb(modelUrl: String?): Boolean {
        val path = modelUrl?.trim().orEmpty()
        return path == SHOP_CARD_ANIMATED_GLB_ASSET ||
            path.endsWith("shop-card-animated.glb")
    }

    fun demoFallback(): MapArtifactProduct = MapArtifactProduct(
        id = DEMO_PRODUCT_ID,
        name = "Neon Pulse Oversized Tee",
        imageUrl = "https://cdn.shopify.com/s/files/1/0739/5203/5098/files/3457070110335593502_2048.jpg?v=1765037188",
        modelUrl = DEMO_GLB_ASSET,
        slotType = "upper_body",
    )

    fun shopCardAnimatedFallback(): MapArtifactProduct = MapArtifactProduct(
        id = SHOP_CARD_PRODUCT_ID,
        name = "Shop Card III",
        imageUrl = "https://cdn.shopify.com/s/files/1/0739/5203/5098/files/3457070110335593502_2048.jpg?v=1765037188",
        modelUrl = SHOP_CARD_ANIMATED_GLB_ASSET,
        slotType = "collectible",
    )

    /** Default pair of map artifacts (static tee + animated shop card). */
    fun demoMapArtifacts(): List<MapArtifactProduct> = listOf(
        demoFallback(),
        shopCardAnimatedFallback(),
    )
}
