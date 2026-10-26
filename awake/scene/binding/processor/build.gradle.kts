/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// The KSP processor behind @SceneBinding: a build tool that scene modules run through the
// com.awakekt.awake.plugin.scene-components plugin, never a runtime dependency.
plugins {
    alias(libs.plugins.kotlin.jvm)
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(kotlin("test"))
    testImplementation(libs.kctfork.ksp)
    // The real annotations and binding contract, so the test sources compile as a scene module's do.
    testImplementation(project(":awake:scene:binding"))
}
