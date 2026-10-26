/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    kotlin("multiplatform") version "@KOTLIN_VERSION@"
    id("com.android.kotlin.multiplatform.library") version "@AGP_VERSION@"
    kotlin("plugin.serialization") version "@KOTLIN_VERSION@"
    id("com.awakekt.awake.plugin.scene-components") version "@AWAKE_VERSION@"
}

// A consumer's own version must not select the processor's version.
version = "7.8.9-consumer"

awakeSceneComponents {
    registry.set("example.beacon.BeaconSceneComponents")
}

kotlin {
    jvmToolchain(17)
    jvm("desktop")
    android {
        namespace = "example.beacon"
        compileSdk = 35
        minSdk = 33
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }
    // Local publication on another host omits Apple artifacts. The release Consumer runs on macOS.
    if (System.getProperty("os.name").startsWith("Mac")) {
        iosArm64()
        iosSimulatorArm64()
    }
    sourceSets {
        commonMain.dependencies { implementation("com.awakekt.awake.scene:binding:@AWAKE_VERSION@") }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

tasks.register("checkProcessorCoordinate") {
    doLast {
        val processor = configurations.getByName("kspCommonMainMetadata").dependencies.single()
        check(processor is ExternalModuleDependency)
        check("${processor.group}:${processor.name}:${processor.version}" == "com.awakekt.awake.scene.binding:processor:@AWAKE_VERSION@")
    }
}
