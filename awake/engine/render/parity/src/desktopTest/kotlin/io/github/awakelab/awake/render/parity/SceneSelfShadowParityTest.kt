/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.parity

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Whether a lit surface samples its own depth, counted as pixels, on both backends at once.
 *
 * [SceneBackendParityTest] asks where the shadow *lands*; this asks whether the surface it lands on
 * is speckled. The two need different scenes, which is the point of keeping them apart: a
 * single-sided plane under one `directionalShadowBox` -- the scene that catches a mirrored lookup
 * -- scores zero here however wrong the bias is, because nothing is ever in the shadow map at its
 * own depth. Showing speckle needs the closed cube and cascaded fit [renderStudioCubeScene] builds.
 *
 * Reported to the studio as a stipple on the rotating cube's lit faces, and initially assumed to be
 * a WebGPU fault. It is not: both backends count it, and the cause was the slope term in
 * `AslShadowShaders` approximating `tan(acos(n))` as `(1 - n)/n`, which is 4.4x short at n = 0.9
 * and converges only at grazing angles -- so it was weakest exactly where a face points at the
 * light, and a spinning cube sweeps its lit faces through that band.
 *
 * Captures land in `build/reports/render-parity` whether the test passes or fails; the residual is
 * only a few dozen pixels and is far easier to read as a picture than as a number.
 */
class SceneSelfShadowParityTest {

    @Test
    fun neitherBackendSpecklesTheStudiosCube() {
        val counts = BACKEND_ORDER.associateWith { backend ->
            openHeadlessScene(backend).use { session ->
                STUDIO_YAWS.mapIndexed { index, yaw ->
                    val pixels = session.renderer.renderStudioCubeScene(yaw)
                    val speckled = pixels.selfShadowedPixels()
                    if (speckled > MAX_SELF_SHADOWED) write(backend, index, pixels)
                    speckled
                }
            }
        }

        // Printed on a pass too: the residual this is ratcheted against is a number worth seeing
        // move, and a threshold that only speaks when it trips hides which way it is drifting.
        counts.forEach { (backend, perYaw) -> println("SELF-SHADOW $backend $perYaw") }
        val worst = counts.mapValues { (_, perYaw) -> perYaw.max() }
        assertTrue(
            worst.values.all { it <= MAX_SELF_SHADOWED },
            "a lit surface is sampling its own depth -- see $REPORT_DIR. Per yaw: " +
                counts.entries.joinToString("; ") { (backend, perYaw) -> "$backend $perYaw" } +
                ". Positive control, 2026-09-01: zeroing SHADOW_BIAS_TEXELS, " +
                "SHADOW_SLOPE_BIAS_TEXELS and SHADOW_NORMAL_OFFSET_TEXELS scores a worst yaw of " +
                "13 on both backends (the depth pass's rasterizer bias absorbs the rest), so " +
                "this probe still fires above the ratchet when the receiver bias is gone.",
        )
    }

    private fun write(backend: HeadlessUiBackend, yawIndex: Int, pixels: ByteArray) {
        val image = BufferedImage(SCENE_SIZE, SCENE_SIZE, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until SCENE_SIZE) {
            for (x in 0 until SCENE_SIZE) {
                val offset = (y * SCENE_SIZE + x) * 4
                fun channel(index: Int) = pixels[offset + index].toInt() and 0xFF
                image.setRGB(x, y, (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
            }
        }
        val out = File(REPORT_DIR).apply { mkdirs() }
        ImageIO.write(image, "png", File(out, "self-shadow-${backend.name.lowercase()}-yaw$yawIndex.png"))
    }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)

        const val REPORT_DIR = "build/reports/render-parity"

        /**
         * A RATCHET, not a target. 4 is 2x the worst yaw measured after the 2026-09-01 fix pair
         * (both backends read `[0, 2, 1]` per quarter turn or better), down from the 32 the
         * receiver-only bias era needed.
         *
         * Held here so a regression fails immediately rather than sliding under a loose bound.
         * Lower it, never raise it: raising this to make a red run green is the same move as
         * re-recording a baseline to make a diff go away.
         *
         * What got it here, in order of what mattered:
         *
         * - **A hardware comparison sampler with LINEAR filtering.** `textureSampleCompareLevel`
         *   on a `sampler_comparison` compares each texel BEFORE the bilinear blend, so a tap
         *   near the lit/shadowed tie averages instead of flipping. NEAREST hardware compare is
         *   bit-identical to the manual `select` loop it replaced -- the tie-resolution theory
         *   this KDoc used to end on was measured dead; the FILTER is the whole effect.
         * - **Rasterizer depth bias in the depth pass** (`SHADOW_DEPTH_BIAS_CONSTANT`/`_SLOPE`).
         *   The residual after linear compare was a per-texel waffle on the cube's tilted face,
         *   identical in the PNGs on both backends, immune to every receiver-side constant --
         *   receiver bias estimates slope from nDotL, but the error is the MAP polygon's slope,
         *   which only the rasterizer knows per primitive. Source bias removed it at every yaw
         *   and let the receiver constants drop to 1.5/2/offset 1.
         *
         * The old "WebGPU runs higher than Vulkan here" note was an artifact of the metric, not
         * the rendering: Vulkan's offscreen target is UNORM and WebGPU's is sRGB, so the same
         * waffle clears the count's delta threshold on one backend and not the other. Compare
         * the PNGs before believing a backend gap in these counts.
         */
        const val MAX_SELF_SHADOWED = 4
    }
}
