/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.render.testing.HeadlessRenderSession
import org.junit.AfterClass
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/** The dither fade on both backends: Vulkan first, then WebGPU. */
class DitherFadeParityTest {
    /**
     * A dither-faded caster lets the ground show through about the share it fades by, keeps its
     * shadow and the rest of the frame as they were, and at opacity 1 renders exactly as before.
     */
    @Test
    fun aDitherFadedCasterShowsTheGroundThroughAndKeepsItsShadow() {
        val failures = BACKEND_ORDER.flatMap { backend ->
            val renderer = session(backend).renderer
            val before = renderer.renderShadowScene()
            val opaque = renderer.renderFadedCasterScene(fadeOpacity = 1f).also { writeEvidence(backend, it, "dither-fade-opaque") }
            val faded = renderer.renderFadedCasterScene(fadeOpacity = 0.35f).also { writeEvidence(backend, it, "dither-fade-faded") }
            val caster = (0 until SCENE_SIZE * SCENE_SIZE).filter { opaque.isCasterRed(it) }.toHashSet()
            val through = caster.count { !faded.isCasterRed(it) }
            val elsewhere = (0 until SCENE_SIZE * SCENE_SIZE).count { it !in caster && !opaque.samePixel(faded, it) }
            listOfNotNull(
                "$backend: opacity 1 differs from the unfaded scene".takeIf { !before.contentEquals(opaque) },
                "$backend: the caster covers no pixels".takeIf { caster.isEmpty() },
                "$backend: the ground shows through $through of ${caster.size} caster pixels, not about 62%"
                    .takeIf { caster.isNotEmpty() && through.toDouble() / caster.size !in FADED_SHARE },
                "$backend: $elsewhere pixels off the caster changed, its shadow among them"
                    .takeIf { elsewhere > caster.size / EDGE_SHARE_DIVISOR },
            )
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun ByteArray.isCasterRed(pixel: Int): Boolean {
        val r = this[pixel * 4].toInt() and 0xFF
        val g = this[pixel * 4 + 1].toInt() and 0xFF
        val b = this[pixel * 4 + 2].toInt() and 0xFF
        return r > CASTER_RED_MIN && g < CASTER_OTHER_MAX && b < CASTER_OTHER_MAX
    }

    private fun ByteArray.samePixel(other: ByteArray, pixel: Int): Boolean =
        (0..3).all { this[pixel * 4 + it] == other[pixel * 4 + it] }

    private fun writeEvidence(backend: HeadlessUiBackend, pixels: ByteArray, name: String) {
        val image = BufferedImage(SCENE_SIZE, SCENE_SIZE, BufferedImage.TYPE_INT_ARGB)
        for (index in 0 until SCENE_SIZE * SCENE_SIZE) {
            fun channel(offset: Int) = pixels[index * 4 + offset].toInt() and 0xFF
            image.setRGB(index % SCENE_SIZE, index / SCENE_SIZE, (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
        }
        ImageIO.write(image, "png", File(File(REPORT_DIR).apply { mkdirs() }, "$name-${backend.name.lowercase()}.png"))
    }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        private val sessions = mutableMapOf<HeadlessUiBackend, HeadlessRenderSession>()

        fun session(backend: HeadlessUiBackend): HeadlessRenderSession = sessions.getOrPut(backend) { openHeadlessScene(backend) }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            sessions.remove(HeadlessUiBackend.Vulkan)?.close()
        }

        const val REPORT_DIR = "build/reports/render-parity"
        /** 10 of every 16 pixels in the dither tile are discarded at opacity 0.35. */
        val FADED_SHARE = 0.5..0.75
        const val CASTER_RED_MIN = 80
        const val CASTER_OTHER_MAX = 60

        /** Pixels off the caster may change only along its anti-aliased edge: a few per cent of its area. */
        const val EDGE_SHARE_DIVISOR = 5
    }
}
