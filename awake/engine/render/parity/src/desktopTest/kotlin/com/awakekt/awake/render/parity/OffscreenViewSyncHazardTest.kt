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
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The scene of #1676 under Vulkan synchronization validation: a main scene, drawn through the
 * headless swapchain with its frames in flight, and a transparent camera View refreshed every frame
 * through `submitToTexture`. Any `SYNC-HAZARD-*` the validation layer reports fails it.
 *
 * Run by `desktopSyncValidationTest`, which turns the layer and its synchronization validation on;
 * the layer's messages reach the test as the device's `AWAKE_VERIFY_VALIDATION` lines.
 */
class OffscreenViewSyncHazardTest {
    @Test
    fun aSceneWithAnOffscreenViewRunsFramesWithNoSynchronizationHazard() = assertNoHazards("main scene and offscreen View", withView = true)

    @Test
    fun theMainSceneAloneRunsFramesWithNoSynchronizationHazard() = assertNoHazards("main scene alone", withView = false)

    private fun assertNoHazards(label: String, withView: Boolean) {
        assertEquals("true", System.getenv(SYNC_VALIDATION_ENV), "run through desktopSyncValidationTest, which enables synchronization validation")
        val output = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(TeeStream(original, output), true))
        try {
            runScene(FRAMES, withView)
        } finally {
            System.setOut(original)
        }
        val hazards = output.toString().lineSequence().filter { "SYNC-HAZARD" in it }.toList()
        println("OffscreenViewSyncHazard ($label): ${hazards.size} SYNC-HAZARD messages over $FRAMES frames")
        hazards.groupingBy { HAZARD.find(it)?.value ?: "?" }.eachCount().forEach { (kind, count) -> println("  $kind: $count") }
        assertEquals(0, hazards.size, "$label, synchronization hazards:\n${hazards.take(3).joinToString("\n\n")}")
    }

    private fun runScene(frames: Int, withView: Boolean) = runBlocking {
        lateinit var runtime: SceneAppLifecycleRuntime
        val game = app {
            window { size(SIZE, SIZE) }
            scene("offscreen-view") {
                onReady {
                    runtime = this
                    drawsSceneCanvas = false
                    populate(withView)
                }
                ui { SceneCanvas(runtime.world, modifier = Modifier.fillMaxSize(), views = runtime.canvasViews()) }
            }
        }
        openRuntimeSession(HeadlessUiBackend.Vulkan, game, SIZE, SIZE).use { session ->
            try {
                game.ready(session.renderer)
                repeat(frames) { game.update(1f / 60f, SIZE.toFloat(), SIZE.toFloat()) }
            } finally {
                session.renderer.waitIdle()
                game.dispose()
            }
        }
    }

    /** A lit, shadowed cube on a ground for each camera, the portrait's [SIDE] units away. */
    private fun SceneAppLifecycleRuntime.populate(withView: Boolean) {
        val cube = generate { cube(size = 1f) }
        val ground = generate { plane(size = 6f) }
        val lit = renderer.createMaterial(LitShadowUniformLayout)
        world.add(world.create(), Camera(lens(0f)))
        world.add(world.create(), Skybox(mode = Skybox.Mode.SolidColor(Color(0.1f, 0.2f, 0.8f))))
        world.add(world.create(), Light(direction = Vec3f(-1f, 2f, 0.5f)))
        for (x in listOf(0f, SIDE)) {
            world.create().also {
                world.add(it, Transform(position = Vec3f(x, 0f, 0f)))
                world.add(it, MeshRenderer(renderer.createMesh(ground), lit))
            }
            world.create().also {
                world.add(it, Transform(position = Vec3f(x, 0.5f, 0f)))
                world.add(it, MeshRenderer(renderer.createMesh(cube), lit))
            }
        }
        world.create().also {
            world.add(it, Name("Portrait"))
            world.add(it, Camera(lens(SIDE), isPrimary = false))
        }
        if (!withView) return
        world.add(
            world.create(),
            CanvasElement().apply {
                kind = CanvasElementKind.View; camera = "Portrait"
                offsetX = 16f; offsetY = 16f; width = 96f; height = 96f
                view = CanvasView(transparent = true)
            },
        )
    }

    private fun lens(side: Float) = Lens(eye = Vec3f(side, 4f, 5f), center = Vec3f(side, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)

    /** Writes to both [first] and [second]. */
    private class TeeStream(private val first: java.io.OutputStream, private val second: java.io.OutputStream) : java.io.OutputStream() {
        override fun write(b: Int) {
            first.write(b)
            second.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            first.write(b, off, len)
            second.write(b, off, len)
        }
    }

    private companion object {
        const val SIZE = 128
        const val FRAMES = 60
        const val SIDE = 50f
        const val SYNC_VALIDATION_ENV = "VK_KHRONOS_VALIDATION_VALIDATE_SYNC"
        val HAZARD = Regex("SYNC-HAZARD-[A-Z-]+")
    }
}
