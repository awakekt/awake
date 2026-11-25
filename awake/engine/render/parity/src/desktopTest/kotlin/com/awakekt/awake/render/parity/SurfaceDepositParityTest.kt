/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.TexturedUniformLayout
import com.awakekt.awake.asset.shaderpack.terrainContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.passes.uniforms.skinnedMaterialFloats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Dynamic upward-facing surface deposit parity across forward shaders (`textured`,
 * `skinned_textured` unlit & lit, `lit_shadow`, and `terrain`) on Vulkan and WebGPU.
 *
 * Negative controls:
 * - `surfaceDepositAmount = 0f` leaves dark surfaces dark (baseline).
 * - Vertical surfaces (`normal = (0, 0, 1)`) stay uncoated even at `surfaceDepositAmount = 1f`.
 * - Draws with `surfaceDepositScale = 0f` stay uncoated even at `surfaceDepositAmount = 1f`.
 */
class SurfaceDepositParityTest {

    @Test
    fun vulkanTexturedSurfaceDepositCoatsUpwardOnlyAndHonorsMask() =
        verifyMesh(HeadlessUiBackend.Vulkan, Surface.TexturedLit)

    @Test
    fun webGpuTexturedSurfaceDepositCoatsUpwardOnlyAndHonorsMask() =
        verifyMesh(HeadlessUiBackend.WebGpu, Surface.TexturedLit)

    @Test
    fun vulkanSkinnedUnlitSurfaceDepositCoatsUpwardOnlyAndHonorsMask() =
        verifyMesh(HeadlessUiBackend.Vulkan, Surface.SkinnedUnlit)

    @Test
    fun webGpuSkinnedUnlitSurfaceDepositCoatsUpwardOnlyAndHonorsMask() =
        verifyMesh(HeadlessUiBackend.WebGpu, Surface.SkinnedUnlit)

    @Test
    fun vulkanSkinnedLitSurfaceDepositCoatsUpwardOnlyAndHonorsMask() =
        verifyMesh(HeadlessUiBackend.Vulkan, Surface.SkinnedLit)

    @Test
    fun webGpuSkinnedLitSurfaceDepositCoatsUpwardOnlyAndHonorsMask() =
        verifyMesh(HeadlessUiBackend.WebGpu, Surface.SkinnedLit)

    @Test
    fun vulkanLitShadowSurfaceDepositCoatsUpwardOnly() =
        verifyMesh(HeadlessUiBackend.Vulkan, Surface.LitShadow)

    @Test
    fun webGpuLitShadowSurfaceDepositCoatsUpwardOnly() =
        verifyMesh(HeadlessUiBackend.WebGpu, Surface.LitShadow)

