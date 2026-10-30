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
            api(project(":awake:core:io"))
            // The rule a tag follows (Tags.isValid), so the manifest's tags list checks against it.
            implementation(project(":awake:ecs"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            // Encrypted bundles: AES-256-GCM and HKDF-SHA256 through each target's own provider
            // (JDK on desktop and Android, CryptoKit on iOS, WebCrypto in the browser).
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
            // Lifts cryptography-kotlin's kotlinx-io 0.9.0, whose wasm helper breaks browser bundles.
            implementation(libs.kotlinx.io.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // IndexedDbContentCache's IndexedDB access, and the coroutine `await` that turns its
        // Promises into suspend calls. wasmJs-only -- see the note in awake:core:host's history.
        named("wasmJsMain") {
            dependencies {
                implementation(libs.kotlinx.browser)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Project")
        description.set("Portable Awake project manifest format and compatibility validation")
    }
}
