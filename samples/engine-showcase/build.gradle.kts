/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library.kmp)
    id("com.awakekt.awake.plugin.application")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    jvmToolchain(17)
    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.awakekt.awake.showcase"
        compileSdk = (findProperty("android.compileSdk") as String).toInt()
        minSdk = (findProperty("android.minSdk") as String).toInt()
    }

    jvm("desktop")

    iosArm64 {
        binaries.framework {
            baseName = "EngineShowcase"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "EngineShowcase"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                val port =
                    if (mode == org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Mode.PRODUCTION) 8089 else 8088
                devServer = devServer?.copy(port = port)
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:asset:gltf"))
            implementation(project(":awake:asset:shader-pack"))
            implementation(project(":awake:asset:terrain"))
            implementation(project(":awake:core:animation"))
            implementation(project(":awake:core:geometry"))
            implementation(project(":awake:core:host"))
            implementation(project(":awake:core:image"))
            implementation(project(":awake:core:input"))
            implementation(project(":awake:core:math"))
            implementation(project(":awake:ecs"))
            implementation(project(":awake:engine:bootstrap"))
            implementation(project(":awake:engine:render:contract"))
            implementation(project(":awake:physics:api"))
            // The heightfield-terrain showcase runs a real Jolt world, so it needs a backend and
            // not just the contract -- this is the sample that proves physics is wired at all.
            implementation(project(":awake:backend:jolt"))
            implementation(project(":awake:scene:scene-core"))
            implementation(project(":awake:scene:physics"))
            implementation(project(":awake:scene:world"))
            implementation(project(":awake:ai:behavior"))
            implementation(project(":awake:scene:authoring"))
            // The nav-chase showcase bakes a walkability grid from its own heightmap.
            implementation(project(":awake:navigation"))
            // The streamed-nav showcase owns the scope its cell bakes and path searches run on.
            implementation(libs.kotlinx.coroutines.core)
            // The showcase switcher, and nothing else: this sample has one panel, not a shell.
            implementation(project(":awake:ui:shadcn"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":awake:engine:render:testing"))
        }
        val appMain = create("appMain") {
            dependsOn(commonMain.get())
        }
        appMain.dependencies {
            implementation(project(":awake:backend:vulkan"))
        }
        named("desktopMain") {
            dependsOn(appMain)
        }
        named("androidMain") {
            dependsOn(appMain)
        }
        named("iosMain") {
            dependsOn(appMain)
        }
        named("desktopTest") {
            dependencies {
                implementation(project(":awake:backend:vulkan"))
            }
        }
        named("wasmJsMain") {
            dependencies {
                implementation(project(":awake:backend:webgpu"))
                implementation(libs.kotlinx.browser)
            }
            resources.srcDir(project(":awake:backend:webgpu").file("src/wasmJsMain/resources"))
        }
    }
}

awake {
    xcframework("EngineShowcase") {
        includeMoltenVK = true
    }

    desktopApp {
        mainClass = "com.awakekt.awake.showcase.app.MainKt"
        description = "Run the Awake engine showcase; pass -Pawake.showcase=<id> for a focused demo."
    }

    test {
        useVulkanNatives = true
        useNagaShaders = true
        exclusiveGpu = true
        forkEvery = 1
    }

    testResources {
        roots(
            layout.projectDirectory.dir("src/commonMain/resources"),
            layout.projectDirectory.dir("src/appMain/resources"),
        )
    }
}
