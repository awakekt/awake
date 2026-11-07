/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.particles.ParticleSpace
import com.awakekt.awake.render.testing.HeadlessRenderSession
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [renderParticleEmitterScene] on both backends, Vulkan first: a world-space emitter leaves a trail
 * across the frame and a local-space one keeps every sprite in a column above where it ended.
 *
 * The left of the frame is the measure: its columns hold the trail's first sprites and never the local
 * column, which stands at x = [EMITTER_END_X]. Negative control, measured on lavapipe at 128 px on both
 * backends: with `ParticleDrawBuilder` drawing a local emitter through the identity frame instead of its
 * entity's, the column stands at the origin, so the local emitter leaves 432 red pixels on the left and
 * none at the right edge, and both local assertions fail.
 */
class ParticleEmitterParityTest {
    @Test
    fun aWorldEmitterLeavesATrailAndALocalOneCarriesItsSprites() {
        val failures = BACKEND_ORDER.flatMap { backend ->
            val renderer = session(backend).renderer
            val world = renderer.renderParticleEmitterScene(ParticleSpace.World, SCENE_SIZE)
            val local = renderer.renderParticleEmitterScene(ParticleSpace.Local, SCENE_SIZE)
            val worldLeft = world.redIn(0 until LEFT_COLUMNS)
            val localLeft = local.redIn(0 until LEFT_COLUMNS)
            val localRight = local.redIn(LEFT_COLUMNS until SCENE_SIZE)
            listOfNotNull(
                "$backend: the world-space trail left $worldLeft red pixels on the left, not a trail".takeIf { worldLeft < MIN_TRAIL },
                "$backend: the local-space emitter left $localLeft red pixels behind on the left".takeIf { localLeft > 0 },
                "$backend: the local-space column has only $localRight red pixels".takeIf { localRight < MIN_TRAIL },
            )
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun ByteArray.redIn(columns: IntRange): Int = (0 until SCENE_SIZE * SCENE_SIZE).count { pixel ->
        pixel % SCENE_SIZE in columns && isRed(pixel)
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

        /** The left 60% of the frame: x below 0.4 world units, well short of the local column. */
        const val LEFT_COLUMNS = SCENE_SIZE * 3 / 5

        /** A handful of sprites' worth: one 0.25-unit sprite covers about 64 pixels at 128 px. */
        const val MIN_TRAIL = 200
        const val RED_MIN = 120
        const val OTHER_MAX = 80
    }
}
