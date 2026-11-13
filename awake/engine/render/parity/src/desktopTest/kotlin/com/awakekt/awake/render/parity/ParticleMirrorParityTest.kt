/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.setParticleInstance
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A particle with a negative size is the same sprite mirrored on both axes, on both backends.
 *
 * One 2x2 sprite, red / green over blue / yellow, is drawn through the particle shader straight on
 * through an orthographic lens at +2, -2 and 0 size. Each
 * quadrant's centre pixel names the colour that landed there. Vulkan is what desktop and Android ship;
 * WebGPU is compared against it, so the two cannot disagree about which way a mirrored sprite faces.
 */
class ParticleMirrorParityTest {
    @Test
    fun aNegativeSizeFlipsTheSpriteOnBothAxesAndKeepsItsFootprint() = onEveryBackend { backend, renderer ->
        val upright = renderer.draw(SPRITE)
        val mirrored = renderer.draw(-SPRITE)

        assertEquals(UPRIGHT, upright.quadrants(), "$backend: positive size must read the texture as authored")
        assertEquals(MIRRORED, mirrored.quadrants(), "$backend: negative size must be the positive one flipped on both axes")
        assertEquals(upright.drawn(), mirrored.drawn(), "$backend: mirroring must not move or resize the sprite")
        val box = mirrored.drawn().box()
        assertTrue(box.first in 62..66 && box.second in 62..66, "$backend: sprite should span about 64 px, spans $box")
    }

    @Test
    fun aZeroSizeParticleDrawsNothing() = onEveryBackend { backend, renderer ->
        val empty = renderer.draw(0f)

        assertTrue(empty.drawn().isEmpty(), "$backend: a zero-size particle covered ${empty.drawn().size} pixels")
        val clear = empty.pixel(0, 0)
        val strays = (0 until SIZE * SIZE).count { empty.pixel(it % SIZE, it / SIZE) != clear }
        assertEquals(0, strays, "$backend: a zero-size particle left $strays pixels differing from the clear colour")
    }

    @Test
    fun vulkanAndWebGpuAgreeOnEveryMirroredFrame() {
        val frames = BACKENDS.map { backend ->
            val renderer = session(backend).renderer
            listOf(
                renderer.draw(SPRITE),
                renderer.draw(-SPRITE),
            )
        }
        frames[0].zip(frames[1]).forEachIndexed { index, (vulkan, webGpu) ->
            val differing = (0 until SIZE * SIZE).count {
                val a = vulkan.pixel(it % SIZE, it / SIZE)
                val b = webGpu.pixel(it % SIZE, it / SIZE)
                a.indices.any { channel -> abs(a[channel] - b[channel]) > CHANNEL_TOLERANCE }
            }
            assertTrue(differing <= EDGE_PIXELS, "frame $index: $differing pixels differ between Vulkan and WebGPU")
        }
    }

    private fun onEveryBackend(check: (HeadlessUiBackend, Renderer) -> Unit) =
        BACKENDS.forEach { backend -> check(backend, session(backend).renderer) }

    /** One sprite of signed [size] at the centre, seen straight on, as a [Frame]. */
    private fun Renderer.draw(size: Float): Frame {
        val target = createRenderTarget(SIZE, SIZE)
        val quad = createMesh(SpriteQuad)
        val material = createMaterial(ParticleUniformLayout, texture = FOUR_COLOURS)
        return try {
            val lens = Lens(eye = Vec3f(0f, 0f, EYE_DISTANCE), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 50f).apply {
                projection = Lens.Projection.Orthographic
                orthoHalfHeight = VIEW_HALF_HEIGHT
            }
            val sprite = RenderDrawCommand(
                mesh = quad,
                material = material,
                instanceModels = listOf(Mat4().setParticleInstance(0f, 0f, 0f, size)),
                instanceColors = listOf(Vec4(1f, 1f, 1f, 1f)),
                instanceFrames = listOf(0f),
                // cameraRight, cameraUp, frameInfo: ParticleExtraUniformLayout. The camera looks down -Z.
                extraUniformFloats = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 0f),
            )
            renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = lens,
                    drawCalls = listOf(sprite),
                    light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
                    environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false),
                    clipSpace = clipSpace,
                    aspect = 1f,
                    drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
                ),
            )
            Frame(runBlocking { readPixels(target) }.data.copyOf(SIZE * SIZE * 4))
        } finally {
            quad.destroy()
            material.destroy()
            target.destroy()
        }
    }

    /** A frame's RGBA bytes, row 0 first. */
    private class Frame(private val bytes: ByteArray) {
        fun pixel(x: Int, y: Int): List<Int> = List(4) { bytes[(y * SIZE + x) * 4 + it].toInt() and 0xFF }

        /** The colour name at each quadrant's centre: top-left, top-right, bottom-left, bottom-right. */
        fun quadrants(): List<String> = listOf(LOW to LOW, HIGH to LOW, LOW to HIGH, HIGH to HIGH).map { (x, y) -> pixel(x, y).name() }

        /** Positions of the pixels that are not the clear colour. */
        fun drawn(): List<Pair<Int, Int>> {
            val clear = pixel(0, 0)
            return (0 until SIZE * SIZE).map { it % SIZE to it / SIZE }.filter { (x, y) -> pixel(x, y) != clear }
        }

        private fun List<Int>.name(): String = when {
            this[0] > LIT && this[1] < DIM && this[2] < DIM -> "red"
            this[1] > LIT && this[0] < DIM && this[2] < DIM -> "green"
            this[2] > LIT && this[0] < DIM && this[1] < DIM -> "blue"
            this[0] > LIT && this[1] > LIT && this[2] < DIM -> "yellow"
            else -> "other$this"
        }
    }

    private fun List<Pair<Int, Int>>.box(): Pair<Int, Int> =
        (maxOf { it.first } - minOf { it.first } + 1) to (maxOf { it.second } - minOf { it.second } + 1)

    private companion object {
        val BACKENDS = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        private val sessions = mutableMapOf<HeadlessUiBackend, HeadlessRenderSession>()

        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        fun session(backend: HeadlessUiBackend): HeadlessRenderSession = sessions.getOrPut(backend) { openHeadlessScene(backend) }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            sessions.remove(HeadlessUiBackend.Vulkan)?.close()
        }

        const val SIZE = 128
        const val EYE_DISTANCE = 4f
        const val VIEW_HALF_HEIGHT = 2f

        /** 2 world units across, so 64 px at 128 px / 4 units. */
        const val SPRITE = 2f

        /** Quadrant centres: the sprite spans pixels 32..95, so each quarter's middle is 48 or 80. */
        const val LOW = 48
        const val HIGH = 80
        const val LIT = 200
        const val DIM = 60
        const val CHANNEL_TOLERANCE = 24
        const val EDGE_PIXELS = 64

        val UPRIGHT = listOf("red", "green", "blue", "yellow")
        val MIRRORED = listOf("yellow", "blue", "green", "red")

        /** Red / green over blue / yellow: no flip of either axis, or both, looks like another. */
        val FOUR_COLOURS = TextureAsset(
            data = byteArrayOf(
                -1, 0, 0, -1, 0, -1, 0, -1,
                0, 0, -1, -1, -1, -1, 0, -1,
            ),
            width = 2,
            height = 2,
        )
    }
}
