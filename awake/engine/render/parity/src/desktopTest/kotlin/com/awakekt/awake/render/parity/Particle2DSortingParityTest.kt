/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.spriteScenePipeline
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.sprites.SpriteDrawInput
import com.awakekt.awake.render.passes.sprites.SpriteRenderBatch
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.scene2d.Particle2DSystem
import com.awakekt.awake.scene.scene2d.ParticleEmitter2D
import com.awakekt.awake.scene.scene2d.SceneSprite
import com.awakekt.awake.scene.scene2d.SortingLayers
import com.awakekt.awake.scene.scene2d.Sprite
import com.awakekt.awake.scene.scene2d.collectSpriteDraws
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cross-backend headless GPU pixel verification for 2D particle emitters (`ParticleEmitter2D` ->
 * `World.collectSpriteDraws` -> `SpriteRenderBatch` -> `spriteScenePipeline(buildAdditive = true)`)
 * on both Vulkan and WebGPU.
 *
 * ### Geometry & Pixel Map (`64 × 64`, orthographic `orthoHalfHeight = 1f`, world `[-1, +1]²`)
 * - World `(-1, -1)`..`(+1, +1)` maps linearly to pixels `(0, 64)`..`(64, 0)` (`32 px` per world unit).
 * - **Scenario 1 (`verifyAlphaSorting` in [alphaSortingAndAdditiveInterleavingMatchOnBothBackends])**:
 *   - Back sprite (`Default`, `sortOrder = -1`): `1.5`u opaque blue `(0, 0, 255)` at `(0, 0)` -> `[8..55]²`.
 *   - Mid emitter (`Default`, `sortOrder = 0`): untextured `1.0`u opaque red `(255, 0, 0)` at `(0, 0)` -> `[16..47]²`.
 *   - Front sprite (`Default`, `sortOrder = +1`): `1.0`u opaque green `(0, 255, 0)` at `(+0.5, 0)` -> `x in [32..63], y in [16..47]`.
 *   - Foreground emitter (`Foreground`, `sortOrder = -10`, `z = -1`): `0.5`u opaque yellow `(255, 255, 0)` at `(+0.5, 0)` -> `x in [40..55], y in [24..39]`.
 *   - **Negative control**: setting the mid red emitter's `sortOrder = -2` drops it behind the `-1` blue sprite so `(24, 32)` reads pure blue instead of pure red.
 * - **Scenario 2 (`verifyAdditiveInterleaving` in [alphaSortingAndAdditiveInterleavingMatchOnBothBackends])**:
 *   - Same `Default` sorting layer for all three entities:
 *     1. Back alpha sprite (`sortOrder = -1`): `1.5`u opaque red `Color(0.55f, 0f, 0f, 1f)` at `(0, 0)`.
 *     2. Mid **additive** `ParticleEmitter2D` (`additive = true`, `sortOrder = 0`): `1.0`u quad `Color(0.65f, 0.5f, 0f, 1f)` at `(0, 0)`.
 *     3. Front alpha sprite (`sortOrder = +1`): `1.0`u **opaque blue** `Color(0f, 0f, 1f, 1f)` covering the right half `(+0.5, 0)`.
 *   - **Unoccluded overlap `(24, 32)`**: additive blending (`SRC_ALPHA + ONE`) sums the red channel past both inputs (`R >= 245`, clamped near `255`) and adds green (`G >= 115`), whereas the **negative control** (`additive = false`) replaces the back sprite with `0.65` red (`R <= 185`).
 *   - **Occluded overlap `(40, 32)`**: the `+1` opaque blue alpha sprite overwrites the `0` additive particle (`R <= 3, G <= 3, B >= 252`), proving additive 2D particles stay interleaved in `sortOrder` rather than being batched after alpha sprites; the **negative control** (`sortOrder = +2` on the additive emitter) sums yellow-red on top of the blue sprite (`R >= 150, G >= 115, B >= 252`).
 *
 * ### Channel Tolerances
 * - Exact opaque channels: `±3` (`<= 3` for zero channels, `>= 252` for full `1.0` channels).
 * - Fractional/additive summed channels: bounded by explicit separation bands (`>= 245` vs `<= 185` control) so 8-bit UNORM rounding matches across Vulkan and WebGPU.
 */
