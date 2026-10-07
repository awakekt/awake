/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.math.ViewportScaling
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.physics.DegreesOfFreedom
import com.awakekt.awake.render.capture.FramebufferAttachment
import com.awakekt.awake.render.capture.FramebufferAttachmentData
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.scene.binding.destroy
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.scene2d.Sprite
import com.awakekt.awake.scene.scene2d.SpriteClips
import com.awakekt.awake.scene.scene2d.Tilemap
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.showcase.app.engineShowcaseApp
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Loads assets/examples/runtime-2d.scene.json through the sample app and its production plan.
 *
 * Measured on both backends at 384x224: floor 12288 pixels; editing adds 1024 blue pixels;
 * animation replaces 576 gold pixels with 576 red pixels. Permanent controls: paused clips
 * retain 576 gold/0 red; removing Tilemap gives 0 green/0 blue. At 448x224, Fit retains
 * 12288 green/4096 blue, whereas Stretch gives 14336 green/4800 blue. Without the runtime's
 * pixel-dimension capture fix, the initial readback throws "A virtual viewport capture
 * requires pixel width and height." WebGPU scheduled frames use an offscreen target;
 * this verifies the production app's capture path, without browser presentation.
 */
class Runtime2dSceneParityTest {
    @Test
    fun tilesAnimationPhysicsViewportAndExportWorkTogether() = runBlocking {
        for (backend in HeadlessUiBackend.entries) {
            val app = engineShowcaseApp(initialShowcaseId = "runtime-2d")
            open(backend, app).use { session ->
                try {
                    app.ready(session.renderer)
                    app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
                    val runtime = app.requireService<SceneAppLifecycleRuntime>()
                    val actor = runtime.named("falling-sprite")
                    val mapEntity = runtime.named("tiles")
                    val map = requireNotNull(runtime.world.get<Tilemap>(mapEntity))
                    val sprite = requireNotNull(runtime.world.get<Sprite>(actor))
                    val clips = requireNotNull(runtime.world.get<SpriteClips>(actor))
                    val initial = runtime.capture()
                    val initialCounts = counts(initial)
                    assertTrue(initialCounts.green > 10000, "$backend: tile floor missing: $initialCounts")
                    assertTrue(initialCounts.gold > 500, "$backend: animated sprite missing: $initialCounts")

                    map.setTile(7, 3, 1)
                    val edited = counts(runtime.capture())
                    println("$backend integrated tiles: initial=$initialCounts edited=$edited")
                    assertTrue(edited.blue - initialCounts.blue in 1000..1040, "$backend: tile edit must add a cell: $edited")

                    clips.speed = 0f
                    repeat(31) { app.update(STEP, WIDTH.toFloat(), HEIGHT.toFloat()) }
                    val paused = counts(runtime.capture())
                    assertEquals(0, paused.red, "$backend: paused animation control")
                    assertTrue(paused.gold > 500, "$backend: paused sprite must remain visible")
                    clips.speed = 1f
                    repeat(31) { app.update(STEP, WIDTH.toFloat(), HEIGHT.toFloat()) }
                    val animated = counts(runtime.capture())
                    assertEquals(3, sprite.frame, "$backend: sample clip must advance before rendering")
                    assertTrue(animated.red > 500 && animated.gold == 0, "$backend: next clip frame must reach pixels: $animated")
                    clips.speed = 0f
                    repeat(90) { app.update(STEP, WIDTH.toFloat(), HEIGHT.toFloat()) }
                    val pose = requireNotNull(runtime.world.get<Transform>(actor))
                    assertTrue(pose.position.y in -2.1f..-1.9f, "$backend: sprite must settle on its floor: ${pose.position}")
                    assertEquals(0.25f, pose.position.z, 0.001f)
                    assertEquals(0f, pose.rotation.x, 0.001f)
                    assertEquals(0f, pose.rotation.y, 0.001f)
                    assertEquals(DegreesOfFreedom.PLANE_2D, requireNotNull(runtime.world.get<PhysicsBody>(actor)).degreesOfFreedom)
                    val settled = counts(runtime.capture())
                    assertTrue(settled.red > 500, "$backend: settled sprite must remain visible: $settled")

                    val wide = counts(runtime.capture(WIDTH + 64))
                    assertTrue(abs(wide.blue - settled.blue) <= 8, "$backend: Fit must preserve cell size on a wider target: $wide / $settled")
                    assertTrue(abs(wide.green - settled.green) <= 8, "$backend: Fit must preserve floor size: $wide / $settled")
                    val stretched = verifyStretchControl(runtime, backend, wide)

                    verifyExport(runtime)

                    // Permanent rendering control: removing the layer must remove its coloured floor.
                    runtime.world.remove<Tilemap>(mapEntity)
                    val removed = counts(runtime.capture())
                    assertEquals(0, removed.green, "$backend: removed tile layer must release its draw contribution")
                    assertEquals(0, removed.blue, "$backend: removed tile layer must remove the edited cell")
                    println("$backend integrated runtime: paused=$paused animated=$animated settled=$settled wide=$wide stretched=$stretched removed=$removed, actorY=${pose.position.y}")
                } finally {
                    session.renderer.waitIdle()
                    app.dispose()
                }
            }
        }
    }

