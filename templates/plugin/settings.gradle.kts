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
        // Google's mirror of Maven Central first, Central after it: a 429 or 5xx ends the lookup at the
        // repository that answers it, and Central throttles these runners; only a 404 moves on (#1592).
        // The mirror carries no Awake release, so com.awakekt goes straight to Central.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralMirror"
            content {
                excludePortalOnly()
                excludeGroupAndSubgroups("com.awakekt")
            }
        }
        mavenCentral { content { excludePortalOnly() } }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralMirror"
            content {
                excludeGroupAndSubgroups("com.awakekt")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":plugin")
