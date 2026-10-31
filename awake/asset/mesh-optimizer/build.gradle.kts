/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

application {
    mainClass.set("com.awakekt.awake.asset.meshoptimizer.MainKt")
}

dependencies {
    implementation(project(":awake:core:geometry"))
    implementation(project(":awake:asset:gltf"))
    implementation(project(":awake:asset:mesh-lod"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

// Mirrors :awake:tailwind-generator's own `generateTailwindScale` task -- `application`'s
// own `run` task doesn't set a stable workingDir, and CLI args (input/output/ratio) are
// naturally passed the same way. Run via
// `./gradlew :awake:asset:mesh-optimizer:decimate --args="in.gltf out.gltf 0.5"`.
tasks.register<JavaExec>("decimate") {
    group = "awake tools"
    description = "Simplify a .gltf mesh's triangle count -- args: <input.gltf> <output.gltf> <targetRatio>"
    dependsOn("classes")
    workingDir = projectDir
    mainClass.set("com.awakekt.awake.asset.meshoptimizer.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split(" "))
    }
}

// Cook levels of detail into a project's glTF models with its awake.lod.json settings. Run via
// `./gradlew :awake:asset:mesh-optimizer:cookLods --args="<project> <asset> [<asset> ...] --output <dir>"`.
tasks.register<JavaExec>("cookLods") {
    group = "awake tools"
    description = "Generate MSFT_lod levels for glTF models -- args: <project> <asset> [<asset> ...] --output <dir>"
    dependsOn("classes")
    workingDir = rootProject.projectDir
    mainClass.set("com.awakekt.awake.asset.meshoptimizer.LodCookMain")
    classpath = sourceSets.main.get().runtimeClasspath
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split(" "))
    }
}
