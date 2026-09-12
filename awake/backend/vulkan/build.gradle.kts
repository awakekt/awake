/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.extension.*
import java.util.Base64

plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.backend-layering")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    android {
        // Not `...awake.vulkan`, which `:awake:backend:vulkan:bindings` already claims. AGP
        // rejects two libraries sharing an Android namespace, and an app depending on both --
        // which every Android consumer of this backend does -- fails to merge manifests with an
        // error naming the namespace and neither owner. Invisible inside a composite build and
        // fatal for a consumer resolving both from Maven, which is where it was found.
        namespace = "com.awakekt.awake.vulkan.backend"
    }

    // iosX64 (Intel simulator) dropped: Compose Multiplatform stopped publishing it
    // after 1.11.0-alpha01 (Apple Silicon only going forward)

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:engine:render:passes2d"))
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:host"))
            implementation(project(":awake:core:image"))
            implementation(project(":awake:core:input"))
            implementation(project(":awake:core:logging"))
            // Renderer/DrawCall/TextureLoader (moved in from awake-core) need Mat4/Camera
            // and Bitmap/readResourceBytes -- see docs/mvp-plan.md's Decision Log, D11, for
            // the awake-core split this module boundary comes from.
            implementation(project(":awake:core:math"))
            // Glyph metrics for the UI pass. `ui:text`, not `ui-core`: the backend needs
            // `UiFont` and nothing else from the UI stack, and depending on the engine being
            // retired for one type is what kept `ui-core` alive here.
            implementation(project(":awake:core:text"))
            // Module restructuring slice 1 (see docs/mvp-plan.md): Mesh/Material/Renderer's
            // expect declarations now implement the narrow backend-neutral interfaces this
            // module owns, so RenderSystem (awake-scene) can depend on just that module
            // instead of all of awake-backend-vulkan's concrete Vulkan bindings. `api`, not
            // `implementation`, since consumers reaching these types through awake-backend-vulkan
            // (e.g. VulkanApplication.kt) need them visible too.
            api(project(":awake:engine:render:contract"))
            // Scene/render-pipeline implementation detail. Backend consumers must depend on the
            // generic contract, not inherit authored scene vocabulary from this driver.
            implementation(project(":awake:engine:render:passes"))
            // Raw generated Vulkan API (see docs/tasks/2026-08-09-application-seam-and-module-
            // naming-plan.md, Part 3) -- gen/handles/models/enums/Vulkan.kt/VulkanSurface.kt.
            // `api`, not `implementation`: Renderer/GraphicsDevice/etc.'s own public signatures
            // (e.g. RenderPipeline constructor params) surface these raw types to consumers.
            api(project(":awake:backend:vulkan:bindings"))
            // Reusable-Application gap fix (see docs/mvp-plan.md's Decision Log):
            // VulkanGameApplication implements the Application interface (awake-engine) and
            // owns generic scene loading/TransformSystem/RenderSystem wiring (awake-scene) so
            // a new game doesn't have to hand-roll the same ~200 lines of GraphicsDevice/
            // SwapchainManager/RenderPipeline/Mesh/Material bootstrap awake-demo used to.
            implementation(libs.kotlinx.coroutines.core)
            // VulkanGameApplication now extends GameApplication (see
            // docs/reference/decision-log.md for the duplication this replaces). `api`,
            // not `implementation`: it's a supertype of VulkanGameApplication, so consumers
            // (sample-hello-cube, awake-demo) need it resolvable on their own classpath too.
            api(project(":awake:engine:platform"))
            api(project(":awake:asset:shaders"))
            implementation(project(":awake:asset:shader-compiler"))
            // HeadlessRenderSession, the shape `vulkanHeadlessUi` hands a windowless renderer
            // back in -- shared with the WebGPU backend so a drawing can be sent to either.
            api(project(":awake:engine:render:testing"))
            // PackShaderSets.Triangle, the scene pipeline the headless UI fixture builds.
            implementation(project(":awake:asset:shader-pack"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":awake:engine:compose"))
            // Backend-neutral offscreen frame capture and PNG diagnostics.
            implementation(project(":awake:engine:render:testing"))
            // RendererHeadlessShadowMapTest builds the real lit_shadow pipeline and uses the
            // same generated shader-pack artifacts as production; keep this test dependency for
            // the pack's uniform layouts and drift coverage.
            implementation(project(":awake:asset:shader-pack"))
            // Test-only, and backend-neutral in both directions: the shadow tests render the
            // showcase's own scene FILE rather than a copy of its numbers, so the scene drifting
            // fails the test instead of silently leaving it checking something else.
            implementation(project(":awake:scene:runtime"))
        }
        androidMain.dependencies {
            implementation(libs.leakcanary.android)
        }
    }
}

