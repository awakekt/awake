/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaderpack.shadowMapViewContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.inverse
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The shadow-map viewer shows the depth the shadow pass actually wrote.
 *
 * A plate floats over a large ground under an angled sun. For every pixel, the expected depth is
 * worked out on the CPU from the cascade's own matrix: the nearer of the plate and the ground along
 * that light ray. The viewer must match it for the cascade it is pointed at, and must not match it
 * for another cascade or with the view off, so the check cannot pass on a picture that merely
 * looks like depth.
 */
class RendererHeadlessShadowMapViewTest {

    @Test
    fun eachLayerShowsTheDepthItsCascadeWrote() {
        val (renderer, _) = shared()
        val light = SceneLight(direction = LIGHT, color = Vec3f(1f, 1f, 1f))
        val cascades = shadowCascadeUniforms(light, CAMERA, 1f, renderer.clipSpace)
        val plateLayer = cascades.viewProjections.indexOfFirst { it.plateCoverage() > MIN_PLATE_PIXELS }
        assertTrue(plateLayer >= 0, "No cascade holds the plate, so there is nothing to find in the map.")
        val otherLayer = (plateLayer + 1) % cascades.count
        val expected = expectedDepths(cascades.viewProjections[plateLayer])

        val shown = renderer.render(light.copy(cascades = cascades), RenderDebugView.ShadowMap, plateLayer)
        val wrongLayer = renderer.render(light.copy(cascades = cascades), RenderDebugView.ShadowMap, otherLayer)
        val off = renderer.render(light.copy(cascades = cascades), RenderDebugView.Off, plateLayer)

        val misses = expected.entries.filter { (pixel, depth) -> abs(shown.grey(pixel) - depth * MAX_CHANNEL) > DEPTH_TOLERANCE }
        assertTrue(expected.size > MIN_COMPARED_PIXELS, "Only ${expected.size} pixels had a clear expected depth.")
        assertTrue(
            misses.isEmpty(),
            "${misses.size} of ${expected.size} pixels off their depth, e.g. " +
                misses.take(5).joinToString { (p, d) -> "$p: ${shown.grey(p)} vs ${d * MAX_CHANNEL}" },
        )
        assertTrue(matches(wrongLayer, expected) < expected.size / 2, "Layer $otherLayer shows layer $plateLayer's depth.")
        assertTrue(matches(off, expected) < expected.size / 2, "The lit frame already reads as this depth.")
    }

    private fun matches(frame: Frame, expected: Map<Pair<Int, Int>, Float>) =
        expected.count { (pixel, depth) -> abs(frame.grey(pixel) - depth * MAX_CHANNEL) <= DEPTH_TOLERANCE }

