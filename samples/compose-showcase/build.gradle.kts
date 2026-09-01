/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
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
            implementation(project(":awake:engine:bootstrap"))
            implementation(project(":awake:scene:authoring"))
            implementation(project(":awake:compose:foundation"))
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

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the Compose Foundation layout showcase."
    dependsOn("desktopMainClasses")
    wireVulkanDesktopNatives(project(":awake:backend:vulkan:bindings"))
    useNagaShaderCompiler(this)
    mainClass.set("io.github.awakelab.awake.sample.composeshowcase.app.MainKt")
    classpath = files(
        layout.buildDirectory.dir("classes/kotlin/desktop/main"),
        layout.buildDirectory.dir("processedResources/desktop/main"),
        kotlin.jvm("desktop").compilations.getByName("main").runtimeDependencyFiles,
    )
    environment(VulkanDesktopEnv.environment())
    val jvmArgsList = mutableListOf<String>()
    if (HostOs.isMac) jvmArgsList += "-XstartOnFirstThread"
    jvmArgs(jvmArgsList)
}
