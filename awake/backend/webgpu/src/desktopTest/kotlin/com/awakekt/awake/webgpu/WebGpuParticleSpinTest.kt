/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
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
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A particle's spin turns its quad in its own plane, proven on real pixels.
 *
 * One sprite, red on its left half and blue on its right, is drawn from the particle shader at a
 * chosen spin, and the picture says which way round it came out. The same scene runs through both
 * backends in the render parity suite; this one needs only WebGPU, so it runs wherever the Vulkan
 * natives are not built.
 *
 * Each test has an answer the shader before spin could not give: it ignored column 2 of the instance
 * matrix, so every sprite came out upright whatever its spin.
 */
class WebGpuParticleSpinTest {

    @Test
    fun theFrameIsReadTopRowFirst() = withScene { renderer ->
        val high = renderer.draw(spin = 0f, centreY = 1f)

        assertTrue(high.centroid { it.isDrawn() }.y < SIZE / 2.0, "a sprite above the centre must read as above it, so rows run top to bottom")
    }

    @Test
    fun anUnturnedSpriteKeepsItsRedLeftOfItsBlue() = withScene { renderer ->
        val picture = renderer.draw(spin = 0f)

        val red = picture.centroid { it.isRed() }
        val blue = picture.centroid { it.isBlue() }
        assertTrue(red.x < blue.x - SPREAD, "red x=${red.x} must be left of blue x=${blue.x}")
        assertTrue(abs(red.y - blue.y) < 1.0, "an unturned sprite's halves sit level: red y=${red.y}, blue y=${blue.y}")
    }

    /** Counter-clockwise as the viewer sees it: the right half (blue) goes to the top, the left half (red) to the bottom. */
    @Test
    fun aQuarterTurnPutsBlueOnTopAndRedBelow() = withScene { renderer ->
        val picture = renderer.draw(spin = (PI / 2).toFloat())

        val red = picture.centroid { it.isRed() }
        val blue = picture.centroid { it.isBlue() }
        assertTrue(blue.y < red.y - SPREAD, "blue y=${blue.y} must be above red y=${red.y}")
        assertTrue(abs(red.x - blue.x) < 1.0, "a quarter turn puts the halves in a column: red x=${red.x}, blue x=${blue.x}")
    }

    /** An eighth of a turn makes a diamond, whose box is the square's diagonal wide and tall. */
    @Test
    fun anEighthOfATurnDrawsADiamondAsWideAsTheSquaresDiagonal() = withScene { renderer ->
        val upright = renderer.draw(spin = 0f).extent { it.isDrawn() }
        val diamond = renderer.draw(spin = (PI / 4).toFloat()).extent { it.isDrawn() }

        val ratio = diamond.width.toDouble() / upright.width
        assertTrue(ratio in 1.3..1.5, "the box should grow by the square root of two, grew by $ratio")
        assertTrue(abs(diamond.width - diamond.height) <= 2, "a diamond is as tall as it is wide: ${diamond.width} x ${diamond.height}")
    }

    /** A flat sprite takes its axes from the plane it lies in, and the spin turns it within that plane. */
    @Test
    fun aFlatSpriteTurnsInItsOwnPlane() = withScene { renderer ->
        val upright = renderer.draw(spin = 0f, flat = true)
        val turned = renderer.draw(spin = (PI / 2).toFloat(), flat = true)

        assertTrue(upright.centroid { it.isRed() }.x < upright.centroid { it.isBlue() }.x - SPREAD, "seen from above, red starts left of blue")
        assertTrue(turned.centroid { it.isBlue() }.y < turned.centroid { it.isRed() }.y - SPREAD, "and a quarter turn puts blue above red")
    }

    /** A stretched particle points along its motion, so the spin written beside the stretch changes nothing. */
    @Test
    fun aStretchedParticleIgnoresItsSpin() = withScene { renderer ->
        val plain = renderer.draw(spin = 0f, stretchX = 1f)
        val spun = renderer.draw(spin = (PI / 2).toFloat(), stretchX = 1f)

        assertTrue(plain.drawn.isNotEmpty(), "the stretched sprite drew nothing")
        assertTrue(plain.drawn == spun.drawn, "spin changed the pixels of a stretched particle")
    }

    private fun withScene(block: (Renderer) -> Unit) = block(shared.renderer)

