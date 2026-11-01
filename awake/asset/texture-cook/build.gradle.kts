/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

application {
    mainClass.set("com.awakekt.awake.asset.texturecook.TextureCookMain")
}

// libktx's natives for every desktop a project is cooked on; LWJGL loads the one for the host.
val lwjglNatives = listOf("natives-linux", "natives-macos-arm64", "natives-windows")

dependencies {
    implementation(project(":awake:asset:texture-ktx"))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.ktx)
    lwjglNatives.forEach { natives ->
        runtimeOnly(variantOf(libs.lwjgl.core) { classifier(natives) })
        runtimeOnly(variantOf(libs.lwjgl.ktx) { classifier(natives) })
    }
    testImplementation(kotlin("test"))
}

// Cook a project's textures to KTX2 with its awake.textures.json settings. Run via
// `./gradlew :awake:asset:texture-cook:cookTextures --args="<project> <texture> [<texture> ...] --output <dir>"`.
tasks.register<JavaExec>("cookTextures") {
    group = "awake tools"
    description = "Compress textures to KTX2 (Basis Universal, ASTC, ETC2) -- args: <project> <texture> [<texture> ...] --output <dir>"
    dependsOn("classes")
    workingDir = rootProject.projectDir
    mainClass.set("com.awakekt.awake.asset.texturecook.TextureCookMain")
    classpath = sourceSets.main.get().runtimeClasspath
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split(" "))
    }
}
