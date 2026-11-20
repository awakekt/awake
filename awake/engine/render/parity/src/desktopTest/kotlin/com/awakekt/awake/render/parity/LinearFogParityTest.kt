/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Linear distance fog ramps from its start to its end by distance from the eye, through both
 * lit shaders the scene's fog reaches (`textured` and `lit_shadow`), on both backends (#1807).
 *
 * The centre pixel sees the plane's centre, [CENTRE_DISTANCE] metres from the eye. A range that
 * starts past it leaves the pixel as it is without fog; a range centred on it covers it by half,
 * which must draw what an exponential fog of the same coverage, `ln 2 / distance`, draws; a range
 * that ends before it draws the fog colour, black here.
 *
 * Negative control, before the shaders read the range: every linear case drew the unfogged pixel,
 * since the frame had no density, so the midpoint and end checks failed on both backends.
 */
class LinearFogParityTest {

    @Test
    fun vulkanTexturedLinearFogRamps() = verify(HeadlessUiBackend.Vulkan, textured = true)

    @Test
    fun webGpuTexturedLinearFogRamps() = verify(HeadlessUiBackend.WebGpu, textured = true)

    @Test
    fun vulkanLitShadowLinearFogRamps() = verify(HeadlessUiBackend.Vulkan, textured = false)

    @Test
    fun webGpuLitShadowLinearFogRamps() = verify(HeadlessUiBackend.WebGpu, textured = false)

    private fun verify(backend: HeadlessUiBackend, textured: Boolean) = openHeadlessScene(backend).use { session ->
        val renderer = session.renderer
        fun centre(environment: EnvironmentUniforms): List<Int> {
            val pixels = if (textured) renderer.renderTexturedPbrScene(environment) else renderer.renderLitPlane(environment)
            val index = ((SCENE_SIZE / 2) * SCENE_SIZE + SCENE_SIZE / 2) * 4
            return (0..2).map { pixels[index + it].toInt() and 0xFF }
        }
        val clear = centre(BLACK_FOG)
        val beforeStart = centre(linear(CENTRE_DISTANCE + 1f, CENTRE_DISTANCE + RAMP))
        val midway = centre(linear(CENTRE_DISTANCE - RAMP / 2, CENTRE_DISTANCE + RAMP / 2))
        val halfExponential = centre(BLACK_FOG.copy(fogDensity = ln(2f) / CENTRE_DISTANCE))
        val pastEnd = centre(linear(CENTRE_DISTANCE - RAMP, CENTRE_DISTANCE - 1f))
        val measured = "clear $clear, before start $beforeStart, midway $midway, half exponential $halfExponential, past end $pastEnd"
        println("$backend ${if (textured) "textured" else "lit_shadow"}: $measured")

        assertTrue(clear.max() > MIN_LIT, "$backend: the plane drew nothing to fog: $measured")
        assertTrue(beforeStart.near(clear), "$backend: fog reached a point nearer than its start: $measured")
        assertTrue(midway.near(halfExponential), "$backend: the ramp's midpoint is not half fogged: $measured")
        assertTrue(midway.max() < clear.max() - MIN_LIT / 2, "$backend: the ramp's midpoint is not fogged: $measured")
        assertTrue(pastEnd.all { it <= TOLERANCE }, "$backend: a point past the end is not the fog colour: $measured")
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

    private companion object {
        val CENTRE_DISTANCE = sqrt(EYE_Y * EYE_Y + EYE_Z * EYE_Z)
        const val RAMP = 8f
        const val TOLERANCE = 2
        const val MIN_LIT = 60
        val BLACK_FOG = EnvironmentUniforms.Default.copy(fogColor = Color.Black, shadowsEnabled = false)
    }
}
