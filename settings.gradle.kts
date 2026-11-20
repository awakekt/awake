/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

rootProject.name = "Awake"

include(":awake:core:math")
include(":awake:core:math2d")
include(":awake:core:spatial")
include(":awake:core:particles")
include(":awake:core:lod")
include(":awake:core:camera")
include(":awake:core:pool")
include(":awake:core:graphics2d")
include(":awake:core:color")
include(":awake:core:input")
include(":awake:core:logging")
include(":awake:core:diagnostics")
include(":awake:core:image")
include(":awake:core:host")
include(":awake:core:io")
include(":awake:core:schema")
include(":awake:project")
include(":awake:project:runtime")
include(":awake:project:cli")
include(":awake:project:blueprint")
include(":awake:project:transport-ktor")
include(":awake:project:transport-browser")
include(":awake:core:geometry")
include(":awake:core:animation")
include(":awake:core:text")
include(":awake:core:audio")
include(":awake:core:di")
include(":awake:core:config")
include(":awake:core:settings")
include(":awake:core:state")
include(":awake:core:localization")
include(":awake:core:expression")
include(":awake:asset:gltf")
include(":awake:asset:sprite")
include(":awake:asset:terrain")
include(":awake:terrain")
include(":awake:kit:terrain-layers")
include(":awake:asset:mesh-optimizer")
include(":awake:asset:mesh-lod")
include(":awake:asset:texture-ktx")
include(":awake:asset:texture-cook")
include(":awake:asset:texture-ktx-loader")
include(":awake:asset:shaders")
include(":awake:asset:shader-pack")
include(":awake:asset:shader-dsl")
include(":awake:asset:shader-document")
include(":awake:asset:shader-compiler")
include(":awake:ecs")
include(":awake:ecs:benchmark")
include(":awake:ui:benchmark")
include(":awake:scene:scene3d:benchmark")
include(":awake:scene:scene-core")
include(":awake:scene:controls")
include(":awake:world")
include(":awake:navigation")
include(":awake:particles")
include(":awake:worldtext")
include(":awake:trail")
include(":awake:tilemap")
include(":awake:ai")
include(":awake:ai:behavior")
include(":awake:blueprint")
include(":awake:scene:blueprint")
include(":awake:node-graph")
include(":awake:scene:world")
include(":awake:scene:worldstream")
include(":awake:scene:physics")
include(":awake:scene:audio")
include(":awake:scene:particles")
include(":awake:scene:net")
include(":awake:scene:worldtext")
include(":awake:scene:trail")
include(":awake:scene:navigation")
include(":awake:scene:ai")
include(":awake:scene:shader")
include(":awake:scene:scene2d")
include(":awake:scene:canvas")
include(":awake:scene:character")
include(":awake:scene:gltf")
include(":awake:scene:scene3d")
include(":awake:scene:document")
include(":awake:scene:binding")
include(":awake:scene:binding:processor")
include(":awake:scene:runtime")
include(":awake:scene:authoring")
include(":awake:editor:contract")
include(":awake:plugin-archive")
include(":awake:engine:render:contract")
include(":awake:engine:render:effects")
include(":awake:engine:render:testing")
include(":awake:engine:render:parity")
include(":awake:engine:render:passes")
include(":awake:engine:render:passes2d")
include(":awake:compose:runtime")
include(":awake:compose:ui")
include(":awake:compose:foundation")
include(":awake:compose:game")
include(":awake:compose:ui-testing")
include(":awake:compose:di")
include(":awake:compose:state")
include(":awake:compose:localization")
include(":awake:compose:assets")
include(":awake:ui:material3")
include(":awake:ui:shadcn")
include(":awake:ui:node-graph-canvas")
include(":awake:ui:font-atlas-generator")
include(":awake:ui:canvas")
include(":awake:tailwind")
include(":awake:tailwind-generator")
include(":awake:engine:platform")
include(":awake:engine:window")
include(":awake:engine:bootstrap")
include(":awake:engine:compose")
include(":awake:backend:vulkan")
include(":awake:backend:vulkan:bindings")
include(":awake:backend:vulkan:bindings:android-native")
include(":awake:backend:webgpu")
include(":awake:backend:vulkan:generator")
include(":awake:physics:api")
include(":awake:physics:ragdoll")
include(":awake:net:api")
include(":awake:net:server-ktor")
include(":awake:net:client-ktor")
include(":awake:net:client-browser")
include(":awake:net:prediction")
include(":awake:backend:jolt")
include(":awake:bom")
// Samples: illustrative, and free to be thrown away and rewritten.
include(":samples:ui-showcase")
include(":samples:engine-showcase")
include(":samples:engine-showcase:androidApp")
include(":samples:compose-showcase")
include(":samples:server")
include(":samples:net-demo")


pluginManagement {
    includeBuild("build-logic")
    // Modules only the Plugin Portal serves: each plugin's marker and implementation. The repositories
    // listed before the Portal exclude them, so an error from one of those (a Central 429) can't stop
    // the search for them before it reaches the Portal (#1592).
    val portalOnly = listOf(
        "org.gradle.toolchains.foojay-resolver-convention:org.gradle.toolchains.foojay-resolver-convention.gradle.plugin",
        "org.gradle.toolchains:foojay-resolver",
        "org.jetbrains.kotlinx.benchmark:org.jetbrains.kotlinx.benchmark.gradle.plugin",
        "org.jetbrains.kotlinx:kotlinx-benchmark-plugin",
    )
    fun RepositoryContentDescriptor.excludePortalOnly() =
        portalOnly.forEach { excludeModule(it.substringBefore(':'), it.substringAfter(':')) }
    repositories {
        // A repository that fails to answer, rather than answering 404, stops the search there: the
        // Plugin Portal listed first failed builds whose plugins Maven Central or Google serve (#1592).
        // So Google (AGP's markers are only there) and Central first, and the Portal last, for the
        // plugins only it serves, such as foojay's.
        // Google only for its own groups (AGP's markers, androidx, com.google): a Google error can then
        // fail no plugin it doesn't serve, as one listed before Central would (#1592).
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                excludePortalOnly()
            }
        }
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        // Google only for its own groups: a Google error can then fail no dependency it doesn't serve (#1592).
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // No Maven Local and no snapshot repository: Core's own build resolves nothing from them. Its
        // only snapshots are io.ygdrasil's, which awakekt.github.io below serves, and every repository listed
        // is one more that can answer a lookup wrongly (#1592).
        mavenCentral()
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        // The wgpu4k builds pinned in build.gradle.kts, byte for byte, served by the Maven fallback
        // workflow from the mirror-io.ygdrasil-* release. See "WebGPU release pin" in
        // docs/release-process.md.
        maven("https://awakekt.github.io/awake/") { content { includeGroup("io.ygdrasil") } }
    }
}
