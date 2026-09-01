/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.parity

import io.github.awakelab.awake.render.renderer.Renderer
import io.github.awakelab.awake.render.testing.comparePixels
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The studio cube scene, pinned to committed pixels on BOTH backends.
 *
 * Every other shadow test asserts a property -- a contrast, a centroid, a speckle count -- and
 * all of them pass for any picture that keeps the shadow roughly in place. What the shadow LOOKS
 * like (edge softness, gradient, contact, cascade continuity) was pinned by nothing, so a change
 * could degrade all of it while staying green. This is the pin: exact pixels, tolerance 2 per
 * channel, zero differing pixels allowed.
 *
 * Three yaws, because [STUDIO_YAWS]' behaviour repeats with the cube's quarter-turn symmetry:
 * indices 0..2 are one full period of the speckle pattern the parity ratchet counts, so these
 * three frames cover every case the twelve do.
 *
 * Per-backend baselines: the two backends store colour differently (Vulkan's offscreen target is
 * UNORM, WebGPU's takes sRGB from the surface -- see [SceneBackendParityTest]), so one image
 * cannot serve both. Re-record with `-DAWAKE_RECORD_SNAPSHOTS=true` -- and per this repo's
 * verification rules, only after stating what should change and why.
 */
class SceneShadowBaselineTest {

    @Test
    fun theStudioCubeSceneMatchesItsBaselineOnBothBackends() {
        val record = System.getProperty("AWAKE_RECORD_SNAPSHOTS")?.toBoolean() == true
        val failures = mutableListOf<String>()
        BACKEND_ORDER.forEach { backend ->
            openHeadlessScene(backend).use { session -> checkBackend(session.renderer, backend, record, failures) }
        }
        if (record) {
            // Recording must never masquerade as a pass: fail loudly so a CI run with the flag
            // set cannot go green while silently rewriting what green means.
            fail("Recorded ${BACKEND_ORDER.size * BASELINE_YAWS.size} shadow baselines -- rerun without the flag.")
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun checkBackend(
        renderer: Renderer,
        backend: HeadlessUiBackend,
        record: Boolean,
        failures: MutableList<String>,
    ) {
        BASELINE_YAWS.forEach { yawIndex ->
            val pixels = renderer.renderStudioCubeScene(STUDIO_YAWS[yawIndex])
            val failure = checkYaw(pixels, backend, yawIndex, record)
            if (failure != null) failures += failure
        }
    }

    /** Records or compares one frame; returns a failure message, or null when it matches. */
    private fun checkYaw(pixels: ByteArray, backend: HeadlessUiBackend, yawIndex: Int, record: Boolean): String? {
        val baselineFile = baselineFile(backend, yawIndex)
        return when {
            record -> {
                baselineFile.parentFile.mkdirs()
                baselineFile.writeBytes(pixels)
                null
            }
            !baselineFile.exists() ->
                "${baselineFile.path} does not exist -- record baselines with -DAWAKE_RECORD_SNAPSHOTS=true"
            else -> compareAgainst(baselineFile, pixels, backend, yawIndex)
        }
    }

    /** null when the frame matches its baseline; a message pointing at dumped PNGs otherwise. */
    private fun compareAgainst(baselineFile: File, pixels: ByteArray, backend: HeadlessUiBackend, yawIndex: Int): String? {
        val result = comparePixels(pixels, baselineFile.readBytes())
        if (result.matches) return null
        val dir = File(FAILURE_DIR).apply { mkdirs() }
        val actual = File(dir, "${backend.name.lowercase()}-yaw$yawIndex-actual.png")
        val expected = File(dir, "${backend.name.lowercase()}-yaw$yawIndex-baseline.png")
        writePng(pixels, actual)
        writePng(baselineFile.readBytes(), expected)
        return "$backend yaw $yawIndex diverged: ${result.diffPixelCount} pixels " +
            "(max channel diff ${result.maxChannelDiff}) -- compare ${actual.path} against ${expected.path}"
    }

    private fun baselineFile(backend: HeadlessUiBackend, yawIndex: Int): File =
        File("src/desktopTest/resources/baselines/studio-cube-${backend.name.lowercase()}-yaw$yawIndex.rgba")

    private fun writePng(pixels: ByteArray, file: File) {
        val image = BufferedImage(SCENE_SIZE, SCENE_SIZE, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until SCENE_SIZE) {
            for (x in 0 until SCENE_SIZE) {
                val offset = (y * SCENE_SIZE + x) * 4
                fun channel(index: Int) = pixels[offset + index].toInt() and 0xFF
                image.setRGB(x, y, (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
            }
        }
        ImageIO.write(image, "png", file)
    }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)

        /** One full period of the cube's quarter-turn-symmetric speckle pattern. */
        val BASELINE_YAWS = listOf(0, 1, 2)

        const val FAILURE_DIR = "build/test-failures/SceneShadowBaselineTest"
    }
}
