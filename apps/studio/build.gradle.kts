/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("awake.publish-convention")
    id("awake.test-resources-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    id("awake.ui-ownership-convention")
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
                    if (mode == org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Mode.PRODUCTION) 8087 else 8086
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
            implementation(project(":awake:core:host"))
            implementation(project(":awake:core:image"))
            implementation(project(":awake:core:input"))
            implementation(project(":awake:engine:bootstrap"))
            implementation(project(":awake:engine:render:contract"))
            implementation(project(":awake:core:math"))
            // PackShaderSets and the uniform layouts beside them. A real dependency now, not a
            // static file path: the shaders are Kotlin, so nothing reads a .wgsl off disk.
            implementation(project(":awake:asset:shader-pack"))
            implementation(project(":awake:asset:gltf"))
            implementation(project(":awake:ecs"))
            implementation(project(":awake:scene"))
            implementation(project(":awake:scene:authoring"))
            implementation(project(":awake:editor"))
            implementation(project(":awake:editor:scene"))
            implementation(project(":awake:editor:physics"))
            implementation(project(":awake:editor:ai"))
            implementation(project(":awake:editor:render"))
            implementation(project(":awake:ui:shadcn"))
            implementation(project(":awake:ui:builder"))
            // Outline glyphs directly, not through ShadcnIcons: that registry is pinned to the
            // 20px "mini" solid tier, and studio's chrome is outline at 24.
            implementation(project(":awake:heroicons"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
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
                implementation(project(":awake:compose:ui-testing"))
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

val desktopNativeLibDir =
    project(":awake:backend:vulkan:bindings").layout.buildDirectory.dir("desktop-native-libs")
val desktopVulkanEnv = VulkanDesktopEnv.environment()

// The desktop test JVM needs the same native wiring the `run` task above sets up: these tests
// construct a real Vulkan device to render a frame and look at it, which is the only way to catch
// something the unit tests cannot see (a mirrored viewport, an unlit surface).
tasks.named<Test>("desktopTest") {
    requireExclusiveGpu(this)
    dependsOn(":awake:backend:vulkan:bindings:buildDesktopNative")
    useNagaShaderCompiler(this)
    jvmArgs("-Djava.library.path=${desktopNativeLibDir.get().asFile.absolutePath}")
    environment(desktopVulkanEnv)
    // Each device session creates a Vulkan instance, and a second vkCreateInstance in the same
    // JVM fails -- same reason :awake:backend:vulkan forks per class.
    forkEvery = 1
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the Awake studio sample."
    dependsOn("desktopMainClasses")
    dependsOn(":awake:backend:vulkan:bindings:buildDesktopNative")
    // Shipped shaders are WGSL, so every pipeline this sample builds goes through naga.
    useNagaShaderCompiler(this)
    mainClass.set("io.github.awakelab.awake.studio.app.MainKt")
    classpath = files(
        layout.buildDirectory.dir("classes/kotlin/desktop/main"),
        layout.buildDirectory.dir("processedResources/desktop/main"),
        kotlin.jvm("desktop").compilations.getByName("main").runtimeDependencyFiles
    )
    environment(desktopVulkanEnv)
    val jvmArgsList =
        mutableListOf("-Djava.library.path=${desktopNativeLibDir.get().asFile.absolutePath}")
    if (HostOs.isMac) {
        jvmArgsList += "-XstartOnFirstThread"
    }
    jvmArgs(jvmArgsList)
}

// appMain holds compiled Vulkan SPIR-V; commonMain holds hand-authored assets (models, scene
// documents) every target loads. Both need serving to iosSimulatorArm64Test/wasmJsBrowserTest
// the same way the real app gets them -- Kotlin's own resource merging reaches the compiled
// app bundle, but not karma's test server or a Kotlin/Native test binary's working directory.
awakeTestResources {
    roots.from(layout.projectDirectory.dir("src/appMain/resources"))
    roots.from(layout.projectDirectory.dir("src/commonMain/resources"))
}
