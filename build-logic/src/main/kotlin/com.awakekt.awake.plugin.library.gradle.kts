/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(17)

    android {
        compileSdk = (findProperty("android.compileSdk") as String).toInt()
        minSdk = (findProperty("android.minSdk") as String).toInt()
        withHostTest {}
    }

    jvm("desktop")

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = project.path.removePrefix(":").replace(":", "-")
        }
    }

    // Opt-out for modules whose API cannot exist in a browser (backend:vulkan has no wasm
    // actuals -- browsers speak WebGPU, not Vulkan). Set awake.target.wasmJs=false in the
    // module's gradle.properties.
    if (findProperty("awake.target.wasmJs") != "false") {
        @Suppress("OPT_IN_USAGE")
        wasmJs {
            browser()
        }
    }
}

// A module whose native code finds its classes by name keeps those names in consumer-rules.pro, and
// every app that shrinks its release gets them: Android apps from the library, and desktop apps from
// the jar, under META-INF/proguard, where R8 looks for a library's rules. verifyKeepRules checks the file.
val keepRulesFile = layout.projectDirectory.file("consumer-rules.pro")
if (keepRulesFile.asFile.isFile) {
    kotlin {
        android {
            optimization {
                consumerKeepRules.publish = true
                consumerKeepRules.file(keepRulesFile)
            }
        }
    }
    tasks.withType<ProcessResources>().matching { it.name == "desktopProcessResources" }.configureEach {
        from(keepRulesFile) {
            into("META-INF/proguard")
            rename { "${project.path.removePrefix(":").replace(':', '-')}.pro" }
        }
    }
}
