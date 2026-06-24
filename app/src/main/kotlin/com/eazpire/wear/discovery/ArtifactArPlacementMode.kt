package com.eazpire.wear.discovery

/** How the user places the artifact in the AR world. */
enum class ArtifactArPlacementMode {
    /** Tap the on-screen hand to place where the camera is pointing. */
    Default,

    /** Manual placement when the real GPS spot is unreachable (banner shown). */
    SpecialManual,
}