    @Test
    fun vulkanTerrainSurfaceDepositBrightensFlatGround() =
        verifyTerrain(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuTerrainSurfaceDepositBrightensFlatGround() =
        verifyTerrain(HeadlessUiBackend.WebGpu)

    @Test
    fun vulkanSurfaceDepositTextureVariesAndSeedShiftsSpatialPattern() =
        verifyTextureAndSeed(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuSurfaceDepositTextureVariesAndSeedShiftsSpatialPattern() =
        verifyTextureAndSeed(HeadlessUiBackend.WebGpu)

    private enum class Surface { TexturedLit, SkinnedUnlit, SkinnedLit, LitShadow }

    private fun verifyTextureAndSeed(backend: HeadlessUiBackend) = openHeadlessScene(backend).use { session ->
        val renderer = session.renderer
        val partialA = renderer.renderQuad(Surface.TexturedLit, PARTIAL_SEED_A, upward = true, depositScale = 1f)
        val partialB = renderer.renderQuad(Surface.TexturedLit, PARTIAL_SEED_B, upward = true, depositScale = 1f)
        val spread = centerLumaSpread(partialA)
        val seedDiff = meanAbsRgbDiff(partialA, partialB)
        println("$backend SeedTexture: spread=$spread seedDiff=$seedDiff")
        assertTrue(spread >= MIN_TEXTURE_SPREAD, "$backend coated surface looked flat (luma spread=$spread)")
        assertTrue(seedDiff >= MIN_SEED_DIFF, "$backend changing surfaceDepositSeed did not randomize pattern (diff=$seedDiff)")
    }

    private fun verifyMesh(backend: HeadlessUiBackend, surface: Surface) = openHeadlessScene(backend).use { session ->
        val renderer = session.renderer
        val clear = meanCenterRgb(renderer.renderQuad(surface, CLEAR_ENV, upward = true, depositScale = 1f))
        val coatedUp = meanCenterRgb(renderer.renderQuad(surface, SNOW_ENV, upward = true, depositScale = 1f))
        val maskedOut = meanCenterRgb(renderer.renderQuad(surface, SNOW_ENV, upward = true, depositScale = 0f))
        val vertical = meanCenterRgb(renderer.renderQuad(surface, SNOW_ENV, upward = false, depositScale = 1f))
        val verticalClear = meanCenterRgb(renderer.renderQuad(surface, CLEAR_ENV, upward = false, depositScale = 1f))
        val summary = "clear=$clear coatedUp=$coatedUp maskedOut=$maskedOut vertical=$vertical verticalClear=$verticalClear"
        println("$backend $surface: $summary")

        assertTrue(coatedUp[0] > clear[0] + MIN_DEPOSIT_GAIN, "$backend $surface: upward surface did not brighten with snow deposit ($summary)")
        assertTrue(coatedUp[1] > clear[1] + MIN_DEPOSIT_GAIN, "$backend $surface: upward surface green channel did not brighten ($summary)")
        assertTrue(maskedOut.near(clear), "$backend $surface: surfaceDepositScale=0 did not exempt the draw ($summary)")
        assertTrue(vertical.near(verticalClear), "$backend $surface: vertical wall must not collect upward deposit ($summary)")
    }

    private fun verifyTerrain(backend: HeadlessUiBackend) = openHeadlessScene(backend).use { session ->
        val renderer = session.renderer
        val land = Heightmap(FloatArray(17 * 17), 17, 17, Vec3f(1f, 1f, 1f))
        val layout = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f)
        val feature = runBlocking { (renderer as ContentFeatureHost).attachContentFeature(terrainContentFeature(PackShaderSets.Terrain, land, layout)) }
        try {
            val clear = meanCenterRgb(renderer.renderTerrain(CLEAR_ENV))
            val coated = meanCenterRgb(renderer.renderTerrain(SNOW_ENV))
            val summary = "clear=$clear coated=$coated"
            println("$backend Terrain: $summary")
            assertTrue(coated[0] > clear[0] + MIN_TERRAIN_GAIN, "$backend Terrain: flat ground did not brighten under snow deposit ($summary)")
        } finally {
            feature.detach()
        }
    }

    private fun Renderer.renderQuad(
        surface: Surface,
        environment: EnvironmentUniforms,
        upward: Boolean,
        depositScale: Float,
    ): ByteArray {
        val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        val mesh = createMesh(
            when (surface) {
                Surface.TexturedLit -> texturedQuad(upward)
                Surface.SkinnedUnlit, Surface.SkinnedLit -> skinnedQuad(upward)
                Surface.LitShadow -> litQuad(upward)
            },
        )
        val material = when (surface) {
            Surface.TexturedLit -> createMaterial(TexturedUniformLayout, texture = DARK_BLUE_TEX)
            Surface.SkinnedUnlit, Surface.SkinnedLit -> createMaterial(SkinnedUniformLayout, texture = DARK_BLUE_TEX)
            Surface.LitShadow -> createMaterial(LitShadowUniformLayout)
        }
        val extras = when (surface) {
            Surface.TexturedLit -> pbrMaterialFloats(0f, 0.8f, Color.White, Color.Transparent)
            Surface.SkinnedUnlit -> skinnedMaterialFloats(IDENTITY_JOINT, Color.White, Color.Transparent, unlit = true)
            Surface.SkinnedLit -> skinnedMaterialFloats(IDENTITY_JOINT, Color.White, Color.Transparent, unlit = false)
            Surface.LitShadow -> floatArrayOf(0f, 0.8f, 0f, 0f)
        }
        val lens = if (upward) {
            Lens(eye = Vec3f(0f, 3.2f, 2.2f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 40f)
        } else {
            Lens(eye = Vec3f(0f, 0f, 3.6f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 40f)
        }
        val lightDir = if (upward) Vec3f(0.1f, 1f, 0.2f) else Vec3f(0.1f, 0.2f, 1f)
        val draw = RenderDrawCommand(mesh, material, extraUniformFloats = extras).apply {
            surfaceDepositScale = depositScale
        }
        return try {
            renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = lens,
                    drawCalls = listOf(draw),
                    light = SceneLight(direction = lightDir, color = Vec3f(1f, 1f, 1f)),
                    environment = environment,
                    clipSpace = clipSpace,
                    aspect = 1f,
                    drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
                ),
            )
            runBlocking { readPixels(target) }.data
        } finally {
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private fun Renderer.renderTerrain(environment: EnvironmentUniforms): ByteArray {
        val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        return try {
            renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = Lens(eye = Vec3f(0f, 4f, 4f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f),
                    drawCalls = emptyList(),
                    light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
                    environment = environment,
                    clipSpace = clipSpace,
                    aspect = 1f,
                    drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
                ),
            )
            runBlocking { readPixels(target) }.data
        } finally {
            target.destroy()
        }
    }

    private fun meanCenterRgb(pixels: ByteArray): List<Int> {
        val half = SCENE_SIZE / 2
        val sums = IntArray(3)
        var count = 0
        for (y in (half - 4)..(half + 4)) {
            for (x in (half - 4)..(half + 4)) {
                val base = (y * SCENE_SIZE + x) * 4
                sums[0] += pixels[base].toInt() and 0xFF
                sums[1] += pixels[base + 1].toInt() and 0xFF
                sums[2] += pixels[base + 2].toInt() and 0xFF
                count++
            }
        }
        return listOf(sums[0] / count, sums[1] / count, sums[2] / count)
    }

    private fun centerLumaSpread(pixels: ByteArray): Int {
        val half = SCENE_SIZE / 2
        var minL = 255
        var maxL = 0
        for (y in (half - 16)..(half + 16)) {
            for (x in (half - 16)..(half + 16)) {
                val base = (y * SCENE_SIZE + x) * 4
                val luma = ((pixels[base].toInt() and 0xFF) + (pixels[base + 1].toInt() and 0xFF) + (pixels[base + 2].toInt() and 0xFF)) / 3
                if (luma < minL) minL = luma
                if (luma > maxL) maxL = luma
            }
        }
        return maxL - minL
    }

    private fun meanAbsRgbDiff(a: ByteArray, b: ByteArray): Float {
        val half = SCENE_SIZE / 2
        var sum = 0
        var count = 0
        for (y in (half - 16)..(half + 16)) {
            for (x in (half - 16)..(half + 16)) {
                val base = (y * SCENE_SIZE + x) * 4
                for (c in 0..2) {
                    sum += abs((a[base + c].toInt() and 0xFF) - (b[base + c].toInt() and 0xFF))
                }
                count += 3
            }
        }
        return sum.toFloat() / count
    }

    private fun List<Int>.near(other: List<Int>): Boolean = indices.all { abs(this[it] - other[it]) <= TOLERANCE }

    private companion object {
        const val HALF = 2.5f
        const val MIN_DEPOSIT_GAIN = 85
        const val MIN_TERRAIN_GAIN = 35
        const val MIN_TEXTURE_SPREAD = 18
        const val MIN_SEED_DIFF = 8f
        const val TOLERANCE = 4
        val CLEAR_ENV = EnvironmentUniforms.Default.copy(shadowsEnabled = false)
        val SNOW_ENV = EnvironmentUniforms.Default.copy(
            shadowsEnabled = false,
            surfaceDepositColor = Color(0.95f, 0.98f, 1.0f, 1f),
            surfaceDepositSecondaryColor = Color(0.74f, 0.83f, 0.94f, 1f),
            surfaceDepositAmount = 1f,
            surfaceDepositSlope = 0.55f,
            surfaceDepositSoftness = 0.22f,
            surfaceDepositRoughness = 0.92f,
            surfaceDepositNoiseScale = 1.6f,
            surfaceDepositGlint = 0.30f,
            surfaceDepositGrain = 0.60f,
        )
        val PARTIAL_SEED_A = SNOW_ENV.copy(surfaceDepositAmount = 0.58f, surfaceDepositSeed = 1.0f)
        val PARTIAL_SEED_B = SNOW_ENV.copy(surfaceDepositAmount = 0.58f, surfaceDepositSeed = 73.5f)
        val IDENTITY_JOINT = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val DARK_BLUE_TEX = TextureAsset(byteArrayOf(0x18, 0x22, 0x38, 0xFF.toByte()), 1, 1)

        fun corners(upward: Boolean): List<Triple<Float, Float, Float>> = if (upward) {
            listOf(Triple(-HALF, 0f, -HALF), Triple(HALF, 0f, -HALF), Triple(HALF, 0f, HALF), Triple(-HALF, 0f, HALF))
        } else {
            listOf(Triple(-HALF, -HALF, 0f), Triple(HALF, -HALF, 0f), Triple(HALF, HALF, 0f), Triple(-HALF, HALF, 0f))
        }

        fun normalFor(upward: Boolean): Triple<Float, Float, Float> = if (upward) Triple(0f, 1f, 0f) else Triple(0f, 0f, 1f)

        fun texturedQuad(upward: Boolean): MeshGeometry {
            val (nx, ny, nz) = normalFor(upward)
            val uvs = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
            val verts = corners(upward).zip(uvs).flatMap { (p, uv) ->
                listOf(p.first, p.second, p.third, nx, ny, nz, 1f, 1f, 1f, uv.first, uv.second)
            }
            return MeshGeometry(verts.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColorUv)
        }

        fun skinnedQuad(upward: Boolean): MeshGeometry {
            val (nx, ny, nz) = normalFor(upward)
            val uvs = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
            val verts = corners(upward).zip(uvs).flatMap { (p, uv) ->
                listOf(p.first, p.second, p.third, nx, ny, nz, 1f, 1f, 1f, uv.first, uv.second, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
            }
            return MeshGeometry(verts.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColorUvSkin)
        }

        fun litQuad(upward: Boolean): MeshGeometry {
            val (nx, ny, nz) = normalFor(upward)
            val verts = corners(upward).flatMap { p ->
                listOf(p.first, p.second, p.third, nx, ny, nz, 0.08f, 0.12f, 0.22f)
            }
            return MeshGeometry(verts.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColor)
        }
    }
}
