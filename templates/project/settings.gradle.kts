rootProject.name = "AwakeProject"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // An Awake Core release Maven Central refused under its publishing limits, served by Core at
        // Central's coordinates.
        maven("https://awakekt.github.io/awake/") { mavenContent { includeGroupAndSubgroups("com.awakekt.awake") } }
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
        // An Awake Core release Maven Central refused under its publishing limits, served by Core at
        // Central's coordinates.
        maven("https://awakekt.github.io/awake/") { mavenContent { includeGroupAndSubgroups("com.awakekt.awake") } }
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