    private fun verifyExport(runtime: SceneAppLifecycleRuntime) {
        val exported = SceneLoader.fromWorld(runtime.world, "runtime-2d-export")
        val reloaded = SceneLoader.instantiate(SceneLoader.decode(SceneLoader.encode(exported)))
        try {
            val reloadedMap = reloaded.roots.single { it.name == "tiles" }.let { reloaded.world.get<Tilemap>(it.entity) }
            assertEquals(1, requireNotNull(reloadedMap).grid[7, 3])
            val reloadedBody = reloaded.roots.single { it.name == "falling-sprite" }.let { reloaded.world.get<PhysicsBody>(it.entity) }
            assertEquals(DegreesOfFreedom.PLANE_2D, requireNotNull(reloadedBody).degreesOfFreedom)
        } finally {
            reloaded.destroy()
        }
    }

    private suspend fun verifyStretchControl(runtime: SceneAppLifecycleRuntime, backend: HeadlessUiBackend, wide: Counts): Counts {
        val camera = requireNotNull(runtime.world.get<Camera>(runtime.named("camera")))
        val viewport = requireNotNull(camera.viewport)
        camera.viewport = viewport.copy(scaling = ViewportScaling.Stretch)
        return try {
            counts(runtime.capture(WIDTH + 64)).also {
                assertTrue(it.blue > wide.blue + 500, "$backend: Stretch control must change cell size: $it")
            }
        } finally {
            camera.viewport = viewport
        }
    }

    private fun SceneAppLifecycleRuntime.named(name: String): Entity = buildList {
        world.queryEach<Name> { entity, component -> if (component.value == name) add(entity) }
    }.single()

    private suspend fun SceneAppLifecycleRuntime.capture(width: Int = WIDTH): FramebufferAttachmentData =
        readbackAttachment(width, HEIGHT, FramebufferAttachment.Color0).also { assertTrue(it.available, it.reason) }

    private fun counts(image: FramebufferAttachmentData): Counts {
        var green = 0
        var blue = 0
        var gold = 0
        var red = 0
        for (pixel in 0 until image.width * image.height) {
            val offset = pixel * image.channels
            val r = image.values[offset]
            val g = image.values[offset + 1]
            val b = image.values[offset + 2]
            // Colour dominance survives both linear and sRGB attachment encodings.
            if (g > r + 0.2f && g > b + 0.2f) green++
            if (b > r + 0.2f && b > g + 0.2f) blue++
            if (r > b + 0.3f && g > b + 0.3f && abs(r - g) < 0.25f) gold++
            if (r > g + 0.3f && r > b + 0.3f) red++
        }
        return Counts(green, blue, gold, red)
    }

    private fun open(backend: HeadlessUiBackend, app: AwakeAppLifecycle): HeadlessRenderSession = when (backend) {
        HeadlessUiBackend.WebGpu -> {
            val session = webGpuHeadlessPlan(EngineShowcaseRenderPlan, surface = HeadlessSurface(WIDTH, HEIGHT))
            val renderer = OffscreenFrames(session.renderer)
            object : HeadlessRenderSession {
                override val renderer: Renderer = renderer
                override fun close() {
                    renderer.destroyFrameTarget()
                    session.close()
                }
            }
        }
        HeadlessUiBackend.Vulkan -> {
            val renderer = runBlocking { PlanEngine(app).boot() }
            object : HeadlessRenderSession {
                override val renderer: Renderer = renderer
                override fun close() = renderer.destroy()
            }
        }
    }

    private class PlanEngine(app: AwakeAppLifecycle) : VulkanEngine(app, EngineShowcaseRenderPlan) {
        suspend fun boot(): Renderer = createBackendResources(HeadlessSurface(WIDTH, HEIGHT)).renderer
    }

    /** wgpu-native's fixture has no presentation loop; execute scheduled frames offscreen. */
    private class OffscreenFrames(private val backend: Renderer) :
        Renderer by backend,
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = (backend as GpuDrawPreparationSource).gpuDrawPreparer
        private val target = backend.createRenderTarget(WIDTH, HEIGHT)
        override fun draw(input: GpuPassInput) = backend.renderToTexture(target, input)
        fun destroyFrameTarget() {
            backend.waitIdle()
            target.destroy()
        }
    }

    private data class Counts(val green: Int, val blue: Int, val gold: Int, val red: Int)

    private companion object {
        const val WIDTH = 384
        const val HEIGHT = 224
        const val STEP = 1f / 60f
    }
}
