/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// ASL, Awake's procedural shader authoring: Kotlin definitions that emit WGSL text, fed into
// the existing naga pipeline (validateAwakeShaders/syncAwakeShaders) unchanged. Pure text
// generation -- no render contract, no backend, no UI dependency. See
// docs/tasks/2026-08-23-asl-procedural-shader-plan.md for scope and sequencing.
plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.awakekt.awake.asset.shaderdsl"
    }

    sourceSets {
        commonMain.dependencies {
            // GpuDataShape -- ASL reuses the engine's one value-shape enum in its public
            // DSL signatures rather than declaring a parallel type.
            api(project(":awake:core:geometry"))
            // UniformLayout/UniformField -- fieldsFrom() declares a WGSL struct from the
            // renderer's own packing description, the single source both sides read.
            api(project(":awake:engine:render:contract"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Shader DSL")
        description.set("ASL: Kotlin shader definitions emitting WGSL for Awake's shader pipeline")
    }
}

// Headless terminal preview of a shader's fragment stage -- CPU-evaluates the ASL expression
// tree per pixel and prints ANSI true-color half-blocks. No window, no image file. Run via
// `./gradlew :awake:asset:shader-dsl:previewShader` (optional -Pargs="<width> <height> --wgsl").
tasks.register<JavaExec>("previewShader") {
    group = "generation"
    description = "Render the sample checker shader to the terminal via CPU evaluation"
    dependsOn("desktopMainClasses")
    mainClass.set("com.awakekt.awake.asset.shaderdsl.preview.PreviewKt")
    classpath = files(
        kotlin.targets.getByName("desktop").compilations.getByName("main").output.allOutputs,
        configurations.getByName("desktopRuntimeClasspath"),
    )
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split(" "))
    }
}
