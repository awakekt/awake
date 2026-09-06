/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("awake.test-resources-convention")
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
            implementation(project(":awake:scene"))
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

awakeTestResources {
    roots.from(layout.projectDirectory.dir("src/commonMain/resources"))
    roots.from(layout.projectDirectory.dir("src/appMain/resources"))
}

val desktopNativeLibDir =
    project(":awake:backend:vulkan:bindings").layout.buildDirectory.dir("desktop-native-libs")
val desktopVulkanEnv = VulkanDesktopEnv.environment()

tasks.named<Test>("desktopTest") {
    // Serialised against the other modules that open a Vulkan device; see
    // requireExclusiveGpu. forkEvery below isolates classes within this module only.
    requireExclusiveGpu(this)
    dependsOn(":awake:backend:vulkan:bindings:buildDesktopNative")
    useNagaShaderCompiler(this)
    jvmArgs("-Djava.library.path=${desktopNativeLibDir.get().asFile.absolutePath}")
    environment(desktopVulkanEnv)
    forkEvery = 1
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the Awake engine showcase; pass -Pawake.showcase=<id> for a focused demo."
    dependsOn("desktopMainClasses")
    wireVulkanDesktopNatives(project(":awake:backend:vulkan:bindings"))
    // Shipped shaders are WGSL, so every pipeline this sample builds goes through naga.
    useNagaShaderCompiler(this)
    mainClass.set("com.awakekt.awake.showcase.app.MainKt")
    classpath = files(
        layout.buildDirectory.dir("classes/kotlin/desktop/main"),
        layout.buildDirectory.dir("processedResources/desktop/main"),
        kotlin.jvm("desktop").compilations.getByName("main").runtimeDependencyFiles,
    )
    environment(desktopVulkanEnv)
    val jvmArgsList = mutableListOf<String>()
    if (HostOs.isMac) jvmArgsList += "-XstartOnFirstThread"
    providers.gradleProperty("awake.showcase").orNull?.let { showcaseId ->
        jvmArgsList += "-Dawake.showcase=$showcaseId"
    }
    jvmArgs(jvmArgsList)
}