// This module's desktopTest exercises the real renderer, so it needs the same loader
// environment as bindings' own tests -- one definition for all three consumers.
val desktopVulkanEnv = VulkanDesktopEnv.environment()
val desktopNativeLibDir =
    project(":awake:backend:vulkan:bindings").layout.buildDirectory.dir("desktop-native-libs")

// Points java.library.path at bindings' desktop-native-libs AND builds the library first.
//
// The dependency is the load-bearing half. Setting the path alone is silently a no-op when
// nothing has produced the .dylib: `System.loadLibrary("awake-vulkan")` then throws
// UnsatisfiedLinkError and 18 of these tests fail for a reason that looks like a code defect
// and is not. Every fresh clone and every new git worktree starts in exactly that state.
tasks.named<Test>("desktopTest") {
    requireExclusiveGpu(this)
    dependsOn(":awake:backend:vulkan:bindings:buildDesktopNative")
    // RendererHeadlessContentTextureTest compiles its ASL probe to SPIR-V through the same
    // naga binding VulkanShaderResolver uses in production.
    useNagaShaderCompiler(this)
    jvmArgs("-Djava.library.path=${desktopNativeLibDir.get().asFile.absolutePath}")
    environment(desktopVulkanEnv)
    // `-DAWAKE_RECORD_SNAPSHOTS=true` on the Gradle CLI only sets the property on Gradle's own
    // JVM -- desktopTest runs in a forked test JVM, so forward it explicitly. Same fix
    // awake.ui-preview-report-convention applies for the ui-preview modules.
    System.getProperty("AWAKE_RECORD_SNAPSHOTS")
        ?.let { systemProperty("AWAKE_RECORD_SNAPSHOTS", it) }
    // Headless Vulkan tests exercise native loader / instance lifecycle paths that have
    // proven sensitive to process-shared state when multiple renderer baseline classes run
    // in one worker. Fork per test class keeps those baselines isolated and reproducible.
    forkEvery = 1
    finalizedBy("pixelBaselineReport")
}

// Gradle's own HTML test report only shows escaped stdout/stacktrace text -- no <img>
// rendering, no attachment support -- so RendererHeadlessPixelBaselineTest's actual.png/
// baseline.png dumps (see that test's failure branch) are otherwise just files a developer
// has to know to go dig up. This task turns build/test-failures/*/{actual,baseline}.png
// into one self-contained HTML page (images inlined as base64, so it's a single file you
// can open or send without broken relative links) -- a lightweight stand-in for a real
// test-reporting tool (Allure, etc) until there are enough visual tests to justify one.
tasks.register("pixelBaselineReport") {
    group = "verification"
    description =
        "Generate an HTML gallery of actual-vs-baseline PNGs for any failed pixel-baseline test."
    val failuresDir = layout.buildDirectory.dir("test-failures")
    val reportFile = layout.buildDirectory.file("reports/pixel-baseline/index.html")
    // No inputs.dir/outputs.file declared -- this always reruns (a plain dev convenience
    // task, not something that needs Gradle's up-to-date caching), and failuresDir may
    // legitimately not exist yet (no failures ever recorded).
    doLast {
        val root = failuresDir.get().asFile
        val testDirs =
            root.listFiles { file -> file.isDirectory }?.sortedBy { it.name } ?: emptyList()

        fun imgTag(file: File): String {
            if (!file.exists()) return "<p><em>missing: ${file.name}</em></p>"
            val base64 = Base64.getEncoder().encodeToString(file.readBytes())
            return """<img src="data:image/png;base64,$base64" style="image-rendering:pixelated;width:256px;height:256px;border:1px solid #444" />"""
        }

        val sections = testDirs.joinToString("\n") { testDir ->
            """
            <section style="margin-bottom:2rem">
                <h2>${testDir.name}</h2>
                <div style="display:flex;gap:1rem">
                    <div><h3>Baseline (expected)</h3>${imgTag(File(testDir, "baseline.png"))}</div>
                    <div><h3>Actual (rendered)</h3>${imgTag(File(testDir, "actual.png"))}</div>
                </div>
            </section>
            """.trimIndent()
        }

        val body = if (testDirs.isEmpty()) {
            "<p>No pixel-baseline test failures in the last run.</p>"
        } else {
            sections
        }

        val html = """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><title>Pixel baseline report</title></head>
            <body style="font-family:sans-serif;background:#1e1e1e;color:#eee;padding:2rem">
                <h1>Pixel baseline report</h1>
                $body
            </body></html>
        """.trimIndent()

        val out = reportFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(html)
        println("Pixel baseline report: file://${out.absolutePath}")
    }
}

mavenPublishing {
    pom {
        name.set("Awake Vulkan Backend")
        description.set("The Vulkan renderer: swapchain, pipelines, descriptors and draw recording for desktop, Android and iOS")
    }
}
