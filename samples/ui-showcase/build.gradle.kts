/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library.kmp)
    alias(libs.plugins.kotlin.serialization)
    id("com.awakekt.awake.plugin.application")
    id("com.awakekt.awake.plugin.shader-pipeline")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
    id("com.awakekt.awake.plugin.ui-authored-units")
    id("com.awakekt.awake.plugin.ui-preview-report")
    id("com.awakekt.awake.plugin.ui-ownership")
}

// verifyUiOwnership reads the whole src/ tree for its .kt source-pattern checks;
// syncAwakeShaders writes generated shader files under src/ too. Neither task actually depends
// on the other's output, but Gradle's parallel scheduler still needs an explicit order to avoid
// a same-directory read/write race.
tasks.named("verifyUiOwnership") {
    mustRunAfter("syncAwakeShaders")
}

kotlin {
    jvmToolchain(17)
    applyDefaultHierarchyTemplate()

    android {
        namespace = "com.awakekt.awake.sample.uishowcase"
        compileSdk = (findProperty("android.compileSdk") as String).toInt()
        minSdk = (findProperty("android.minSdk") as String).toInt()
        withHostTest {}
    }

    jvm("desktop")

    iosArm64 {
        binaries.framework {
            baseName = "UiShowcase"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "UiShowcase"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // Fixed dev-server ports so dev/prod each get their own, and don't collide with
            // the other samples' wasmJs dev servers (webpack defaults every sample to 8080
            // otherwise). Keep in sync with the "wasmjs-ui-showcase"/"wasmjs-ui-showcase-prod"
            // entries in .claude/launch.json and the port table in
            // docs/reference/developer-docs.md.
            commonWebpackConfig {
                val port =
                    if (mode == org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Mode.PRODUCTION) 8083 else 8082
                devServer = devServer?.copy(port = port)
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:input"))
            implementation(project(":awake:engine:bootstrap"))
            implementation(project(":awake:scene:authoring"))
            implementation(project(":awake:ui:shadcn"))
            implementation(project(":awake:tailwind"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":awake:compose:ui-testing"))
            implementation(project(":awake:compose:foundation"))
            implementation(project(":awake:engine:render:testing"))
            implementation(libs.kotlinx.coroutines.test)
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

        named("androidMain") {
            dependsOn(appMain)
            dependencies {
                api(project(":awake:backend:vulkan"))
            }
        }

        named("iosMain") {
            dependsOn(appMain)
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



val desktopNativeLibDir =
    project(":awake:backend:vulkan:bindings").layout.buildDirectory.dir("desktop-native-libs")
val moltenVkIcdPath =
    fileTree("/opt/homebrew/Cellar/molten-vk") { include("*/etc/vulkan/icd.d/MoltenVK_icd.json") }
        .plus(fileTree("/usr/local/Cellar/molten-vk") { include("*/etc/vulkan/icd.d/MoltenVK_icd.json") })
        .files.firstOrNull()?.absolutePath
val dyldFallbackLibraryPath = "/opt/homebrew/opt/vulkan-loader/lib:/opt/homebrew/lib:/usr/local/lib"

// Vulkan-backed previews use the same headless loader/native-library setup as the backend's
// pixel tests. Keep software previews portable; only the opt-in GPU preview needs this wiring.
awake {
    xcframework("UiShowcase") {
        includeMoltenVK = true
    }

    desktopApp {
        mainClass = "com.awakekt.awake.sample.uishowcase.app.MainKt"
        description = "Run the Awake UI showcase sample."
    }

    test {
        useVulkanNatives = true
        useNagaShaders = true
        exclusiveGpu = true
        forkEvery = 1
    }
}

tasks.withType<Test>().configureEach {
    val record =
        System.getenv("AWAKE_RECORD_SNAPSHOTS") ?: System.getProperty("AWAKE_RECORD_SNAPSHOTS")
    if (record != null) {
        systemProperty("AWAKE_RECORD_SNAPSHOTS", record)
    }
}

tasks.register("validateUiShowcasePlatforms") {
    group = "verification"
    description = "Build and test the UI showcase sample across desktop, iOS simulator, and web."
    dependsOn(
        "desktopTest",
        "desktopJar",
        "iosSimulatorArm64Test",
        "wasmJsBrowserDistribution"
    )
}
