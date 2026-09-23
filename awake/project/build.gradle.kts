/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("com.awakekt.awake.plugin.library")
    alias(libs.plugins.kotlin.serialization)
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

val generateAwakeEngineBuildInfo = tasks.register("generateAwakeEngineBuildInfo") {
    val outputDirectory = layout.buildDirectory.dir("generated/awake/buildInfo/commonMain")
    outputs.dir(outputDirectory)
    doLast {
        val outputFile = outputDirectory.get().file(
            "com/awakekt/awake/project/AwakeEngineBuildInfo.generated.kt",
        ).asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            |package com.awakekt.awake.project
            |
            |internal const val AWAKE_ENGINE_VERSION: String = "${project.version}"
            |""".trimMargin() + "\n",
        )
    }
}

kotlin {
    sourceSets.commonMain {
        kotlin.srcDir(generateAwakeEngineBuildInfo)
    }

    android {
        namespace = "com.awakekt.awake.project"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Project")
        description.set("Portable Awake project manifest format and compatibility validation")
    }
}
