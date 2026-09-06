/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.bootstrap

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.engine.bootstrap.dsl.appDefinition
import com.awakekt.awake.engine.bootstrap.dsl.appModule
import com.awakekt.awake.engine.bootstrap.dsl.createApp
import com.awakekt.awake.engine.bootstrap.dsl.createAppSpec
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

class AppLifecycleDslTutorialDocsTest {

    @Test
    fun reusableModuleOwnsTheGameContent() = runTest {
        val feature = appModule {
            service(String::class, "hello-cube")
        }

        val game = feature.createApp {
            title = "Hello Cube"
            size(1600, 900)
            backend.vulkan()
        }

        assertEquals("Hello Cube", game.windowConfig.title)
        assertEquals(1600, game.windowConfig.width)
        assertEquals(900, game.windowConfig.height)
        assertEquals("hello-cube", game.requireService(String::class))

        recordAppDslTutorial(
            name = "game-dsl-module-shell",
            title = "Game Shell Around A Reusable Module",
            summary = "A sample or game can keep its authored content in a reusable gameModule while the root shell only owns window and platform concerns.",
            snippet = """
                val feature = gameModule {
                    service(String::class, "hello-cube")
                }

                val game = feature.createGame {
                    title = "Hello Cube"
                    size(1600, 900)
                    backend.vulkan()
                }
            """,
        )
    }

    @Test
    fun modulesCanComposeOtherModulesAndStillBuildSpecs() = runTest {
        val events = mutableListOf<String>()
        val debugModule = appModule {
            service(String::class, "debug")
            ready { events += "debug-ready" }
        }
        val hudModule = appModule {
            service(Int::class, 2)
            ready { events += "hud-ready" }
        }
        val feature = appModule {
            module(debugModule)
            module(hudModule)
        }

        val spec = feature.createAppSpec {
            title = "Composable"
            size(960, 540)
            backend.webGpu()
        }
        val game = spec.createLifecycle()
        game.ready(TutorialRenderer)

        assertEquals("Composable", spec.windowConfig.title)
        assertEquals("debug", game.requireService(String::class))
        assertEquals(2, game.requireService(Int::class))
        assertEquals(listOf("debug-ready", "hud-ready"), events)

        recordAppDslTutorial(
            name = "game-dsl-composed-modules",
            title = "Modules Compose Into Larger Features",
            summary = "Reusable modules can stack other modules before they are wrapped in a root GameSpec, which keeps authored content split without reintroducing sample-local bootstrap glue.",
            snippet = """
                val debugModule = gameModule {
                    service(String::class, "debug")
                }
                val hudModule = gameModule {
                    service(Int::class, 2)
                }
                val feature = gameModule {
                    module(debugModule)
                    module(hudModule)
                }

                val spec = feature.createGameSpec {
                    title = "Composable"
                    size(960, 540)
                    backend.webGpu()
                }
            """,
        )
    }

    @Test
    fun statefulDefinitionKeepsSamplesOnOneRootPattern() = runTest {
        val definition = appDefinition(createState = { mutableListOf("boot") }) {
            window {
                title = "Starter Pattern"
                size(1600, 900)
                backend.vulkan()
            }
            module { state ->
                appModule {
                    ready { state += "ready" }
                    service(List::class, state)
                }
            }
        }

        val game = definition.createApp()
        game.ready(TutorialRenderer)

        assertEquals("Starter Pattern", game.windowConfig.title)
        assertEquals(listOf("boot", "ready"), game.requireService(List::class))

        recordAppDslTutorial(
            name = "game-dsl-stateful-definition",
            title = "Stateful Game Definitions Stay Reusable",
            summary = "A reusable authored game can own its runtime state factory, window configuration, and module factory in one engine-level definition instead of teaching every sample its own wrapper pattern.",
            snippet = """
                val definition = gameDefinition(createState = ::RuntimeState) {
                    window {
                        title = "Starter Pattern"
                        size(1600, 900)
                        backend.vulkan()
                    }
                    module { state ->
                        gameModule {
                            service(RuntimeState::class, state)
                        }
                    }
                }

                val game = definition.createGame()
            """,
        )
    }
}

private object TutorialRenderer : Renderer {
    override val clipSpace: ClipSpace = ClipSpace.WebGpu
    override var clearColor: Color = Color.Black
    override var wireframe: Boolean = false
    override var shadowsEnabled: Boolean = true

    override fun createMesh(geometry: MeshGeometry): Mesh = object : Mesh {
        override val format: com.awakekt.awake.core.geometry.VertexFormat =
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
        TextureAsset(ByteArray(target.width * target.height * 4), target.width, target.height)

    override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = Unit

    override fun drawDebugLines(lines: List<LineSegment>) = Unit

    override fun destroy() = Unit
}
