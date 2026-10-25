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

kotlin {
    explicitApi()

    android {
        namespace = "com.awakekt.awake.pluginarchive"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.cryptography.core)
            // Registers each target's platform provider: JDK (desktop, Android), Apple, WebCrypto.
            implementation(libs.cryptography.provider.optimal)
        }
        wasmJsMain.dependencies {
            // Promise.await and typed arrays for the browser's asynchronous DecompressionStream.
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.browser)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Plugin Archive")
        description.set("How an Awake plugin is described, packaged and trusted: manifests, .awakeplugin archives and signed key and revocation lists")
    }
}