class Particle2DSortingParityTest {
    @Test
    fun alphaSortingAndAdditiveInterleavingMatchOnBothBackends() {
        // Open Vulkan before WebGPU and reuse one session per backend so libwgpu_native's bundled
        // Vulkan loader never precedes vkCreateInstance in the same JVM process.
        listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu).forEach { backend ->
            val app = AppSpecBuilder().build().createLifecycle()
            openSpritePlan(backend, app).use { session ->
                try {
                    runBlocking {
                        verifyAlphaSorting(backend, session.renderer)
                        verifyAdditiveInterleaving(backend, session.renderer)
                    }
                } finally {
                    session.renderer.waitIdle()
                    app.dispose()
                }
            }
        }
    }

    private suspend fun verifyAlphaSorting(backend: HeadlessUiBackend, renderer: Renderer) {
        val whiteTex = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)
        val batch = SpriteRenderBatch(renderer) { whiteTex }
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val layers = SortingLayers(listOf("Background", "Default", "Foreground"))
        try {
            suspend fun buildAndRender(midSortOrder: Int, tag: String): ByteArray {
                val world = buildAlphaSortingWorld(midSortOrder)
                return renderDraws(renderer, target, batch, world.collectSpriteDraws(layers), "particle2d-alpha-$backend-$tag")
            }

            val ordered = buildAndRender(midSortOrder = 0, tag = "ordered")
            ordered.assertRgbNear(backend, 12, 32, listOf(0, 0, 255), "back blue sprite margin")
            ordered.assertRgbNear(backend, 24, 32, listOf(255, 0, 0), "mid red particle over back blue sprite")
            ordered.assertRgbNear(backend, 36, 20, listOf(0, 255, 0), "front green sprite occluding mid red particle")
            ordered.assertRgbNear(backend, 48, 32, listOf(255, 255, 0), "Foreground layer emitter over Default front sprite")

            // Negative control: moving mid red emitter to sortOrder = -2 puts it behind the -1 blue sprite.
            val behindControl = buildAndRender(midSortOrder = -2, tag = "behind-control")
            behindControl.assertRgbNear(backend, 24, 32, listOf(0, 0, 255), "negative control: sortOrder -2 hidden behind -1 blue sprite")
        } finally {
            renderer.waitIdle()
            target.destroy()
            batch.destroy()
        }
    }

    private fun buildAlphaSortingWorld(midSortOrder: Int): World {
        val world = World()
        val transformSystem = TransformSystem()
        val particleSystem = Particle2DSystem()

        world.addSolidSprite(x = 0f, scale = 1.5f, tint = SceneColor(0f, 0f, 1f, 1f), sortOrder = -1)
        val midEmitter = world.addSolidEmitter(x = 0f, size = 1f, tint = Color(1f, 0f, 0f, 1f), sortOrder = midSortOrder)
        world.addSolidSprite(x = 0.5f, scale = 1f, tint = SceneColor(0f, 1f, 0f, 1f), sortOrder = 1)
        val fgEmitter = world.addSolidEmitter(x = 0.5f, size = 0.5f, tint = Color(1f, 1f, 0f, 1f), sortOrder = -10, z = -1f).apply {
            sortingLayer = "Foreground"
        }

        transformSystem.update(world, 0f)
        particleSystem.update(world, 0f)
        midEmitter.emit(1)
        fgEmitter.emit(1)
        particleSystem.update(world, 1f / 60f)
        return world
    }

    private fun World.addSolidSprite(x: Float, scale: Float, tint: SceneColor, sortOrder: Int) {
        create().also { e ->
            add(e, Transform().apply { position.set(x, 0f, 0f); this.scale.set(scale, scale, 1f) })
            add(e, Sprite(SceneSprite(texture = "white", pixelsPerUnit = 1f, tint = tint, sortingLayer = "Default", sortOrder = sortOrder)))
        }
    }

    private fun World.addSolidEmitter(
        x: Float,
        size: Float,
        tint: Color,
        sortOrder: Int,
        z: Float = 0f,
    ): ParticleEmitter2D = ParticleEmitter2D(maxParticles = 4, random = Random(1)).apply {
        texture = ""
        spawnRate = 0f
        lifetime = 10f
        this.size = size
        speed = 0f
        this.tint = tint
        sortingLayer = "Default"
        this.sortOrder = sortOrder
    }.also { emitter ->
        create().also { e ->
            add(e, Transform().apply { position.set(x, 0f, z) })
            add(e, emitter)
        }
    }

    private suspend fun verifyAdditiveInterleaving(backend: HeadlessUiBackend, renderer: Renderer) {
        val whiteTex = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)
        val batch = SpriteRenderBatch(renderer) { whiteTex }
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val layers = SortingLayers(listOf("Default"))
        try {
            suspend fun buildAndRender(additive: Boolean, emitterSortOrder: Int, tag: String): ByteArray {
                val world = World()
                val transformSystem = TransformSystem()
                val particleSystem = Particle2DSystem()

                // 1. Back opaque dark-red alpha sprite (sortOrder = -1)
                world.create().also { e ->
                    world.add(e, Transform().apply { scale.set(1.5f, 1.5f, 1f) })
                    world.add(
                        e,
                        Sprite(
                            SceneSprite(
                                texture = "white",
                                pixelsPerUnit = 1f,
                                tint = SceneColor(0.55f, 0f, 0f, 1f),
                                sortingLayer = "Default",
                                sortOrder = -1,
                            ),
                        ),
                    )
                }
                // 2. Interleaved 2D particle emitter (sortOrder = emitterSortOrder)
                val midEmitter = ParticleEmitter2D(maxParticles = 4, random = Random(5)).apply {
                    texture = ""
                    spawnRate = 0f
                    lifetime = 10f
                    size = 1f
                    speed = 0f
                    tint = Color(0.65f, 0.5f, 0f, 1f)
                    this.additive = additive
                    sortingLayer = "Default"
                    sortOrder = emitterSortOrder
                }
                world.create().also { e ->
                    world.add(e, Transform())
                    world.add(e, midEmitter)
                }
                // 3. Front opaque blue alpha sprite covering right half (sortOrder = +1)
                world.create().also { e ->
                    world.add(e, Transform().apply { position.set(0.5f, 0f, 0f) })
                    world.add(
                        e,
                        Sprite(
                            SceneSprite(
                                texture = "white",
                                pixelsPerUnit = 1f,
                                tint = SceneColor(0f, 0f, 1f, 1f),
                                sortingLayer = "Default",
                                sortOrder = 1,
                            ),
                        ),
                    )
                }

                transformSystem.update(world, 0f)
                particleSystem.update(world, 0f)
                midEmitter.emit(1)
                particleSystem.update(world, 1f / 60f)
                return renderDraws(renderer, target, batch, world.collectSpriteDraws(layers), "particle2d-additive-$backend-$tag")
            }

            val interleaved = buildAndRender(additive = true, emitterSortOrder = 0, tag = "interleaved")
            val unoccluded = rgb(interleaved, 24, 32)
            val occluded = rgb(interleaved, 40, 32)

            // Negative control A: straight alpha (additive = false) does NOT sum red past 0.65 (~166).
            val alphaControl = rgb(buildAndRender(additive = false, emitterSortOrder = 0, tag = "alpha-control"), 24, 32)
            // Negative control B: sortOrder = +2 draws additive particle AFTER the +1 blue sprite.
            val frontAdditiveControl = rgb(buildAndRender(additive = true, emitterSortOrder = 2, tag = "front-control"), 40, 32)

            println(
                "$backend 2D additive parity: unoccluded=$unoccluded (alphaControl=$alphaControl), " +
                    "occludedByFrontAlpha=$occluded (frontAdditiveControl=$frontAdditiveControl)",
            )

            assertTrue(
                unoccluded[0] >= 245 && unoccluded[1] >= 115 && unoccluded[2] <= TOLERANCE,
                "$backend: additive particle over dark-red sprite must sum red to saturation, got $unoccluded",
            )
            assertTrue(
                alphaControl[0] <= 185,
                "$backend: non-additive control must not sum red above source tint, got $alphaControl",
            )
            assertTrue(
                occluded[0] <= TOLERANCE && occluded[1] <= TOLERANCE && occluded[2] >= 252,
                "$backend: +1 opaque blue alpha sprite must occlude 0 additive particle in sort order, got $occluded",
            )
            assertTrue(
                frontAdditiveControl[0] >= 150 && frontAdditiveControl[1] >= 115 && frontAdditiveControl[2] >= 252,
                "$backend: sortOrder +2 control must add warm glow over +1 blue sprite, got $frontAdditiveControl",
            )
        } finally {
            renderer.waitIdle()
            target.destroy()
            batch.destroy()
        }
    }

    private suspend fun renderDraws(
        renderer: Renderer,
        target: RenderTarget,
        batch: SpriteRenderBatch,
        inputs: List<SpriteDrawInput>,
        captureName: String,
    ): ByteArray {
        renderer.renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens(),
                drawCalls = batch.collect(inputs),
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
            ),
        )
        val image = renderer.readPixels(target)
        PixelMap(SIZE, SIZE, image.data).writePng(File("build/reports/render-captures/$captureName.png"))
        return image.data.copyOf()
    }

    private fun openSpritePlan(
        backend: HeadlessUiBackend,
        app: com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle,
    ): HeadlessRenderSession {
        val plan = RenderPlan(
            primary = ScenePipeline(
                key = PipelineKey.Primary,
                shaders = PackShaderSets.LitShadow,
                vertexFormat = VertexFormat.PositionNormalColor,
            ),
            scenePipelines = listOf(spriteScenePipeline(buildAdditive = true)),
        )
        return openRuntimeSession(backend, app, SIZE, SIZE, plan)
    }

    private fun ByteArray.assertRgbNear(
        backend: HeadlessUiBackend,
        x: Int,
        y: Int,
        expected: List<Int>,
        label: String,
    ) {
        val actual = rgb(this, x, y)
        assertTrue(
            expected.zip(actual).all { (e, a) -> abs(e - a) <= TOLERANCE },
            "$backend ($label) at ($x,$y): expected $expected ±$TOLERANCE, got $actual",
        )
    }

    private fun rgb(pixels: ByteArray, x: Int, y: Int): List<Int> =
        (0 until 3).map { pixels[(y * SIZE + x) * 4 + it].toInt() and 0xff }

    private fun lens() = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, near = 0.1f, far = 20f, fovYRadians = 1f).apply {
        projection = Lens.Projection.Orthographic
        orthoHalfHeight = 1f
    }

    private companion object {
        const val SIZE = 64
        const val TOLERANCE = 3
    }
}
