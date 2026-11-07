/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.render.testing.HeadlessRenderSession
import org.junit.AfterClass
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [renderTiltedSpawnRingScene] on both backends, Vulkan first: a turned flat emitter's
 * `orientSpawnRing` stands its spawn ring up in the plane its sprites lie in, so the frame shows a
 * circle of sprites with an empty centre.
 *
 * Two bands measure it. Sprites above and below the middle, more than [OFF_AXIS] world units from
 * y = 0, exist only if the ring is tilted; the centre square, well inside [RING_RADIUS], holds none
 * of a ring's sprites. Measured on lavapipe at 128 px, identical on both backends: the tilted ring
 * leaves 1213 red pixels off-axis and none in the centre (1803 in all).
 *
 * Negative control, measured the same way: with the simulation's `orientSpawnShape` left unset (the
 * scene drawn with `orientSpawnRing = false`, which is what `ParticleEmitter` passes it), the ring stays
 * horizontal and is seen edge on as a band across the middle, so the off-axis bands hold 0 red pixels
 * and the centre 304 (672 in all), and both assertions fail.
 */
class ParticleSpawnRingParityTest {
    @Test
    fun aTiltedSpawnRingStandsUpAsACircle() {
        val failures = BACKEND_ORDER.flatMap { backend ->
            val frame = session(backend).renderer.renderTiltedSpawnRingScene(SCENE_SIZE)
            val offAxis = frame.redWhere { _, y -> abs(y) > OFF_AXIS }
            val centre = frame.redWhere { x, y -> abs(x) < CENTRE && abs(y) < CENTRE }
            listOfNotNull(
                "$backend: only $offAxis red pixels above and below the middle; the ring is not tilted".takeIf { offAxis < MIN_RING },
                "$backend: $centre red pixels in the ring's centre".takeIf { centre > 0 },
            )
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    // Counts red pixels whose centre, in world units (y up), satisfies [where].
    private fun ByteArray.redWhere(where: (Float, Float) -> Boolean): Int = (0 until SCENE_SIZE * SCENE_SIZE).count { pixel ->
        val x = ((pixel % SCENE_SIZE) + 0.5f) / SCENE_SIZE * 2f - 1f
        val y = 1f - ((pixel / SCENE_SIZE) + 0.5f) / SCENE_SIZE * 2f
        where(x * RING_VIEW_HALF_HEIGHT, y * RING_VIEW_HALF_HEIGHT) && isRed(pixel)
    }

    private fun ByteArray.isRed(pixel: Int): Boolean {
        val r = this[pixel * 4].toInt() and 0xFF
        val g = this[pixel * 4 + 1].toInt() and 0xFF
        val b = this[pixel * 4 + 2].toInt() and 0xFF
        return r > RED_MIN && g < OTHER_MAX && b < OTHER_MAX
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

        /** Half a ring radius from the middle: an edge-on ring's 0.25-unit sprites never reach it. */
        const val OFF_AXIS = RING_RADIUS / 2f

        /** Half the centre square's side, well inside the ring's inner edge. */
        const val CENTRE = RING_RADIUS / 2f

        /** A handful of sprites' worth: one 0.25-unit sprite covers about 64 pixels at 128 px. */
        const val MIN_RING = 400
        const val RED_MIN = 120
        const val OTHER_MAX = 80
    }
}