    /** One sprite drawn at [spin] radians, seen straight on (or from above when [flat]), as a [Picture]. */
    private fun Renderer.draw(spin: Float, flat: Boolean = false, centreY: Float = 0f, stretchX: Float = 0f): Picture {
        val target = createRenderTarget(SIZE, SIZE)
        val quad = shared.quad
        val material = shared.material
        return try {
            val eye = if (flat) Vec3f(0f, EYE_DISTANCE, 0f) else Vec3f(0f, 0f, EYE_DISTANCE)
            val up = if (flat) Vec3f(0f, 0f, -1f) else Vec3f(0f, 1f, 0f)
            val lens = Lens(eye = eye, center = Vec3f(0f, 0f, 0f), up = up, fovYRadians = 1f, near = 0.1f, far = 50f)
            val forward = (lens.center - lens.eye).normalized()
            val right = forward.cross(lens.up).normalized()
            val cameraUp = right.cross(forward)
            // A flat sprite lies in the ground plane: its +X and -Z are its axes, as the scene writes them.
            val quadRight = if (flat) Vec3f(1f, 0f, 0f) else right
            val quadUp = if (flat) Vec3f(0f, 0f, -1f) else cameraUp
            val sprite = RenderDrawCommand(
                mesh = quad,
                material = material,
                instanceModels = listOf(Mat4().setParticleInstance(0f, centreY, 0f, SPRITE_SIZE, stretchX = stretchX, rotation = spin)),
                instanceColors = listOf(Vec4(1f, 1f, 1f, 1f)),
                instanceFrames = listOf(0f),
                // cameraRight, cameraUp, frameInfo: ParticleExtraUniformLayout.
                extraUniformFloats = floatArrayOf(
                    quadRight.x, quadRight.y, quadRight.z, 0f,
                    quadUp.x, quadUp.y, quadUp.z, 0f,
                    1f, 0f, 0f, 0f,
                ),
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
            Picture(runBlocking { readPixels(target) }.data)
        } finally {
            target.destroy()
        }
    }

    /** The frame's RGBA bytes, row 0 first. */
    private class Picture(private val bytes: ByteArray) {
        fun channel(x: Int, y: Int, channel: Int): Int = bytes[(y * SIZE + x) * 4 + channel].toInt() and 0xFF

        fun Pixel.isRed() = r > LIT && r > 2 * b
        fun Pixel.isBlue() = b > LIT && b > 2 * r
        fun Pixel.isDrawn() = maxOf(r, b) > LIT

        private fun pixels(): List<Pixel> = (0 until SIZE).flatMap { y ->
            (0 until SIZE).map { x -> Pixel(x, y, channel(x, y, 0), channel(x, y, 2)) }
        }

        /** The mean position of the pixels [where] holds, which must be some. */
        fun centroid(where: Picture.(Pixel) -> Boolean): Point {
            val hits = pixels().filter { where(it) }
            assertTrue(hits.isNotEmpty(), "no pixel matched, so the sprite did not draw as expected")
            return Point(hits.map { it.x }.average(), hits.map { it.y }.average())
        }

        /** The box round the pixels [where] holds. */
        fun extent(where: Picture.(Pixel) -> Boolean): Extent {
            val hits = pixels().filter { where(it) }
            assertTrue(hits.isNotEmpty(), "no pixel matched, so the sprite did not draw as expected")
            return Extent(hits.maxOf { it.x } - hits.minOf { it.x } + 1, hits.maxOf { it.y } - hits.minOf { it.y } + 1)
        }

        /** The drawn pixels' positions, to compare two pictures exactly. */
        val drawn: List<Pair<Int, Int>> get() = pixels().filter { it.isDrawn() }.map { it.x to it.y }
    }

    private data class Pixel(val x: Int, val y: Int, val r: Int, val b: Int)

    private data class Point(val x: Double, val y: Double)

    private data class Extent(val width: Int, val height: Int)

    private class Shared {
        private val session = webGpuHeadlessScene()
        val renderer: Renderer = session.renderer
        val quad = renderer.createMesh(QUAD)
        val material = renderer.createMaterial(ParticleUniformLayout, texture = RED_LEFT_BLUE_RIGHT)
    }

    private companion object {
        /** One headless session, quad and material for the whole class, as the scene reuses its own. */
        val shared: Shared by lazy { Shared() }

        const val SIZE = 128
        const val EYE_DISTANCE = 4f
        const val SPRITE_SIZE = 2f

        /** A channel above this is lit, against the black the frame is cleared to. */
        const val LIT = 100

        /** The least gap, in pixels, that tells two halves of the sprite apart. */
        const val SPREAD = 4.0

        /** Two texels: red on the left, blue on the right. */
        val RED_LEFT_BLUE_RIGHT = TextureAsset(data = byteArrayOf(-1, 0, 0, -1, 0, 0, -1, -1), width = 2, height = 1)

        /** The unit quad the scene's particle content draws: position and UV in the XY plane. */
        val QUAD = MeshGeometry(
            floatArrayOf(
                -0.5f, -0.5f, 0f, 0f, 1f,
                0.5f, -0.5f, 0f, 1f, 1f,
                0.5f, 0.5f, 0f, 1f, 0f,
                -0.5f, 0.5f, 0f, 0f, 0f,
            ),
            intArrayOf(0, 1, 2, 2, 3, 0),
            format = VertexFormat.PositionUv,
        )
    }
}
