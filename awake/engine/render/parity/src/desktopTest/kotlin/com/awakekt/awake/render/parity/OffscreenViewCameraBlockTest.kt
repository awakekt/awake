/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.sky.Skybox
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasView
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #1687: a frame whose main camera draws nothing must not overwrite the camera block an offscreen
 * View, submitted earlier in the frame and still running on the GPU, reads. The main camera here looks
 * at empty space, so it prepares no draw; a transparent View refreshed every frame shows a lit cube
 * through its own camera. Frames run back to back with no pause, as a game loop runs them, and every
 * third one is read back. A View drawn through the main camera's matrices shows only the sky.
 */
class OffscreenViewCameraBlockTest {
    @Test
    fun aViewKeepsItsCameraWhenTheMainCameraDrawsNothing() = runBlocking {
        lateinit var runtime: SceneAppLifecycleRuntime
        val game = app {
            window { size(SIZE, SIZE) }
            scene("offscreen-view-camera-block") {
                onReady {
                    runtime = this
                    drawsSceneCanvas = false
                    populate()
                }
                ui { SceneCanvas(runtime.world, modifier = Modifier.fillMaxSize(), views = runtime.canvasViews()) }
            }
        }
        var sampled = 0
        var missing = 0
        openRuntimeSession(HeadlessUiBackend.Vulkan, game, SIZE, SIZE).use { session ->
            try {
                game.ready(session.renderer)
                repeat(FRAMES) { frame ->
                    game.update(1f / 60f, SIZE.toFloat(), SIZE.toFloat())
                    if (frame >= WARM_UP && frame % READ_EVERY == 0) {
                        sampled++
                        if (viewPixels(session.renderer.readPresentedPixels().data) < MIN_CUBE_PIXELS) missing++
                    }
                }
            } finally {
                session.renderer.waitIdle()
                game.dispose()
            }
        }
        println("OffscreenViewCameraBlock: the View's cube was missing in $missing of $sampled sampled frames")
        assertTrue(sampled > 0)
        assertEquals(0, missing, "frames whose View lost its camera to the main one")
    }

    /** Pixels inside the View's rectangle that are not the sky: the cube and its ground. */
    private fun viewPixels(frame: ByteArray): Int {
        var count = 0
        for (y in VIEW_AT + INSET until VIEW_AT + VIEW_SIDE - INSET) {
            for (x in VIEW_AT + INSET until VIEW_AT + VIEW_SIDE - INSET) {
                val at = (y * SIZE + x) * 4
                val differs = (0 until 3).any { abs((frame[at + it].toInt() and 0xFF) - SKY[it]) > TOLERANCE }
                if (differs) count++
            }
        }
        return count
    }

    /** A lit cube on a ground the portrait camera sees; the main camera looks the other way. */
    private fun SceneAppLifecycleRuntime.populate() {
        val lit = renderer.createMaterial(LitShadowUniformLayout)
        world.add(world.create(), Camera(Lens(eye = Vec3f(0f, 4f, 5f), center = Vec3f(0f, 4f, 50f), fovYRadians = 1f, near = 0.1f, far = 100f)))
        world.add(world.create(), Skybox(mode = Skybox.Mode.SolidColor(Color(SKY[0] / 255f, SKY[1] / 255f, SKY[2] / 255f))))
        world.add(world.create(), Light(direction = Vec3f(-1f, 2f, 0.5f)))
        world.create().also {
            world.add(it, Transform(position = Vec3f(SIDE, 0f, 0f)))
            world.add(it, MeshRenderer(renderer.createMesh(generate { plane(size = 6f) }), lit))
        }
        world.create().also {
            world.add(it, Transform(position = Vec3f(SIDE, 0.5f, 0f)))
            world.add(it, MeshRenderer(renderer.createMesh(generate { cube(size = 1f) }), lit))
        }
        world.create().also {
            world.add(it, Name("Portrait"))
            world.add(it, Camera(Lens(eye = Vec3f(SIDE, 4f, 5f), center = Vec3f(SIDE, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f), isPrimary = false))
        }
        world.add(
            world.create(),
            CanvasElement().apply {
                kind = CanvasElementKind.View; camera = "Portrait"
                offsetX = VIEW_AT.toFloat(); offsetY = VIEW_AT.toFloat(); width = VIEW_SIDE.toFloat(); height = VIEW_SIDE.toFloat()
                view = CanvasView(transparent = true)
            },
        )
    }

    private companion object {
        const val SIZE = 128
        const val VIEW_AT = 16
        const val VIEW_SIDE = 96
        const val INSET = 4
        const val SIDE = 100f
        const val FRAMES = 300
        const val WARM_UP = 6
        const val READ_EVERY = 3
        const val TOLERANCE = 12
        const val MIN_CUBE_PIXELS = 200
        val SKY = intArrayOf(26, 51, 204)
    }
}
