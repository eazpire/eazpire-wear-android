pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "eazpire-wear-android"
include(":app")
include(":wear-core")
// Monorepo: ../wear-core (sibling). Mirror repo sync: ./wear-core (same root).
project(":wear-core").projectDir = listOf(file("wear-core"), file("../wear-core"))
    .first { it.isDirectory }
