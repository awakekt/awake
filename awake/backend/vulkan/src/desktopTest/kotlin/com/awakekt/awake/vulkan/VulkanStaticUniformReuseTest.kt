/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

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
import com.awakekt.awake.vulkan.renderer.Renderer as VulkanRenderer

/**
 * A static prop's own uniform block is written once per frame in flight and then left alone,
 * whatever the camera does, and the frames it is left alone on draw what a renderer that wrote
 * every block draws (#1452).
 *
 * The scene is the WebGPU test's of the same name: a lit-and-shadowed ground, a lit prop above it
 * casting onto it, and a textured prop, planned through the shared compiler with shadow cascades.
 * Counts are taken once every frame in flight has drawn the scene, because each has its own slots.
 *
 * Negative control, measured: with a slot's recorded inputs matching whatever is asked of them,
 * the moved prop's frame read back the frame before's pixels, and the waving prop's wrote 0
 * blocks, not 1.
 */
class VulkanStaticUniformReuseTest {

    @Test
    fun aStaticPropIsNotRewrittenOnAFrameWhereNothingChanged() = withScene { scene ->
        val warm = scene.warmUp()
        val still = scene.frame(timeSeconds = 9f)

        assertEquals(0, still.written, "nothing moved, so no draw's block is written")
        assertContentEquals(warm.pixels, still.pixels, "and the frame draws the same")
    }

    @Test
    fun aMovedPropIsWrittenAloneAndDrawnWhereItMoved() = withScene { scene ->
        val warm = scene.warmUp()
        val moved = scene.frame(timeSeconds = 9f, propX = 1.5f)

        assertFalse(warm.pixels.contentEquals(moved.pixels), "the prop is drawn where it moved to")
        assertEquals(1, moved.written, "and only the prop that moved is written")
    }

    @Test
    fun aMovedCameraWritesNoDrawsBlock() = withScene { scene ->
        val warm = scene.warmUp()
        val turned = scene.frame(timeSeconds = 9f, eyeX = 1f)

        assertFalse(warm.pixels.contentEquals(turned.pixels), "the scene is drawn from where the camera moved to")
        assertEquals(0, turned.written, "no draw's block carries the camera, so none is written")
    }

    @Test
    fun aMovedCameraDrawsWhatAFreshRendererDrawsFromThere() {
        val moved = withScene { scene ->
            scene.warmUp()
            scene.frame(timeSeconds = 9f, eyeX = 1f).pixels
        }
        val fresh = withScene { scene -> scene.frame(timeSeconds = 9f, eyeX = 1f).pixels }

        assertContentEquals(fresh, moved, "blocks kept from earlier frames draw as blocks written for this camera")
    }

    @Test
    fun anAnimatedPropIsWrittenEveryFrame() = withScene { scene ->
        repeat(WARM_FRAMES) { scene.frame(timeSeconds = it.toFloat(), wave = WAVE) }
        val next = scene.frame(timeSeconds = 9f, wave = WAVE)

        assertEquals(1, next.written, "a waving prop's clock moves it, so its block is written")
    }

    private fun <T> withScene(block: (Scene) -> T): T {
        val session = vulkanHeadlessScene(SIZE, SIZE)
        return try {
            Scene(session.renderer).use(block)
        } finally {
            session.close()
        }
    }

    private class Frame(val written: Int, val pixels: ByteArray)

    private class Scene(val renderer: RenderRenderer) : AutoCloseable {
        private val device = (renderer as VulkanRenderer).graphicsDevice
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
        private val sun = SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f))
        private val pbr = pbrMaterialFloats(metallic = 0f, roughness = 1f, baseColorFactor = Color.White, emissiveFactor = Color.Transparent)

        /** The still scene, drawn until every frame in flight holds its blocks; returns the last. */
        fun warmUp(): Frame {
            var last = frame(timeSeconds = 0f)
            repeat(WARM_FRAMES - 1) { last = frame(timeSeconds = it + 1f) }
            return last
        }

        /** Plans and draws the scene at [timeSeconds] from [eyeX], with the lit prop at [propX] waving by [wave]. */
        fun frame(timeSeconds: Float, propX: Float = 0f, wave: Vec3f = STILL, eyeX: Float = 0f): Frame {
            val lens = Lens(eye = Vec3f(eyeX, 9f, 7f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)
            val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            val writtenBefore = device.drawBlockWrites
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
            val written = (device.drawBlockWrites - writtenBefore).toInt()
            renderer.renderToTexture(target, pass)
            return Frame(written, runBlocking { renderer.readPixels(target) }.data)
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

        /** More than any renderer keeps frames in flight, so each one's slots hold the scene. */
        const val WARM_FRAMES = 4
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
