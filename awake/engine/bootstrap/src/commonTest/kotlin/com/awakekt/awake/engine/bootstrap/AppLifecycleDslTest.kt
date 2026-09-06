/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.bootstrap

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.appDefinition
import com.awakekt.awake.engine.bootstrap.dsl.appModule
import com.awakekt.awake.engine.bootstrap.dsl.appSpec
import com.awakekt.awake.engine.bootstrap.dsl.createApp
import com.awakekt.awake.engine.bootstrap.dsl.module
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.engine.platform.lifecycle.AppInstaller
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.DrawCall
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SceneLight
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppLifecycleDslTest {

    @Test
    fun gameDslRoutesLifecycleCallbacks() = runTest {
        var readyCalls = 0
        var renderDelta = 0f
        var renderWidth = 0f
        var renderHeight = 0f
        var resized = ""
        var paused = false
        var resumed = false
        var disposed = false

        val game = app {
            ready { readyCalls++ }
            render { frame ->
                renderDelta = frame.delta
                renderWidth = frame.viewportWidth
                renderHeight = frame.viewportHeight
            }
            resize { width, height ->
                resized = "$width x $height"
            }
            pause { paused = true }
            resume { resumed = true }
            dispose { disposed = true }
        }

        game.ready(FakeRenderer)
        game.update(0.016f, 1280f, 720f)
        game.resize(640f, 360f)
        game.pause()
        game.resume()
        game.dispose()

        assertEquals(1, readyCalls)
        assertEquals(0.016f, renderDelta)
        assertEquals(1280f, renderWidth)
        assertEquals(720f, renderHeight)
        assertEquals("640.0 x 360.0", resized)
        assertTrue(paused)
        assertTrue(resumed)
        assertTrue(disposed)
    }

    @Test
    fun gameDslCapturesWindowConfiguration() {
        val game = app {
            window {
                title = "Hello Cube"
                size(1600, 900)
                backend.vulkan()
            }
        }

        assertEquals("Hello Cube", game.windowConfig.title)
        assertEquals(1600, game.windowConfig.width)
        assertEquals(900, game.windowConfig.height)
        assertEquals(AppWindowBackend.VULKAN, game.windowConfig.backend)
    }

    @Test
    fun gameSpecCanBeBuiltAndCreatedSeparately() = runTest {
        var readyCalls = 0
        val spec = appSpec {
            window {
                title = "Spec First"
                size(960, 540)
                backend.webGpu()
            }
            ready { readyCalls += 1 }
            service(String::class, "runtime-proof")
        }

        val game = spec.createLifecycle()
        game.ready(FakeRenderer)

        assertEquals("Spec First", spec.windowConfig.title)
        assertEquals(960, game.windowConfig.width)
        assertEquals(540, game.windowConfig.height)
        assertEquals(AppWindowBackend.WEBGPU, game.windowConfig.backend)
        assertEquals("runtime-proof", game.requireService(String::class))
        assertEquals(1, readyCalls)
    }

    @Test
    fun gameDslCanInstallFeatureServices() {
        val game = app {
            install(
                object : AppInstaller {
                    override fun install(into: AppSpecBuilder) {
                        into.service(String::class, "debug")
                    }
                },
            )
        }

        assertEquals("debug", game.requireService(String::class))
    }

    @Test
    fun gameDslCanInstallReusableGameModule() = runTest {
        val events = mutableListOf<String>()
        val feature = appModule {
            service(String::class, "module-service")
            ready { events += "module-ready" }
            render { _ -> events += "module-render" }
            dispose { events += "module-dispose" }
        }

        val game = app {
            ready { events += "root-ready" }
            module(feature)
        }

        game.ready(FakeRenderer)
        game.update(0.016f, 320f, 240f)
        game.dispose()

        assertEquals("module-service", game.requireService(String::class))
        assertEquals(
            listOf("root-ready", "module-ready", "module-render", "module-dispose"),
            events,
        )
    }

    @Test
    fun gameModuleCanCreateGameShellDirectly() = runTest {
        val events = mutableListOf<String>()
        val feature = appModule {
            service(String::class, "feature-service")
            ready { events += "module-ready" }
        }

        val game = feature.createApp {
            title = "Module Shell"
            size(1024, 576)
            backend.webGpu()
        }

        game.ready(FakeRenderer)

        assertEquals("Module Shell", game.windowConfig.title)
        assertEquals(1024, game.windowConfig.width)
        assertEquals(576, game.windowConfig.height)
        assertEquals(AppWindowBackend.WEBGPU, game.windowConfig.backend)
        assertEquals("feature-service", game.requireService(String::class))
        assertEquals(listOf("module-ready"), events)
    }

    @Test
    fun gameDefinitionOwnsStateWindowAndModuleFactory() = runTest {
        val definition = appDefinition(createState = { mutableListOf("state") }) {
            window {
                title = "Definition Shell"
                size(1280, 720)
                backend.vulkan()
            }
            module { state ->
                appModule {
                    service(List::class, state)
                    ready { state += "ready" }
                }
            }
        }

        val state = definition.createState()
        val spec = definition.createAppSpec(state)
        val game = spec.createLifecycle()
        game.ready(FakeRenderer)

        assertEquals("Definition Shell", spec.windowConfig.title)
        assertEquals(1280, game.windowConfig.width)
        assertEquals(720, game.windowConfig.height)
        assertEquals(AppWindowBackend.VULKAN, game.windowConfig.backend)
        assertEquals(listOf("state", "ready"), state)
        assertEquals(state, game.requireService(List::class))
    }

    @Test
    fun gameDslComposesInstallerCallbacksInOrder() = runTest {
        val events = mutableListOf<String>()
        val game = app {
            ready { events += "root-ready" }
            render { _ -> events += "root-render" }
            dispose { events += "root-dispose" }
            install(
                object : AppInstaller {
                    override fun install(into: AppSpecBuilder) {
                        into.ready { events += "feature-ready" }
                        into.render { _ -> events += "feature-render" }
                        into.dispose { events += "feature-dispose" }
                    }
                },
            )
        }

        game.ready(FakeRenderer)
        game.update(0.016f, 320f, 200f)
        game.dispose()

        assertEquals(
            listOf(
                "root-ready",
                "feature-ready",
                "root-render",
                "feature-render",
                "feature-dispose",
                "root-dispose",
            ),
            events,
        )
    }
}

