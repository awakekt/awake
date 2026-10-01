/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.tasks.GenerateImageVectorsTask

plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
    id("com.awakekt.awake.plugin.icon-codegen")
}

val generateLucideImageVectors = tasks.register<GenerateImageVectorsTask>("generateLucideImageVectors") {
    group = "awake codegen"
    description = "Generate source-faithful Lucide ImageVector icons."
    sourceDirectory.set(layout.projectDirectory.dir("src/commonMain/svg/lucide"))
    outputDirectory.set(layout.buildDirectory.dir("generated/lucideImageVector"))
    generatorScript.set(rootProject.layout.projectDirectory.file("tools/icons/svg_to_ui_image_vector.py"))
    pythonExecutable.convention(
        providers.gradleProperty("awake.icons.python")
            .orElse(providers.environmentVariable("AWAKE_PYTHON"))
            .orElse("python3"),
    )
}

kotlin {
    android {
        namespace = "com.awakekt.awake.heroicons"
    }

    sourceSets {
        commonMain { kotlin.srcDir(generateLucideImageVectors) }
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math2d"))
            api(project(":awake:compose:ui"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
