plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.library") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

subprojects {
    if (System.getenv("CI") != "true") {
        val localRoot = System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir")
        val safeName = path.removePrefix(":").replace(":", "_")
        layout.buildDirectory.set(file("$localRoot/eazpire-wear-android-build/$safeName"))
    }
}