private object FakeRenderer : Renderer {
    override val clipSpace: ClipSpace = ClipSpace.WebGpu
    override var clearColor: Color = Color.Black
    override var wireframe: Boolean = false
    override var shadowsEnabled: Boolean = true

    override fun createMesh(geometry: MeshGeometry): Mesh = object : Mesh {
        override val format: VertexFormat =
            geometry.format

        override val sizeBytes: Long = 0

        override fun destroy() = Unit
    }

    override fun createMaterial(
        texture: TextureAsset?,
        renderTarget: RenderTarget?,
        uniformFloatCount: Int,
        pbrTextures: PbrTextureSet?,
    ): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    override fun createRenderTarget(width: Int, height: Int): RenderTarget = object : RenderTarget {
        override val width: Int = width
        override val height: Int = height
        override fun destroy() = Unit
    }

    override fun draw(camera: Lens, drawCalls: List<DrawCall>, light: SceneLight) = Unit

    override fun renderToTexture(
        target: RenderTarget,
        camera: Lens,
        drawCalls: List<DrawCall>,
        light: SceneLight,
    ) =
        Unit

    override suspend fun readPixels(target: RenderTarget): TextureAsset =
        TextureAsset(ByteArray(0), 0, 0)

    override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = Unit

    override fun drawDebugLines(lines: List<LineSegment>) = Unit

    override fun destroy() = Unit
}
