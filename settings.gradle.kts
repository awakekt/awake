/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

rootProject.name = "Awake"

include(":awake:core:math")
include(":awake:core:math2d")
include(":awake:core:graphics2d")
include(":awake:core:color")
include(":awake:core:input")
include(":awake:core:logging")
include(":awake:core:image")
include(":awake:core:host")
include(":awake:core:geometry")
include(":awake:core:animation")
include(":awake:core:text")
include(":awake:asset:gltf")
include(":awake:asset:terrain")
include(":awake:asset:mesh-optimizer")
include(":awake:asset:shaders")
include(":awake:asset:shader-pack")
include(":awake:asset:shader-dsl")
include(":awake:asset:shader-compiler")
include(":awake:ecs")
include(":awake:ecs:benchmark")
include(":awake:ui:benchmark")
include(":awake:scene:rendering:benchmark")
include(":awake:scene")
include(":awake:scene:scene-core")
include(":awake:scene:controls")
include(":awake:scene:navigation")
include(":awake:scene:physics")
include(":awake:scene:rendering")
include(":awake:scene:runtime")
include(":awake:scene:authoring")
include(":awake:engine:render:contract")
include(":awake:engine:render:testing")
include(":awake:engine:render:passes")
include(":awake:engine:render:passes2d")
include(":awake:compose:runtime")
include(":awake:compose:ui")
include(":awake:compose:foundation")
include(":awake:compose:ui-testing")
include(":awake:ui:material3")
include(":awake:ui:shadcn")
include(":awake:ui:font-atlas-generator")
include(":awake:tailwind")
include(":awake:tailwind-generator")
include(":awake:heroicons")
include(":awake:engine:platform")
include(":awake:engine:bootstrap")
include(":awake:engine:compose")
include(":awake:editor")
include(":awake:editor:scene")
include(":awake:backend:vulkan")
include(":awake:backend:vulkan:bindings")
include(":awake:backend:vulkan:bindings:android-native")
include(":awake:backend:webgpu")
include(":awake:backend:vulkan:generator")
include(":awake:physics:api")
include(":awake:backend:jolt")
include(":samples:ui-showcase")
include(":samples:engine-showcase")
include(":samples:studio")
include(":samples:server")


pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
        // wgpu4k (Phase 2.5 spike, see docs/mvp-plan.md) has no stable release yet (last tag
        // v0.1.1, June 2025) -- only snapshots via Sonatype's current Central Portal
        // snapshot repo (not the legacy oss.sonatype.org ones above).
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}
