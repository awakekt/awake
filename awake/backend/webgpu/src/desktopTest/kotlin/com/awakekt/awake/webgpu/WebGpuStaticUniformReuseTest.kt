/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

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
 * every block whatever it holds, the moved prop's frame read back the first frame's pixels. With
 * `DrawUniformInputs.matches` answering yes for any slot it has seen, the moved prop again kept its
 * old pixels. With the frame block staged only on the first frame, the moved camera's frame read
 * back the first frame's pixels.
 */
class WebGpuStaticUniformReuseTest {

    @Test
    fun aStaticPropIsNotReUploadedOnAFrameWhereNothingChanged() = withScene { scene ->
        val first = scene.frame(timeSeconds = 1f)
        val still = scene.frame(timeSeconds = 2f)

        assertEquals(DRAWS + FRAME_BLOCK + IDENTITY_BLOCK, first.staged, "the first frame uploads every draw's block and the frame's")
        assertEquals(DRAWS, first.packed, "having packed each")
        assertEquals(0, still.packed, "nothing moved, so nothing is packed")
        assertEquals(0, still.staged, "or uploaded")
        assertContentEquals(first.pixels, still.pixels, "and the frame draws the same")
    }

    @Test
    fun aMovedPropIsUploadedAloneAndDrawnWhereItMoved() = withScene { scene ->
        val first = scene.frame(timeSeconds = 1f)
        val moved = scene.frame(timeSeconds = 2f, propX = 1.5f)

        assertFalse(first.pixels.contentEquals(moved.pixels), "the prop is drawn where it moved to")
        assertEquals(1, moved.packed, "and only the prop that moved is packed")
        assertEquals(1, moved.staged, "and uploaded")
    }

    @Test
    fun aMovedCameraUploadsTheFrameBlockAndNoDraws() = withScene { scene ->
        val first = scene.frame(timeSeconds = 1f)
        val turned = scene.frame(timeSeconds = 2f, eyeX = 1f)

        assertFalse(first.pixels.contentEquals(turned.pixels), "the scene is drawn from where the camera moved to")
        assertEquals(0, turned.packed, "no draw's block carries the camera, so none is packed")
        assertEquals(FRAME_BLOCK, turned.staged, "and the frame block alone is uploaded")
    }

    @Test
    fun aMovedCameraDrawsWhatAFreshRendererDrawsFromThere() {
        val moved = withScene { scene ->
            scene.frame(timeSeconds = 1f)
            scene.frame(timeSeconds = 2f, eyeX = 1f).pixels
        }
        val fresh = withScene { scene -> scene.frame(timeSeconds = 2f, eyeX = 1f).pixels }

        assertContentEquals(fresh, moved, "draw blocks kept from the frame before draw as blocks packed for this camera")
    }

    @Test
    fun anAnimatedPropIsUploadedEveryFrame() = withScene { scene ->
        scene.frame(timeSeconds = 1f, wave = WAVE)
        val next = scene.frame(timeSeconds = 2f, wave = WAVE)

        assertEquals(1, next.packed, "a waving prop's clock moves it, so it is packed")
        assertEquals(1, next.staged, "and its block changes")
    }

