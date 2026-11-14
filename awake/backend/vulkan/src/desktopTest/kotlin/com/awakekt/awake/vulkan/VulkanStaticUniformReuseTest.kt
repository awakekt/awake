/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.terrainContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.SpriteUniformLayout
import com.awakekt.awake.render.passes.uniforms.TrailUniformLayout
import com.awakekt.awake.render.passes.uniforms.WorldTextUniformLayout
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.passes.uniforms.setParticleInstance
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
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

    @Test
    fun remainingSceneDrawsReuseTheirBlocksUnderAMovingCamera() {
        val session = vulkanHeadlessScene(SIZE, SIZE)
        try {
            val renderer = session.renderer
            val device = (renderer as VulkanRenderer).graphicsDevice
            val target = renderer.createRenderTarget(SIZE, SIZE)
            val skinnedMesh = renderer.createMesh(skinnedQuad(half = 1.2f))
            val spriteMesh = renderer.createMesh(uvQuad(half = 0.8f))
            val textMesh = renderer.createMesh(uvColorQuad(half = 0.8f))
            val trailMesh = renderer.createMesh(rgbaUvQuad(half = 0.8f))
            val particleMesh = renderer.createMesh(uvQuad(half = 0.5f))
            val skinnedMat = renderer.createMaterial(SkinnedUniformLayout, texture = WHITE, pbrTextures = PbrTextureSet())
            val spriteMat = renderer.createMaterial(SpriteUniformLayout, texture = WHITE)
            val textMat = renderer.createMaterial(WorldTextUniformLayout, texture = WHITE)
            val trailMat = renderer.createMaterial(TrailUniformLayout, texture = WHITE)
            val particleMat = renderer.createMaterial(ParticleUniformLayout, texture = WHITE)
            val palette = FloatArray(32).also { Mat4().data.copyInto(it, 0); Mat4().data.copyInto(it, 16) }
            val spriteExtras = floatArrayOf(0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f)
            val textExtras = floatArrayOf(1f, 1f, 1f, 1f, 0.5f, 0.25f, 0f, 0f)
            val trailExtras = floatArrayOf(1f, 1f, 1f, 1f)
            val particleExtras = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f, 0f, 0f)
            val particleInstances = listOf(Mat4().setParticleInstance(2f, 0.5f, 0f, 1.2f))
            val particleColors = listOf(Vec4(1f, 1f, 1f, 1f))
            val particleFrames = listOf(0f)
            val sun = SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f))

            fun render(eyeX: Float, timeSeconds: Float): Frame {
                val lens = Lens(eye = Vec3f(eyeX, 8f, 6f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)
                val writtenBefore = device.drawBlockWrites
                val draws = listOf(
                    RenderDrawCommand(mesh = skinnedMesh, material = skinnedMat, model = translation(-1.5f, 0f, 0f), extraUniformFloats = palette, timeSeconds = timeSeconds),
                    RenderDrawCommand(mesh = spriteMesh, material = spriteMat, model = translation(0f, 0.2f, -1.5f), extraUniformFloats = spriteExtras, transparent = true, timeSeconds = timeSeconds),
                    RenderDrawCommand(mesh = textMesh, material = textMat, model = translation(0f, 0.4f, 1.5f), extraUniformFloats = textExtras, transparent = true, timeSeconds = timeSeconds),
                    RenderDrawCommand(mesh = trailMesh, material = trailMat, model = translation(1.5f, 0.3f, -1.2f), extraUniformFloats = trailExtras, transparent = true, timeSeconds = timeSeconds),
                    RenderDrawCommand(
                        mesh = particleMesh,
                        material = particleMat,
                        instanceModels = particleInstances,
                        instanceColors = particleColors,
                        instanceFrames = particleFrames,
                        extraUniformFloats = particleExtras,
                        transparent = true,
                        timeSeconds = timeSeconds,
                    ),
                )
                val pass = ScenePassCompiler.compile(
                    lens = lens,
                    drawCalls = draws,
                    light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace)),
                    clipSpace = renderer.clipSpace,
                    aspect = 1f,
                    drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
                )
                val written = (device.drawBlockWrites - writtenBefore).toInt()
                renderer.renderToTexture(target, pass)
                return Frame(written, runBlocking { renderer.readPixels(target) }.data)
            }
            try {
                var warm = render(eyeX = 0f, timeSeconds = 0f)
                repeat(WARM_FRAMES - 1) { warm = render(eyeX = 0f, timeSeconds = it + 1f) }
                val moved = render(eyeX = 1.5f, timeSeconds = 9f)

                assertFalse(warm.pixels.contentEquals(moved.pixels), "the scene draws from the moved camera")
                assertEquals(0, moved.written, "none of the five shaders rewrites its draw block when only the camera and clock move")
            } finally {
                listOf(skinnedMat, spriteMat, textMat, trailMat, particleMat).forEach { it.destroy() }
                listOf(skinnedMesh, spriteMesh, textMesh, trailMesh, particleMesh).forEach { it.destroy() }
                target.destroy()
            }
        } finally {
            session.close()
        }
    }

    @Test
    fun terrainReusesItsContentUniformsWhileTheCameraStaysWithinASnapCell() = runBlocking {
        val session = vulkanHeadlessScene(SIZE, SIZE)
        try {
            val renderer = session.renderer
            val device = (renderer as VulkanRenderer).graphicsDevice
            val attached = (renderer as ContentFeatureHost).attachContentFeature(
                terrainContentFeature(
                    shaders = PackShaderSets.Terrain,
                    heightmap = domeHeightmap(),
                    config = TerrainClipmapConfig(ringCount = 3, ringResolution = 16, baseSpacing = 4f),
                ),
            )
            val target = renderer.createRenderTarget(SIZE, SIZE)
            suspend fun render(eyeX: Float): Pair<Int, ByteArray> {
                val writesBefore = device.contentUniformWrites
                renderer.renderSceneToTexture(
                    target,
                    Lens(eye = Vec3f(eyeX, 8f, 16f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 200f),
                    emptyList(),
                )
                return (device.contentUniformWrites - writesBefore).toInt() to renderer.readPixels(target).data
            }
            try {
                var warmPixels = render(eyeX = 0f).second
                repeat(WARM_FRAMES - 1) { warmPixels = render(eyeX = 0f).second }
                val (inCellWrites, movedPixels) = render(eyeX = 1f)
                val (snappedWrites, _) = render(eyeX = 5f)

                assertFalse(warmPixels.contentEquals(movedPixels), "moving the camera changes the rendered terrain")
                assertEquals(0, inCellWrites, "staying within the 4-unit snap cell leaves the terrain uniform buffer untouched")
                assertEquals(1, snappedWrites, "crossing into the next snap cell writes the shifted ring origins")
            } finally {
                target.destroy()
                attached.detach()
            }
        } finally {
            session.close()
        }
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

        fun skinnedQuad(half: Float): MeshGeometry {
            val corners = listOf(-half to -half, half to -half, half to half, -half to half)
            val j0 = Float.fromBits(0)
            val vertices = corners.flatMap { (x, z) ->
                val u = (x + half) / (2 * half)
                val v = (z + half) / (2 * half)
                listOf(x, 0f, z, 0f, 1f, 0f, 1f, 1f, 1f, u, v, j0, j0, j0, j0, 1f, 0f, 0f, 0f)
            }
            return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColorUvSkin)
        }

        fun uvQuad(half: Float): MeshGeometry {
            val corners = listOf(-half to -half, half to -half, half to half, -half to half)
            val vertices = corners.flatMap { (x, z) ->
                listOf(x, 0f, z, (x + half) / (2 * half), (z + half) / (2 * half))
            }
            return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionUv)
        }

        fun uvColorQuad(half: Float): MeshGeometry {
            val corners = listOf(-half to -half, half to -half, half to half, -half to half)
            val vertices = corners.flatMap { (x, z) ->
                listOf(x, 0f, z, (x + half) / (2 * half), (z + half) / (2 * half), 1f, 1f, 1f, 1f)
            }
            return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionUvColor)
        }

        fun rgbaUvQuad(half: Float): MeshGeometry {
            val corners = listOf(-half to -half, half to -half, half to half, -half to half)
            val vertices = corners.flatMap { (x, z) ->
                listOf(x, 0f, z, 1f, 1f, 1f, 1f, (x + half) / (2 * half), (z + half) / (2 * half))
            }
            return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionRgbaUv)
        }

        fun domeHeightmap(samples: Int = 17): Heightmap = Heightmap(
            samples = FloatArray(samples * samples) { index ->
                val x = (index % samples - samples / 2f) / (samples / 2f)
                val z = (index / samples - samples / 2f) / (samples / 2f)
                (1f - (x * x + z * z)).coerceAtLeast(0f)
            },
            width = samples,
            depth = samples,
            scale = Vec3f(2f, 6f, 2f),
        )
    }
}
