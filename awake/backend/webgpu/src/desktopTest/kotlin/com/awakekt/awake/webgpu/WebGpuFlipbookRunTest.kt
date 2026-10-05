/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A frame sheet's run can start at any cell, proven on real pixels.
 *
 * A 2 x 2 sheet of four flat colours is drawn on a plane, and the colour at the centre says which
 * cell the shader chose at each time. The same scenario runs through both backends in the render
 * parity suite; this one needs only WebGPU, so it runs wherever the Vulkan natives are not built.
 *
 * Each test has an answer the old shader could not give. It always played from cell 0, so a run
 * starting at cell 2 would have shown red and green, and a held cell 1 would have shown red.
 */
class WebGpuFlipbookRunTest {

    @Test
    fun aRunStartsAtItsFirstFrameAndLoopsInsideItsLength() = withScene { renderer ->
        val run = TextureAnimation(columns = 2, rows = 2, frameCount = 2, framesPerSecond = 1f, firstFrame = 2)

        val played = MID_FRAME_TIMES.map { renderer.centreHue(run, it) }

        assertEquals(listOf(BLUE, YELLOW, BLUE), played, "frames 2 and 3, then 2 again")
    }

    /** The control: the same run from the first cell plays the first row, so a start frame is told from none. */
    @Test
    fun theSameRunFromTheFirstCellPlaysTheFirstRow() = withScene { renderer ->
        val run = TextureAnimation(columns = 2, rows = 2, frameCount = 2, framesPerSecond = 1f)

        val played = MID_FRAME_TIMES.map { renderer.centreHue(run, it) }

        assertEquals(listOf(RED, GREEN, RED), played, "frames 0 and 1, then 0 again")
    }

    @Test
    fun aHeldRunShowsOneChosenCellWhateverTheTime() = withScene { renderer ->
        val held = TextureAnimation(columns = 2, rows = 2, frameCount = 1, framesPerSecond = 0f, firstFrame = 1)

        val shown = listOf(0.5f, 1.5f, 7.5f).map { renderer.centreHue(held, it) }

        assertEquals(listOf(GREEN, GREEN, GREEN), shown)
    }

    private fun withScene(block: (Renderer) -> Unit) = webGpuHeadlessScene().use { block(it.renderer) }

    /** The colour channels at the centre of a plane showing [animation] at [timeSeconds]: its hue, whatever the lighting. */
    private fun Renderer.centreHue(animation: TextureAnimation, timeSeconds: Float): Set<Int> {
        val target = createRenderTarget(SIZE, SIZE)
        val mesh = createMesh(plane())
        val material = createMaterial(
            texture = SHEET,
            uniformFloatCount = MaterialUniformLayouts.PbrTextured.total,
            pbrTextures = PbrTextureSet(),
        )
        return try {
            val draw = RenderDrawCommand(
                mesh = mesh,
                material = material,
                extraUniformFloats = pbrMaterialFloats(
                    metallic = 0.1f,
                    roughness = 0.45f,
                    baseColorFactor = Color.White,
                    emissiveFactor = Color.Transparent,
                    textureAnimation = animation,
                ),
                timeSeconds = timeSeconds,
            )
            renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f),
                    drawCalls = listOf(draw),
                    light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f), ambient = null),
                    environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false),
                    clipSpace = clipSpace,
                    aspect = 1f,
                    drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
                ),
            )
            val pixels = runBlocking { readPixels(target) }.data
            val offset = (SIZE / 2 * SIZE + SIZE / 2) * 4
            val channels = (0..2).map { pixels[offset + it].toInt() and 0xFF }
            channels.indices.filter { channels[it] * 2 >= channels.max() }.toSet()
        } finally {
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private fun plane() = MeshGeometry(
        floatArrayOf(
            -HALF, 0f, -HALF, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f,
            HALF, 0f, -HALF, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f,
            HALF, 0f, HALF, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f,
            -HALF, 0f, HALF, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 1f,
        ),
        intArrayOf(0, 1, 2, 2, 3, 0),
        VertexFormat.PositionNormalColorUv,
    )

    private companion object {
        const val SIZE = 128
        const val HALF = 6f
        const val EYE_Y = 6f
        const val EYE_Z = 9f

        /** Channel indices: red, green, blue. Yellow is red and green. */
        val RED = setOf(0)
        val GREEN = setOf(1)
        val BLUE = setOf(2)
        val YELLOW = setOf(0, 1)

        /** Mid-frame at one frame a second. */
        val MID_FRAME_TIMES = listOf(0.5f, 1.5f, 2.5f)

        /** Red, green, blue, yellow: cells 0 to 3 of [SHEET], in reading order from the image's top left. */
        val CELL_COLOURS = listOf(
            byteArrayOf(-1, 0, 0),
            byteArrayOf(0, -1, 0),
            byteArrayOf(0, 0, -1),
            byteArrayOf(-1, -1, 0),
        )

        /** A 2 x 2 sheet of 2 x 2 texels per cell. Data row 0 is the image's bottom row. */
        val SHEET = TextureAsset(
            data = ByteArray(4 * 4 * 4) { index ->
                val texel = index / 4
                val cell = (if (texel / 4 < 2) 2 else 0) + (if (texel % 4 < 2) 0 else 1)
                if (index % 4 == 3) -1 else CELL_COLOURS[cell][index % 4]
            },
            width = 4,
            height = 4,
        )
    }
}
