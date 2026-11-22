/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// Kotlin Multiplatform library without a separate Android AAR target.
//
// Pure-Kotlin modules with no androidMain sources, Android resources, or Android device tests
// publish a single JVM (`desktop`) jar that Android apps and Android KMP libraries resolve
// directly through Gradle's JVM environment compatibility rules. Avoiding redundant `-android`
// publications trims 20 files per module from every Maven Central release bundle and removes
// unnecessary AGP subproject configuration. Modules that carry Android-specific code, keep rules,
// or Android device tests apply `com.awakekt.awake.plugin.library` instead.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.awakekt.awake.plugin.browser-tests")
}

kotlin {
    jvmToolchain(17)

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

    // Every allocation gate reads the calling thread's bytes through one helper, as the median of
    // several windows (#1592); verifyAllocationGates holds that no other test reads the counter.
    sourceSets.named("desktopTest") {
        kotlin.srcDir(rootProject.layout.projectDirectory.dir("test-support/allocation"))
    }
}

val keepRulesFile = layout.projectDirectory.file("consumer-rules.pro")
if (keepRulesFile.asFile.isFile) {
    tasks.withType<ProcessResources>().matching { it.name == "desktopProcessResources" }.configureEach {
        from(keepRulesFile) {
            into("META-INF/proguard")
            rename { "${project.path.removePrefix(":").replace(':', '-')}.pro" }
        }
    }
}
