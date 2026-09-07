/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.awakekt.awake.plugin.application")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    jvmToolchain(17)
    applyDefaultHierarchyTemplate()

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // Fixed dev-server ports, distinct from the other samples -- see the port table in
            // docs/reference/developer-docs.md.
            commonWebpackConfig {
                val port =
                    if (mode == org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Mode.PRODUCTION) 8085 else 8084
                devServer = devServer?.copy(port = port)
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:input"))
            implementation(project(":awake:core:di"))
            implementation(project(":awake:core:state"))
            implementation(project(":awake:compose:foundation"))
            implementation(project(":awake:compose:di"))
            implementation(project(":awake:compose:state"))
            implementation(project(":awake:engine:bootstrap"))
            implementation(project(":awake:scene:authoring"))
            implementation(project(":awake:asset:shader-pack"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        val appMain = create("appMain") {
            dependsOn(commonMain.get())
        }
        appMain.dependencies {
            implementation(project(":awake:core:math"))
            implementation(project(":awake:backend:vulkan"))
        }

        named("desktopMain") {
            dependsOn(appMain)
        }

        named("wasmJsMain") {
            dependencies {
                implementation(project(":awake:backend:webgpu"))
            }
            resources.srcDir(project(":awake:backend:webgpu").file("src/wasmJsMain/resources"))
        }
    }
}

awake {
    desktopApp {
        mainClass = "com.awakekt.awake.sample.composeshowcase.app.MainKt"
        description = "Run the Compose Foundation layout showcase."
    }
}
