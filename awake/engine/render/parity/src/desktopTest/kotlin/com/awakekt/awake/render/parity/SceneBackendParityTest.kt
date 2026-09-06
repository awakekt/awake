/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A lit, shadowed scene through both backends, compared as a picture.
 *
 * The harness's UI scenarios caught two divergences the day they were written. This is the case
 * they could not reach: WebGPU sampled the shadow map along the wrong V axis for as long as the
 * shader existed, so every caster's shadow fell on the opposite side of the ground from the caster.
 * Vulkan's rendering of this same scene has had coverage the whole time; nothing compared them, and
 * a mirrored shadow is a perfectly plausible-looking picture on its own.
 *
 * Compares where the shadow *is*, not the exact shading. The two backends resolve a PBR fragment to
 * slightly different values, and holding them to that would be a test about floating point rather
 * than about geometry.
 */
class SceneBackendParityTest {

    @Test
    fun theShadowLandsInTheSamePlaceOnBothBackends() {
        val centroids = BACKEND_ORDER.associateWith { backend ->
            openHeadlessScene(backend).use { session ->
                val pixels = session.renderer.renderShadowScene().also { write(backend, it) }
                pixels.shadowCentroid()
                    ?: error("$backend drew no shadow at all -- see $REPORT_DIR")
            }
        }

        val vulkan = centroids.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = centroids.getValue(HeadlessUiBackend.WebGpu)
        val drift = maxOf(kotlin.math.abs(vulkan.first - webGpu.first), kotlin.math.abs(vulkan.second - webGpu.second))

        assertTrue(
            drift <= CENTROID_TOLERANCE,
            "the backends put the shadow in different places -- see $REPORT_DIR. " +
                "Vulkan centroid $vulkan, WebGPU centroid $webGpu, ${drift}px apart",
        )
    }

    /**
     * The middle of the shadowed region of the ground, or null if nothing is shadowed.
     *
     * A centroid rather than a side-of-the-frame reading. The first version of this test compared
     * brightness left and right of centre and passed with the shadow lookup deliberately broken:
     * mirroring the map's V axis moves the shadow along world Z, toward or away from the camera,
     * which a left/right reading cannot see. Both axes, or the test only covers half the bug.
     *
     * The lit level is measured per capture rather than fixed, because the two backends store
     * colour differently -- Vulkan's offscreen target is UNORM and WebGPU's takes its sRGB format
     * from the surface -- so the same ground reads 116 on one and 180 on the other. The shadow is
     * the same 581 pixels either way; only the numbers written into them differ.
     */
    private fun ByteArray.shadowCentroid(): Pair<Int, Int>? {
        val ground = (GROUND_TOP..GROUND_BOTTOM).flatMap { y ->
            (0 until SCENE_SIZE).map { x -> x to y }
        }.filter { (x, y) -> luminanceAt(x, y) > 0 }
        // The ground is most of the frame, so its lit value is the most common one in it.
        val lit = ground.groupingBy { (x, y) -> luminanceAt(x, y) }.eachCount().maxByOrNull { it.value }?.key
        val shadowed = ground.filter { (x, y) -> lit != null && luminanceAt(x, y) < lit - SHADOW_MARGIN }
        return shadowed
            .takeIf { it.isNotEmpty() }
            ?.let { it.sumOf { (x, _) -> x } / it.size to it.sumOf { (_, y) -> y } / it.size }
    }

    private fun write(backend: HeadlessUiBackend, pixels: ByteArray) {
        val image = BufferedImage(SCENE_SIZE, SCENE_SIZE, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until SCENE_SIZE) {
            for (x in 0 until SCENE_SIZE) {
                val offset = (y * SCENE_SIZE + x) * 4
                fun channel(index: Int) = pixels[offset + index].toInt() and 0xFF
                image.setRGB(
                    x,
                    y,
                    (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2),
                )
            }
        }
        val out = File(REPORT_DIR).apply { mkdirs() }
        ImageIO.write(image, "png", File(out, "shadow-scene-${backend.name.lowercase()}.png"))
    }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)

        const val REPORT_DIR = "build/reports/render-parity"

        /** The band the ground occupies in this framing, measured off the captures. */
        const val GROUND_TOP = 45
        const val GROUND_BOTTOM = 110

        /** How far below the lit level a pixel must fall to count as shadowed. */
        const val SHADOW_MARGIN = 10

        /** Antialiasing and PBR rounding move a centroid by a pixel; a mirrored lookup moves it
         * across the ground. */
        const val CENTROID_TOLERANCE = 3
    }
}