    private fun Renderer.render(light: SceneLight, view: RenderDebugView, layer: Int): Frame {
        val target = createRenderTarget(SIZE, SIZE)
        val ground = createMesh(quad(GROUND_HALF, 0f))
        val plate = createMesh(quad(PLATE_HALF, PLATE_HEIGHT))
        val material = createMaterial(LitShadowUniformLayout)
        try {
            renderSceneToTexture(
                target,
                CAMERA,
                listOf(RenderDrawCommand(ground, material), RenderDrawCommand(plate, material)),
                light,
                environment = EnvironmentUniforms(debugView = view, debugLayer = layer),
            )
            return Frame(runBlocking { readPixels(target) }.data)
        } finally {
            ground.destroy()
            plate.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private class Frame(private val pixels: ByteArray) {
        fun grey(pixel: Pair<Int, Int>): Float = (pixels[(pixel.second * SIZE + pixel.first) * RGBA].toInt() and 0xFF).toFloat()
    }

    /**
     * Each pixel's expected depth in the layer [viewProjection] renders: the nearer of the plate and
     * the ground along the light ray through that pixel. Pixels straddling the plate's edge, or whose
     * ground falls outside the cascade's depth range, have no single answer and are left out.
     */
    private fun expectedDepths(viewProjection: Mat4): Map<Pair<Int, Int>, Float> {
        val inverse = requireNotNull(viewProjection.inverse())
        return pixels().mapNotNull { (x, y) ->
            val ground = inverse.depthAtHeight(x + 0.5f, y + 0.5f, 0f) ?: return@mapNotNull null
            val hits = OFFSETS.map { (dx, dy) -> inverse.plateDepth(x + 0.5f + dx, y + 0.5f + dy) != null }
            if (hits.any { it } && !hits.all { it }) return@mapNotNull null
            (x to y) to (inverse.plateDepth(x + 0.5f, y + 0.5f) ?: ground)
        }.toMap()
    }

    /** How many pixels of [this] cascade's layer the plate covers. */
    private fun Mat4.plateCoverage(): Int {
        val inverse = requireNotNull(inverse())
        return pixels().count { (x, y) -> inverse.plateDepth(x + 0.5f, y + 0.5f) != null }
    }

    private fun Mat4.plateDepth(px: Float, py: Float): Float? {
        val depth = depthAtHeight(px, py, PLATE_HEIGHT) ?: return null
        val point = worldAt(px, py, depth)
        return depth.takeIf { abs(point[0]) < PLATE_HALF && abs(point[2]) < PLATE_HALF }
    }

    /** Light NDC depth where the ray through pixel ([px], [py]) crosses height [height]; null outside 0..1. */
    private fun Mat4.depthAtHeight(px: Float, py: Float, height: Float): Float? {
        val near = worldAt(px, py, 0f)[1]
        val far = worldAt(px, py, 1f)[1]
        val depth = (height - near) / (far - near)
        return depth.takeIf { it in DEPTH_MARGIN..1f - DEPTH_MARGIN }
    }

    /** The world point [this] inverse view-projection maps pixel ([px], [py]) at NDC depth [z] to. */
    private fun Mat4.worldAt(px: Float, py: Float, z: Float): FloatArray {
        // The viewer draws the layer's own NDC across the screen: Vulkan's Y-down NDC runs with rows.
        val x = px / SIZE * 2f - 1f
        val y = py / SIZE * 2f - 1f
        val clip = FloatArray(4) { row -> data[row] * x + data[row + 4] * y + data[row + 8] * z + data[row + 12] }
        return FloatArray(3) { clip[it] / clip[3] }
    }

    private fun pixels() = (0 until SIZE).flatMap { y -> (0 until SIZE).map { x -> x to y } }

    private fun quad(half: Float, height: Float) = MeshGeometry(
        floatArrayOf(
            -half, height, -half, 0f, 1f, 0f, 1f, 1f, 1f,
            half, height, -half, 0f, 1f, 0f, 1f, 1f, 1f,
            half, height, half, 0f, 1f, 0f, 1f, 1f, 1f,
            -half, height, half, 0f, 1f, 0f, 1f, 1f, 1f,
        ),
        intArrayOf(0, 1, 2, 2, 3, 0),
        VertexFormat.PositionNormalColor,
    )

    companion object {
        private const val SIZE = 128
        private const val RGBA = 4
        private const val MAX_CHANNEL = 255f
        private const val DEPTH_TOLERANCE = 3f
        private const val DEPTH_MARGIN = 0.01f
        private const val MIN_PLATE_PIXELS = 40
        private const val MIN_COMPARED_PIXELS = 4000
        private const val GROUND_HALF = 200f
        private const val PLATE_HALF = 1.5f
        private const val PLATE_HEIGHT = 1.5f
        private val LIGHT = Vec3f(1f, 1f, 0.5f)

        /** A pixel's neighbours, for telling a plate edge from a plate interior. */
        private val OFFSETS = listOf(0f to 0f, 1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)

        private val CAMERA = Lens(
            eye = Vec3f(0f, 6f, 12f),
            center = Vec3f(0f, 0f, 0f),
            fovYRadians = 1f,
            near = 0.1f,
            far = 100f,
        )

        private var cached: Pair<Renderer, () -> Unit>? = null

        private fun shared(): Pair<Renderer, () -> Unit> = cached ?: newHeadlessShadowRenderer(SIZE).also { created ->
            cached = created
            runBlocking { (created.first as ContentFeatureHost).attachContentFeature(shadowMapViewContentFeature()) }
        }

        @AfterClass
        @JvmStatic
        fun release() {
            cached?.second?.invoke()
            cached = null
        }
    }
}
