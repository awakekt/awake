rootProject.name = "awake-plugin-template"

pluginManagement {
    // Modules only the Plugin Portal serves: each plugin's marker and implementation. The repositories
    // listed before the Portal exclude them, so an error from one of those (a Central 429) can't stop
    // the search for them before it reaches the Portal (#1592).
    val portalOnly = listOf(
        "org.gradle.toolchains.foojay-resolver-convention:org.gradle.toolchains.foojay-resolver-convention.gradle.plugin",
        "org.gradle.toolchains:foojay-resolver",
    )
    fun RepositoryContentDescriptor.excludePortalOnly() =
        portalOnly.forEach { excludeModule(it.substringBefore(':'), it.substringAfter(':')) }
    repositories {
        mavenCentral { content { excludePortalOnly() } }
        // Central's mirror, right after Central: a module or plugin marker that a Central edge answers
        // 404 for, though Central serves it, is then found here: a 404 moves on to the next repository (#1592).
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralMirror"
            content { excludePortalOnly() }
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":plugin")
