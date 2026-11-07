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
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import com.awakekt.awake.render.renderer.Renderer as RenderRenderer
import com.awakekt.awake.webgpu.renderer.Renderer as WebGpuRenderer

/**
 * A static prop's uniform block is uploaded once and then left alone while nothing about it or the
 * frame changes, and the frame it is left alone on draws the same pixels (#1452).
 *
 * The scene is a lit-and-shadowed ground, a lit prop above it casting onto it, and a textured
 * prop, all planned through the shared compiler with shadow cascades, as a scene's static props
 * are. Each frame is planned at a later clock time, as `RenderSystem3D` plans them.
 *
 * Negative controls, measured: with the frame's clock written into every block regardless of
 * animation, the still frame staged 3 blocks, not 0. With [UniformUploads.stageChanged] skipping
 * every block whatever it holds, the moved prop's frame read back the first frame's pixels.
 */
class WebGpuStaticUniformReuseTest {

    @Test
    fun aStaticPropIsNotReUploadedOnAFrameWhereNothingChanged() = withScene { scene ->
        val first = scene.frame(timeSeconds = 1f)
        val still = scene.frame(timeSeconds = 2f)

        assertEquals(DRAWS, first.staged, "the first frame uploads every draw's block")
        assertEquals(0, still.staged, "nothing moved, so nothing is uploaded")
        assertContentEquals(first.pixels, still.pixels, "and the frame draws the same")
    }

    @Test
    fun aMovedPropIsUploadedAloneAndDrawnWhereItMoved() = withScene { scene ->
        val first = scene.frame(timeSeconds = 1f)
        val moved = scene.frame(timeSeconds = 2f, propX = 1.5f)

        assertFalse(first.pixels.contentEquals(moved.pixels), "the prop is drawn where it moved to")
        assertEquals(1, moved.staged, "and only the prop that moved is uploaded")
    }

    @Test
    fun anAnimatedPropIsUploadedEveryFrame() = withScene { scene ->
        scene.frame(timeSeconds = 1f, wave = WAVE)
        val next = scene.frame(timeSeconds = 2f, wave = WAVE)

        assertEquals(1, next.staged, "a waving prop's clock moves it, so its block changes")
    }

    private fun withScene(block: (Scene) -> Unit) = webGpuHeadlessScene().use { session ->
        Scene(session.renderer).use(block)
    }

    private class Frame(val staged: Int, val pixels: ByteArray)

    private class Scene(val renderer: RenderRenderer) : AutoCloseable {
        private val target = renderer.createRenderTarget(SIZE, SIZE)
        private val ground = renderer.createMesh(quad(VertexFormat.PositionNormalColor, half = GROUND_HALF))
        private val prop = renderer.createMesh(quad(VertexFormat.PositionNormalColor, half = PROP_HALF))
        private val card = renderer.createMesh(quad(VertexFormat.PositionNormalColorUv, half = PROP_HALF))
        private val lit = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
        private val textured = renderer.createMaterial(
            texture = WHITE,
            uniformFloatCount = MaterialUniformLayouts.PbrTextured.total,
            pbrTextures = PbrTextureSet(),
        )
        private val lens = Lens(eye = Vec3f(0f, 9f, 7f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)
        private val sun = SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f))
        private val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
        private val pbr = pbrMaterialFloats(metallic = 0f, roughness = 1f, baseColorFactor = Color.White, emissiveFactor = Color.Transparent)

        /** Plans and draws the scene at [timeSeconds], with the lit prop at [propX] waving by [wave]. */
        fun frame(timeSeconds: Float, propX: Float = 0f, wave: Vec3f = STILL): Frame {
            val draws = listOf(
                draw(ground, lit, Mat4(), timeSeconds),
                draw(prop, lit, translation(propX, PROP_HEIGHT, 0f), timeSeconds, wave),
                draw(card, textured, translation(-2.5f, CARD_HEIGHT, 1f), timeSeconds, extras = pbr),
            )
            val pass = ScenePassCompiler.compile(
                lens = lens,
                drawCalls = draws,
                light = light,
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
            )
            val staged = (renderer as WebGpuRenderer).uniformUploads.buffers.size
            renderer.renderToTexture(target, pass)
            return Frame(staged, runBlocking { renderer.readPixels(target) }.data)
        }

        @Suppress("LongParameterList")
        private fun draw(mesh: Mesh, material: Material, model: Mat4, time: Float, wave: Vec3f = STILL, extras: FloatArray = NO_EXTRAS) =
            RenderDrawCommand(
                mesh = mesh,
                material = material,
                model = model,
                extraUniformFloats = extras,
                vertexAnimation = wave,
                timeSeconds = time,
            )

        override fun close() {
            listOf(ground, prop, card).forEach { it.destroy() }
            lit.destroy()
            textured.destroy()
            target.destroy()
        }
    }

    private companion object {
        const val SIZE = 96
        const val DRAWS = 3
        const val GROUND_HALF = 6f
        const val PROP_HALF = 1.2f
        const val PROP_HEIGHT = 1.5f
        const val CARD_HEIGHT = 0.5f
        val STILL = Vec3f(0f, 0f, 0f)
        val WAVE = Vec3f(0.4f, 1f, 3f)
        val NO_EXTRAS = FloatArray(0)
        val WHITE = TextureAsset(data = ByteArray(4) { -1 }, width = 1, height = 1)

        fun translation(x: Float, y: Float, z: Float) = Mat4().apply {
            data[12] = x
            data[13] = y
            data[14] = z
        }

        /** A flat square facing up, white, with UVs when [format] has them. */
        fun quad(format: VertexFormat, half: Float): MeshGeometry {
            val uv = format == VertexFormat.PositionNormalColorUv
            val corners = listOf(-half to -half, half to -half, half to half, -half to half)
            val vertices = corners.flatMap { (x, z) ->
                listOf(x, 0f, z, 0f, 1f, 0f, 1f, 1f, 1f) + if (uv) listOf((x + half) / (2 * half), (z + half) / (2 * half)) else emptyList()
            }
            return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), format)
        }
    }
}
