/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.input.pointer.pointerCursor
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.input.CursorImage
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.dsl.cameraEntity
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneRouterRuntime
import com.awakekt.awake.scene.runtime.pointerCursorImageOf
import com.awakekt.awake.scene.runtime.pointerCursorOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SceneRouterDslTest {

    @Test
    fun routedScenesSwitchThroughCommonRuntime() = runTest {
        val renderer = RouterRecordingRenderer()
        val game = app {
            scenes {
                initial("overview")
                route("overview", label = "Overview") {
                    cameraEntity("camera")
                    assets {
                        mesh("cube") { renderer.createMesh(RouterGeometry) }
                        material("default") { renderer.createMaterial() }
                    }
                }
                route("editor", label = "Editor") {
                    cameraEntity("camera")
                    assets {
                        mesh("cube") { renderer.createMesh(RouterGeometry) }
                        material("default") { renderer.createMaterial() }
                    }
                }
            }
        }

        game.ready(renderer)
        game.update(0.016f, 640f, 480f)

        val router = game.requireService<SceneRouterRuntime>()
        assertEquals("overview", router.activeSceneId)
        assertEquals("overview", router.sceneRuntime.sceneName)

        router.switchTo("editor")
        game.update(0.016f, 640f, 480f)

        assertEquals("editor", router.activeSceneId)
        assertEquals("editor", router.sceneRuntime.sceneName)
    }

    @Test
    fun routedScenesComposeWithGameUiInstaller() = runTest {
        val renderer = RouterRecordingRenderer()
        val game = app {
            scenes {
                route("overview") {
                    cameraEntity("camera")
                    ui {
                        Text("overview ui")
                    }
                }
                route("editor") {
                    cameraEntity("camera")
                }
            }
        }

        game.ready(renderer)
        game.update(0.016f, 320f, 240f)

        assertEquals("overview", game.requireService<SceneRouterRuntime>().activeSceneLabel)
        assertTrue(renderer.lastUiPrimitives.any { it is UiDrawPrimitive.Glyph })
    }

    @Test
    fun pointerCursorOfResolvesSingleSceneAndRoutedSceneAppsBeforeAndAfterReady() = runTest {
        val singleScene = app {
            scene("single") {
                cameraEntity("camera")
            }
        }
        val singleCursor = pointerCursorOf(singleScene)
        assertEquals(PointerCursor.Default, singleCursor())

        val routed = app {
            scenes {
                route("login") { cameraEntity("camera") }
                route("world") { cameraEntity("camera") }
            }
        }
        val routedCursor = pointerCursorOf(routed)
        assertEquals(PointerCursor.Default, routedCursor(), "before ready(), routed cursor defaults safely")

        routed.ready(RouterRecordingRenderer())
        assertEquals(PointerCursor.Default, routedCursor())
    }

    @Test
    fun pointerCursorImageOfPrefersTheHoveredNodesImageOverTheScenesAndFallsBackToIt() = runTest {
        val nodeImage = CursorImage(ByteArray(4 * 4 * CursorImage.PIXEL_STRIDE), 4, 4)
        val sceneImage = CursorImage(ByteArray(2 * 2 * CursorImage.PIXEL_STRIDE), 2, 2)
        val game = app {
            scene("single") {
                cameraEntity("camera")
                ui { Box(Modifier.size(40.dp).pointerCursor(nodeImage)) }
            }
        }
        val image = pointerCursorImageOf(game)
        val hover = { x: Float ->
            game.input.setPointer(false, x, 10f)
            game.input.updateSnapshot()
            // Twice: pointer input is dispatched against the previous frame's placed tree.
            repeat(2) { game.update(0.016f, 100f, 100f) }
        }
        game.ready(RouterRecordingRenderer())

        hover(10f)
        assertSame(nodeImage, image(), "over the node: its image")

        hover(90f)
        assertNull(image(), "off the node with no scene image: the system shape")

        game.requireService<SceneAppLifecycleRuntime>().cursorImage = sceneImage
        assertSame(sceneImage, image(), "off the node: the scene's image")

        hover(10f)
        assertSame(nodeImage, image(), "the node's image beats the scene's")

        hover(90f)
        assertSame(sceneImage, image(), "leaving the node falls back to the scene's image")
    }

    @Test
    fun pointerCursorImageOfResolvesRoutedAppsBeforeAndAfterReady() = runTest {
        val routed = app {
            scenes {
                route("login") { cameraEntity("camera") }
            }
        }
        val image = pointerCursorImageOf(routed)
        assertNull(image(), "before ready(), no scene is active")

        routed.ready(RouterRecordingRenderer())
        assertNull(image())

        val sceneImage = CursorImage(ByteArray(CursorImage.PIXEL_STRIDE), 1, 1)
        routed.requireService<SceneRouterRuntime>().sceneRuntime.cursorImage = sceneImage
        assertSame(sceneImage, image())
    }
}

private val RouterGeometry = MeshGeometry(floatArrayOf(), intArrayOf())

private class RouterRecordingRenderer :
    Renderer,
    GpuDrawPreparationSource {
    override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    var lastUiPrimitives: List<UiDrawPrimitive> = emptyList()

    override val clipSpace: ClipSpace = ClipSpace.WebGpu
    override var clearColor: Color = Color.Black
    override var wireframe: Boolean = false

    override fun createMesh(geometry: MeshGeometry): Mesh = object : Mesh {
        override val format: VertexFormat = geometry.format
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

    override suspend fun readPixels(target: RenderTarget): TextureAsset =
        TextureAsset(ByteArray(target.width * target.height * 4), target.width, target.height)

    override fun draw(input: GpuPassInput) = Unit

    override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) {
        lastUiPrimitives = primitives
    }

    override fun drawDebugLines(lines: List<LineSegment>) = Unit

    override fun destroy() = Unit
}
