/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaderpack.PackShaderSets
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
import com.awakekt.awake.render.passes.uniforms.skinnedMaterialFloats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Linear distance fog ramps from its start to its end by distance from the eye, across every
 * forward scene shader (`textured`, `lit_shadow`, `terrain`, and `skinned_textured`), on both
 * backends (#1807).
 *
 * The centre pixel sees the surface's centre, [CENTRE_DISTANCE] metres from the eye. A range that
 * starts past it leaves the pixel as it is without fog; a range centred on it covers it by half,
 * which must draw what an exponential fog of the same coverage, `ln 2 / distance`, draws; a range
 * that ends before it draws the fog colour, black here.
 */
class LinearFogParityTest {

    @Test
    fun vulkanTexturedLinearFogRamps() = verify(HeadlessUiBackend.Vulkan, Surface.Textured)

    @Test
    fun webGpuTexturedLinearFogRamps() = verify(HeadlessUiBackend.WebGpu, Surface.Textured)

    @Test
    fun vulkanLitShadowLinearFogRamps() = verify(HeadlessUiBackend.Vulkan, Surface.LitShadow)

    @Test
    fun webGpuLitShadowLinearFogRamps() = verify(HeadlessUiBackend.WebGpu, Surface.LitShadow)

    @Test
    fun vulkanTerrainLinearFogRamps() = verify(HeadlessUiBackend.Vulkan, Surface.Terrain)

    @Test
    fun webGpuTerrainLinearFogRamps() = verify(HeadlessUiBackend.WebGpu, Surface.Terrain)

    @Test
    fun vulkanSkinnedTexturedLinearFogRamps() = verify(HeadlessUiBackend.Vulkan, Surface.SkinnedTextured)

    @Test
    fun webGpuSkinnedTexturedLinearFogRamps() = verify(HeadlessUiBackend.WebGpu, Surface.SkinnedTextured)

    private enum class Surface { Textured, LitShadow, Terrain, SkinnedTextured }

    private fun verify(backend: HeadlessUiBackend, surface: Surface) = openHeadlessScene(backend).use { session ->
        val renderer = session.renderer
        val terrainFeature = if (surface == Surface.Terrain) {
            val land = Heightmap(FloatArray(17 * 17), 17, 17, Vec3f(1f, 1f, 1f))
            val layout = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f)
            runBlocking { (renderer as ContentFeatureHost).attachContentFeature(terrainContentFeature(PackShaderSets.Terrain, land, layout)) }
        } else {
            null
        }
        try {
            fun centre(environment: EnvironmentUniforms): List<Int> {
                val pixels = when (surface) {
                    Surface.Textured -> renderer.renderTexturedPbrScene(environment)
                    Surface.LitShadow -> renderer.renderLitPlane(environment)
                    Surface.Terrain -> renderer.renderTerrain(environment)
                    Surface.SkinnedTextured -> renderer.renderSkinnedPlane(environment)
                }
                val index = ((SCENE_SIZE / 2) * SCENE_SIZE + SCENE_SIZE / 2) * 4
                return (0..2).map { pixels[index + it].toInt() and 0xFF }
            }
            val clear = centre(BLACK_FOG)
            val beforeStart = centre(linear(CENTRE_DISTANCE + 1f, CENTRE_DISTANCE + RAMP))
            val midway = centre(linear(CENTRE_DISTANCE - RAMP / 2, CENTRE_DISTANCE + RAMP / 2))
            val halfExponential = centre(BLACK_FOG.copy(fogDensity = ln(2f) / CENTRE_DISTANCE))
            val pastEnd = centre(linear(CENTRE_DISTANCE - RAMP, CENTRE_DISTANCE - 1f))
            val measured = "clear $clear, before start $beforeStart, midway $midway, half exponential $halfExponential, past end $pastEnd"
            println("$backend $surface: $measured")

            assertTrue(clear.max() > MIN_LIT, "$backend $surface: the surface drew nothing to fog: $measured")
            assertTrue(beforeStart.near(clear), "$backend $surface: fog reached a point nearer than its start: $measured")
            assertTrue(midway.near(halfExponential), "$backend $surface: the ramp's midpoint is not half fogged: $measured")
            assertTrue(midway.max() < clear.max() - MIN_LIT / 2, "$backend $surface: the ramp's midpoint is not fogged: $measured")
            assertTrue(pastEnd.all { it <= TOLERANCE }, "$backend $surface: a point past the end is not the fog colour: $measured")
        } finally {
            terrainFeature?.detach()
        }
    }

    private fun List<Int>.near(other: List<Int>) = indices.all { abs(this[it] - other[it]) <= TOLERANCE }

    private fun linear(start: Float, end: Float) = BLACK_FOG.copy(fogStart = start, fogEnd = end)

    /** A white plane under an overhead sun through `lit_shadow`, the textured scene's framing. */
    private fun Renderer.renderLitPlane(environment: EnvironmentUniforms): ByteArray {
        val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        val mesh = createMesh(plane(GROUND_HALF, y = 0f))
        val material = createMaterial(LitShadowUniformLayout)
        return try {
            renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f),
                    drawCalls = listOf(RenderDrawCommand(mesh, material)),
                    light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
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

    /** Flat clipmap terrain under an overhead sun, matching the plane framing. */
    private fun Renderer.renderTerrain(environment: EnvironmentUniforms): ByteArray {
        val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        return try {
            renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f),
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

    /** A textured skinned quad under an overhead sun through `skinned_textured`, matching the plane framing. */
    private fun Renderer.renderSkinnedPlane(environment: EnvironmentUniforms): ByteArray {
        val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        val mesh = createMesh(skinnedPlane(GROUND_HALF))
        val material = createMaterial(SkinnedUniformLayout, texture = WHITE_TEX)
        val extras = skinnedMaterialFloats(IDENTITY_JOINT, Color.White, Color.Transparent, unlit = false)
        return try {
            renderToTexture(
                target,
                ScenePassCompiler.compile(
                    lens = Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f),
                    drawCalls = listOf(RenderDrawCommand(mesh, material, extraUniformFloats = extras)),
                    light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
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

    private companion object {
        val CENTRE_DISTANCE = sqrt(EYE_Y * EYE_Y + EYE_Z * EYE_Z)
        const val RAMP = 8f
        const val TOLERANCE = 2
        const val MIN_LIT = 60
        val BLACK_FOG = EnvironmentUniforms.Default.copy(fogColor = Color.Black, shadowsEnabled = false)
        val IDENTITY_JOINT = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val WHITE_TEX = TextureAsset(ByteArray(4) { 0xFF.toByte() }, 1, 1)

        fun skinnedPlane(half: Float): MeshGeometry {
            val corners = listOf(-half to -half, half to -half, half to half, -half to half)
            val uvs = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
            val vertices = corners.zip(uvs).flatMap { (c, uv) ->
                listOf(c.first, 0f, c.second, 0f, 1f, 0f, 1f, 1f, 1f, uv.first, uv.second, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
            }
            return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColorUvSkin)
        }
    }
}
