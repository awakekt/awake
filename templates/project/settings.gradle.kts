rootProject.name = "AwakeProject"

pluginManagement {
    // Modules only the Plugin Portal serves: each plugin's marker and implementation. The repositories
    // listed before the Portal exclude them, so an error from one of those (a Central 429) can't stop
    // the search for them before it reaches the Portal (#1592).
    val portalOnly = listOf(
        "org.gradle.toolchains.foojay-resolver-convention:org.gradle.toolchains.foojay-resolver-convention.gradle.plugin",
        "org.gradle.toolchains:foojay-resolver",
        "io.ktor.plugin:io.ktor.plugin.gradle.plugin",
        "io.ktor.plugin:plugin",
        "com.google.cloud.tools:jib-gradle-plugin",
    )
    fun RepositoryContentDescriptor.excludePortalOnly() =
        portalOnly.forEach { excludeModule(it.substringBefore(':'), it.substringAfter(':')) }
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                excludePortalOnly()
            }
        }
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
        // An Awake Core release Maven Central refused under its publishing limits, served by Core at
        // Central's coordinates.
        maven("https://awakekt.github.io/awake/") { mavenContent { includeGroupAndSubgroups("com.awakekt.awake") } }
        mavenLocal { content { includeGroupAndSubgroups("com.awakekt.awake") } }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // Not io.ygdrasil: the wgpu4k builds are served by the Awake mirror below, and a cached module
        // recorded as coming from Sonatype would be searched only there.
        maven("https://central.sonatype.com/repository/maven-snapshots/") { mavenContent { excludeGroup("io.ygdrasil") } }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralMirror"
            content {
                excludeGroupAndSubgroups("com.awakekt")
            }
        }
        mavenCentral()
        // An Awake Core release Maven Central refused under its publishing limits, served by Core at
        // Central's coordinates.
        maven("https://awakekt.github.io/awake/") { mavenContent { includeGroupAndSubgroups("com.awakekt.awake") } }
        mavenLocal { content { includeGroupAndSubgroups("com.awakekt.awake") } }
        // The wgpu4k builds the WebGPU backend pins, copied byte for byte by Awake Core, for when
        // Central's 90-day snapshot cleanup removes them.
        maven("https://awakekt.github.io/awake/") { mavenContent { includeGroup("io.ygdrasil") } }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":app:androidApp")
include(":app:desktopApp")
include(":app:shared")
include(":app:webApp")
include(":core")
include(":server")
