/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.particles.ParticleFacing
import com.awakekt.awake.render.testing.HeadlessRenderSession
import org.junit.AfterClass
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [renderUprightSpawnRingScene] on both backends, Vulkan first: an upright emitter's sprites stand in
 * its entity's XY plane, perpendicular to a spawn ring in the entity's XZ plane, with the texture's top
 * toward the entity's +Y, and a rolled entity turns the ring and the sprites together.
 *
 * Unturned, the sprites face the camera along a horizontal band (the ring seen edge on), red (the
 * texture's top) above green. Rolled a quarter about +Z, the band is vertical and red lies toward the
 * world's -X, the entity's +Y. A [ParticleFacing.Flat] emitter on the unturned entity lays its sprites
 * edge on to the camera and draws nothing, so the band is the facing's doing.
 */
class ParticleUprightParityTest {
    @Test
    fun uprightSpritesStandPerpendicularToTheirRingAndTurnWithIt() {
        val failures = BACKEND_ORDER.flatMap { backend ->
            val renderer = session(backend).renderer
            val level = renderer.renderUprightSpawnRingScene(SCENE_SIZE).tones()
            val rolled = renderer.renderUprightSpawnRingScene(SCENE_SIZE, rolled = true).tones()
            val flat = renderer.renderUprightSpawnRingScene(SCENE_SIZE, facing = ParticleFacing.Flat).tones()
            listOfNotNull(
                "$backend: only ${level.count} sprite pixels unturned".takeIf { level.count < MIN_BAND },
                "$backend: unturned sprites reach y=${level.maxAbsY}, off the edge-on ring".takeIf { level.maxAbsY > BAND_HALF },
                "$backend: unturned band spans only x=${level.maxAbsX}".takeIf { level.maxAbsX < RING_RADIUS },
                "$backend: unturned red (top) at y=${level.redY}, not above green at y=${level.greenY}"
                    .takeIf { level.redY <= level.greenY },
                "$backend: only ${rolled.count} sprite pixels rolled".takeIf { rolled.count < MIN_BAND },
                "$backend: rolled sprites reach x=${rolled.maxAbsX}, off the edge-on ring".takeIf { rolled.maxAbsX > BAND_HALF },
                "$backend: rolled band spans only y=${rolled.maxAbsY}".takeIf { rolled.maxAbsY < RING_RADIUS },
                "$backend: rolled red (top) at x=${rolled.redX}, not toward -X of green at x=${rolled.greenX}"
                    .takeIf { rolled.redX >= rolled.greenX },
                "$backend: a flat emitter drew ${flat.count} pixels edge on".takeIf { flat.count > MAX_EDGE_ON },
            )
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** Where a frame's red (sprite top) and green (sprite bottom) pixels are, in world units, y up. */
    private class Tones(
        val count: Int,
        val maxAbsX: Float,
        val maxAbsY: Float,
        val redX: Float,
        val redY: Float,
        val greenX: Float,
        val greenY: Float,
    )

    private fun ByteArray.tones(): Tones {
        var count = 0
        var maxAbsX = 0f
        var maxAbsY = 0f
        val red = FloatArray(3)
        val green = FloatArray(3)
        for (pixel in 0 until SCENE_SIZE * SCENE_SIZE) {
            val r = this[pixel * 4].toInt() and 0xFF
            val g = this[pixel * 4 + 1].toInt() and 0xFF
            if (r < TONE_MIN && g < TONE_MIN) continue
            val x = (((pixel % SCENE_SIZE) + 0.5f) / SCENE_SIZE * 2f - 1f) * RING_VIEW_HALF_HEIGHT
            val y = (1f - ((pixel / SCENE_SIZE) + 0.5f) / SCENE_SIZE * 2f) * RING_VIEW_HALF_HEIGHT
            count += 1
            maxAbsX = maxOf(maxAbsX, abs(x))
            maxAbsY = maxOf(maxAbsY, abs(y))
            val sums = if (r > g) red else green
            sums[0] += x
            sums[1] += y
            sums[2] += 1f
        }
        fun FloatArray.mean(axis: Int) = if (this[2] == 0f) 0f else this[axis] / this[2]
        return Tones(count, maxAbsX, maxAbsY, red.mean(0), red.mean(1), green.mean(0), green.mean(1))
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

        /** Half an edge-on band's width: half a sprite, plus a pixel for rasterisation. */
        const val BAND_HALF = UPRIGHT_SPRITE_SCALE / 2f + 2f * RING_VIEW_HALF_HEIGHT / SCENE_SIZE

        /** A few sprites' worth: one 0.5-unit sprite covers about 256 pixels at 128 px. */
        const val MIN_BAND = 1000

        /** An edge-on quad covers no pixels; a few allow for rasterisation at its rim. */
        const val MAX_EDGE_ON = 16
        const val TONE_MIN = 120
    }
}
