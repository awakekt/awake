/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.testing.HeadlessRenderSession
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Copies of the kinds of prop a glTF town repeats, folded into one instanced draw, render and cast as
 * they do drawn one by one, on both backends: cut-out foliage, and a single-sided prop that is culled.
 * Held to the tolerance [SceneBackendParityTest.repeatedPropsDrawnAsInstancesMatchSeparateDraws] holds
 * opaque, double-sided posts to.
 */
class InstancedPropsParityTest {
    /**
     * Masked: each half-clear card draws and casts only its opaque half, folded or not.
     *
     * Negative control: drawn opaque, the same cards cover the ground behind their clear halves, so a
     * fold that dropped the cutoff differs from the masked separate draws by thousands of channels.
     */
    @Test
    fun foldedFoliageCutsOutWhatSeparateFoliageDoes() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val (separate, separateDraws) = renderer.renderRepeatedPropsScene(shareMaterial = false, masked = true)
            val (instanced, instancedDraws) = renderer.renderRepeatedPropsScene(shareMaterial = true, masked = true)
            val (opaque, _) = renderer.renderRepeatedPropsScene(shareMaterial = true)

            assertEquals(SEPARATE_DRAWS, separateDraws, "$backend: the ground and five cards")
            assertEquals(FOLDED_DRAWS, instancedDraws, "$backend did not fold the masked cards")
            assertEquals(0, separate.differingChannels(instanced), "$backend: folded masked cards differ from separate ones")
            assertTrue(separate.differingChannels(opaque) > MIN_MASK_EFFECT, "$backend: masking cut nothing out, so this compares nothing")
        }
    }

    /**
     * Back-culled: copies of a single-sided prop fold into the back-culled instanced pipeline and draw
     * as they do one by one. Facing the camera, they draw exactly as unculled ones: culling removed
     * nothing it should have kept.
     *
     * Negative control, measured on lavapipe: the same posts wound to face away from the camera
     * differ from the unculled ones in 912 channels on Vulkan.
     */
    @Test
    fun foldedBackCulledPropsDrawWhatSeparateOnesDo() {
        BACKEND_ORDER.forEach { backend ->
            val renderer = session(backend).renderer
            val (separate, separateDraws) = renderer.renderRepeatedPropsScene(shareMaterial = false, cullMode = CullMode.Back)
            val (instanced, instancedDraws) = renderer.renderRepeatedPropsScene(shareMaterial = true, cullMode = CullMode.Back)
            val (unculled, _) = renderer.renderRepeatedPropsScene(shareMaterial = true)
            writeEvidence(backend, instanced, "repeated-props-back-culled-instanced")

            assertEquals(SEPARATE_DRAWS, separateDraws, "$backend: the ground and five posts")
            assertEquals(FOLDED_DRAWS, instancedDraws, "$backend did not fold the back-culled posts")
            assertEquals(0, separate.differingChannels(instanced), "$backend: folded back-culled posts differ from separate ones")
            assertEquals(0, instanced.differingChannels(unculled), "$backend: culling hid posts that face the camera")
        }
    }

    private fun ByteArray.differingChannels(other: ByteArray): Int =
        indices.count { kotlin.math.abs((this[it].toInt() and 0xFF) - (other[it].toInt() and 0xFF)) > CHANNEL_TOLERANCE }

    private fun writeEvidence(backend: HeadlessUiBackend, pixels: ByteArray, name: String) {
        val image = java.awt.image.BufferedImage(SCENE_SIZE, SCENE_SIZE, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for (index in 0 until SCENE_SIZE * SCENE_SIZE) {
            fun channel(offset: Int) = pixels[index * 4 + offset].toInt() and 0xFF
            image.setRGB(index % SCENE_SIZE, index / SCENE_SIZE, (0xFF shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
        }
        javax.imageio.ImageIO.write(image, "png", java.io.File(java.io.File(REPORT_DIR).apply { mkdirs() }, "$name-${backend.name.lowercase()}.png"))
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
        const val SEPARATE_DRAWS = 6
        const val FOLDED_DRAWS = 2

        /** The tolerance the opaque fold is held to: rounding, never a missing or moved surface. */
        const val CHANNEL_TOLERANCE = 2

        /** Five cards' clear halves over the ground: far more channels than rounding ever moves. */
        const val MIN_MASK_EFFECT = 500
    }
}