    /**
     * The scale #1452 measured: 1,300 static draws, each with a block of its own. Planning only;
     * the counts are what a frame would upload.
     */
    @Test
    fun aMovingCameraOverThirteenHundredStaticDrawsUploadsOneBlock() = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer
        val mesh = renderer.createMesh(quad(VertexFormat.PositionNormalColor, half = 0.05f))
        val materials = List(MANY_DRAWS) { renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total) }
        val draws = materials.mapIndexed { index, material ->
            RenderDrawCommand(mesh = mesh, material = material, model = translation((index % ROW - ROW / 2) * 0.25f, 0f, (index / ROW - ROW / 2) * 0.25f))
        }
        val sun = SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f))
        val uploads = (renderer as WebGpuRenderer).uniformUploads

        fun plan(eyeX: Float): Pair<Int, Int> {
            val lens = Lens(eye = Vec3f(eyeX, 9f, 7f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)
            val packedBefore = uploads.packedBlocks
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = draws,
                light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace)),
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
            )
            return (uploads.packedBlocks - packedBefore).toInt() to uploads.buffers.size
        }
        try {
            assertEquals(MANY_DRAWS to MANY_DRAWS + FRAME_BLOCK + IDENTITY_BLOCK, plan(eyeX = 0f), "the first frame")
            assertEquals(0 to FRAME_BLOCK, plan(eyeX = 0.5f), "the camera moved: the frame block, and no draw's")
            assertEquals(0 to FRAME_BLOCK, plan(eyeX = 1f), "and again")
            assertEquals(0 to 0, plan(eyeX = 1f), "the camera held: nothing")
        } finally {
            materials.forEach { it.destroy() }
            mesh.destroy()
        }
    }

    @Test
    fun remainingSceneDrawsReuseTheirBlocksUnderAMovingCamera() = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer
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
        val uploads = (renderer as WebGpuRenderer).uniformUploads

        fun render(eyeX: Float, timeSeconds: Float): Frame {
            val lens = Lens(eye = Vec3f(eyeX, 8f, 6f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)
            val packedBefore = uploads.packedBlocks
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
            val packed = (uploads.packedBlocks - packedBefore).toInt()
            val staged = uploads.buffers.size
            renderer.renderToTexture(target, pass)
            return Frame(packed, staged, runBlocking { renderer.readPixels(target) }.data)
        }
        try {
            val first = render(eyeX = 0f, timeSeconds = 1f)
            val moved = render(eyeX = 1.5f, timeSeconds = 2f)

            assertEquals(5, first.packed, "first frame packs skinned, sprite, world text, trail and particle blocks")
            assertFalse(first.pixels.contentEquals(moved.pixels), "the scene draws from the moved camera")
            assertEquals(0, moved.packed, "none of the five shaders re-packs when only the camera and clock move")
            assertEquals(FRAME_BLOCK, moved.staged, "only the shared frame block is staged")
        } finally {
            listOf(skinnedMat, spriteMat, textMat, trailMat, particleMat).forEach { it.destroy() }
            listOf(skinnedMesh, spriteMesh, textMesh, trailMesh, particleMesh).forEach { it.destroy() }
            target.destroy()
        }
    }

    @Test
    fun terrainReusesItsContentUniformsWhileTheCameraStaysWithinASnapCell() = runBlocking {
        webGpuHeadlessScene().use { session ->
            val renderer = session.renderer
            val device = (renderer as WebGpuRenderer).graphicsDevice
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
                val (initialWrites, firstPixels) = render(eyeX = 0f)
                val (inCellWrites, movedPixels) = render(eyeX = 1f)
                val (snappedWrites, _) = render(eyeX = 5f)

                assertEquals(1, initialWrites, "the first frame writes the terrain's ring and sampling uniforms")
                assertFalse(firstPixels.contentEquals(movedPixels), "moving the camera changes the rendered terrain")
                assertEquals(0, inCellWrites, "staying within the 4-unit snap cell leaves the terrain uniform buffer untouched")
                assertEquals(1, snappedWrites, "crossing into the next snap cell writes the shifted ring origins")
            } finally {
                target.destroy()
                attached.detach()
            }
        }
    }

    private fun <T> withScene(block: (Scene) -> T): T = webGpuHeadlessScene().use { session ->
        Scene(session.renderer).use(block)
    }

    private class Frame(val packed: Int, val staged: Int, val pixels: ByteArray)

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
        private val sun = SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f))
        private val pbr = pbrMaterialFloats(metallic = 0f, roughness = 1f, baseColorFactor = Color.White, emissiveFactor = Color.Transparent)

        /** Plans and draws the scene at [timeSeconds] from [eyeX], with the lit prop at [propX] waving by [wave]. */
        fun frame(timeSeconds: Float, propX: Float = 0f, wave: Vec3f = STILL, eyeX: Float = 0f): Frame {
            // Built afresh every frame, as the scene compiler builds them, with the same values while nothing moves.
            val lens = Lens(eye = Vec3f(eyeX, 9f, 7f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)
            val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            val packedBefore = (renderer as WebGpuRenderer).uniformUploads.packedBlocks
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
            val uploads = (renderer as WebGpuRenderer).uniformUploads
            val packed = (uploads.packedBlocks - packedBefore).toInt()
            val staged = uploads.buffers.size
            renderer.renderToTexture(target, pass)
            return Frame(packed, staged, runBlocking { renderer.readPixels(target) }.data)
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
        const val MANY_DRAWS = 1_300
        const val ROW = 36

        /** The one block a batch uploads for the camera, the light and the cascades. */
        const val FRAME_BLOCK = 1

        /** The identity frame block, uploaded once in a device's life. */
        const val IDENTITY_BLOCK = 1
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
