rootProject.name = "AwakeTemplate"

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
        mavenCentral { content { excludePortalOnly() } }
        mavenLocal()
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
        mavenCentral()
        mavenLocal()
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
