/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.EmitterPlacement
import com.awakekt.awake.particles.ParticleDrawBuilder
import com.awakekt.awake.particles.ParticleSystem
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.particles.ParticleContentSystem
import com.awakekt.awake.scene.particles.ParticleEmitterSource
import com.awakekt.awake.scene.particles.SceneParticleEmitter
import com.awakekt.awake.scene.particles.loadParticleImages
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Decoded PNG particle sprites drawn through [ParticleContentSystem] must appear right-side up on
 * both Vulkan and WebGPU.
 *
 * Awake's image decoders (`decodeImage` / `createBitmap` / WebGPU `flipY = true`) store rows
 * bottom-up so GPU `v = 0` samples the PNG's bottom row and `v = 1` samples its top row (matching
 * `SpriteRenderBatch`). A real 2x2 PNG with a red top row and green bottom row is decoded via
 * [loadParticleImages], attached by [ParticleContentSystem], stepped by [ParticleSystem], and
 * rendered head-on; the sprite's upper half must be red and its lower half green.
 *
 * Negative control (measured before fixing `ParticleContentSystem.QUAD` UVs): both Vulkan and WebGPU
 * failed with `top=[4, 250, 0, 255]` (green) and `bottom=[250, 4, 0, 255]` (red), rendering every
 * decoded PNG particle sprite vertically upside-down.
 */
class ParticleContentOrientationParityTest {
    @Test
    fun vulkanDrawsDecodedPngParticlesRightSideUp() = verify(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuDrawsDecodedPngParticlesRightSideUp() = verify(HeadlessUiBackend.WebGpu)

    private fun verify(backend: HeadlessUiBackend) = runBlocking {
        val renderer = session(backend).renderer
        val emitterSpec = SceneParticleEmitter(
            texture = TEXTURE_PATH,
            maxParticles = 1,
            spawnRate = 60f,
            lifetime = 10f,
            startAlpha = 1f,
            scale = SPRITE_SIZE,
            endScale = SPRITE_SIZE,
            velocity = SceneVec3(0f, 0f, 0f),
        )
        val document = SceneDocument(nodes = listOf(SceneNode("flame", components = listOf(emitterSpec))))
        val assets = AssetSource { path ->
            if (path.value == TEXTURE_PATH) Result.success(TOP_RED_BOTTOM_GREEN_PNG) else Result.failure(IllegalStateException("missing ${path.value}"))
        }
        val images = loadParticleImages(document, assets)
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val content = ParticleContentSystem(renderer, emptyMap(), images)
        val pixels = try {
            val world = World()
            val entity = world.create()
            world.add(entity, Transform())
            world.add(entity, ParticleEmitterSource(emitterSpec))
            TransformSystem().update(world, 0f)
            content.update(world, 0f)
            ParticleSystem(TransformPlacement).update(world, STEP_SECONDS)
            val lens = Lens(eye = Vec3f(0f, 0f, EYE_DISTANCE), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 50f).apply {
                projection = Lens.Projection.Orthographic
                orthoHalfHeight = VIEW_HALF_HEIGHT
            }
            val draws = ArrayList<RenderDrawCommand>()
            ParticleDrawBuilder().appendWorldDrawCalls(draws, world, TransformPlacement, lens, 1f)
            renderer.renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = lens,
                    drawCalls = draws,
                    light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
                    environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false),
                    clipSpace = renderer.clipSpace,
                    aspect = 1f,
                    drawPreparer = (renderer as? GpuDrawPreparationSource)?.gpuDrawPreparer,
                ),
            )
            renderer.readPixels(target).data.copyOf(SIZE * SIZE * RGBA)
        } finally {
            content.close()
            target.destroy()
        }
        fun pixel(x: Int, y: Int): List<Int> = List(RGBA) { pixels[(y * SIZE + x) * RGBA + it].toInt() and 0xFF }
        val top = pixel(CENTER_X, TOP_Y)
        val bottom = pixel(CENTER_X, BOTTOM_Y)
        println("PARTICLE_PNG_ORIENTATION[$backend] top=$top bottom=$bottom")
        assertTrue(top[0] > LIT && top[1] < DIM, "$backend: PNG top row (red) must land at top of particle quad, got top=$top bottom=$bottom")
        assertTrue(bottom[1] > LIT && bottom[0] < DIM, "$backend: PNG bottom row (green) must land at bottom of particle quad, got top=$top bottom=$bottom")
    }

    private object TransformPlacement : EmitterPlacement {
        override fun position(world: World, entity: Entity, into: Vec3f): Boolean {
            val matrix = world.get<Transform>(entity)?.worldMatrix ?: return false
            into.set(matrix.m03, matrix.m13, matrix.m23)
            return true
        }

        override fun orientation(world: World, entity: Entity): Mat4? = world.get<Transform>(entity)?.worldMatrix
    }

    private companion object {
        private val sessions = mutableMapOf<HeadlessUiBackend, HeadlessRenderSession>()

        fun session(backend: HeadlessUiBackend): HeadlessRenderSession = sessions.getOrPut(backend) { openHeadlessScene(backend) }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            sessions.remove(HeadlessUiBackend.Vulkan)?.close()
        }

        const val TEXTURE_PATH = "flame.png"
        const val SIZE = 128
        const val RGBA = 4
        const val EYE_DISTANCE = 4f
        const val VIEW_HALF_HEIGHT = 2f
        const val SPRITE_SIZE = 2f
        const val STEP_SECONDS = 1f / 30f
        const val CENTER_X = 64
        const val TOP_Y = 48
        const val BOTTOM_Y = 80
        const val LIT = 200
        const val DIM = 60

        /** Real 2x2 PNG: top row (y=0) red, bottom row (y=1) green. */
        val TOP_RED_BOTTOM_GREEN_PNG: ByteArray = ByteArrayOutputStream().also { out ->
            val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, 0xFFFF0000.toInt())
            image.setRGB(1, 0, 0xFFFF0000.toInt())
            image.setRGB(0, 1, 0xFF00FF00.toInt())
            image.setRGB(1, 1, 0xFF00FF00.toInt())
            ImageIO.write(image, "png", out)
        }.toByteArray()
    }
}
