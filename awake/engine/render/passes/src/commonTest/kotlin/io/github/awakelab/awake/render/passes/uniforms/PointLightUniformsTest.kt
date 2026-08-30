/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.passes.uniforms

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.renderer.MAX_POINT_LIGHTS
import io.github.awakelab.awake.render.renderer.PointLight
import io.github.awakelab.awake.render.renderer.SceneLight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the shader actually receives, checked at the byte offsets it reads.
 *
 * These assert the *contents* of the packed block, not its size. `UniformLayoutsTest` already
 * catches a layout that grew or shrank; nothing catches a layout that is the right size and holds
 * the wrong numbers -- a range written into a colour's slot produces a correctly-sized buffer and
 * a scene lit by nothing.
 *
 * Scoped to the CPU side deliberately. Verifying the shader's *math* needs a real device, and the
 * only target that can run one in a plain test process is desktop Vulkan -- see
 * `RendererHeadlessPixelBaselineTest`. That would also cover only Vulkan; WebGPU's rendering has
 * no headless harness at all. The packing is the half that can be pinned down cheaply, and it is
 * the half this code owns.
 */
class PointLightUniformsTest {
    private val slots = FloatArray(MAX_POINT_LIGHTS * VEC4 * 2)
    private val colorBase = MAX_POINT_LIGHTS * VEC4

    private fun light(vararg points: PointLight) =
        SceneLight(
            direction = Vec3f(0f, 1f, 0f),
            color = Vec3f(1f, 1f, 1f),
            points = points.toList(),
        )

    @Test
    fun packsPositionWithRangeInTheFourthSlotAndColourAlongside() {
        packPointLights(
            light(PointLight(Vec3f(1f, 2f, 3f), Vec3f(0.25f, 0.5f, 0.75f), range = 7f)),
            eye = Vec3f.ZERO,
            into = slots,
        )

        assertEquals(listOf(1f, 2f, 3f, 7f), slots.slice(0 until 4))
        assertEquals(listOf(0.25f, 0.5f, 0.75f, 0f), slots.slice(colorBase until colorBase + 4))
    }

    /** `w <= 0` is how the shader spells "this slot is off". A slot never written must read zero,
     * or a light that was removed keeps lighting the scene from wherever it last stood. */
    @Test
    fun unusedSlotsReadZeroSoTheShaderSkipsThem() {
        packPointLights(
            light(PointLight(Vec3f(1f, 1f, 1f), Vec3f(1f, 1f, 1f), range = 5f)),
            Vec3f.ZERO,
            slots,
        )

        (1 until MAX_POINT_LIGHTS).forEach { slot ->
            assertEquals(0f, slots[slot * VEC4 + 3], "slot $slot range should be off")
        }
    }

    /** The buffer is caller-owned and reused across frames, so packing must clear what it does not
     * write. Without the clear, last frame's light survives its own removal. */
    @Test
    fun reusingTheBufferDoesNotLeaveLastFramesLightBehind() {
        packPointLights(
            light(PointLight(Vec3f(9f, 9f, 9f), Vec3f(1f, 0f, 0f), range = 4f)),
            Vec3f.ZERO,
            slots,
        )
        packPointLights(light(), Vec3f.ZERO, slots)

        assertTrue(slots.all { it == 0f }, "a frame with no lights must clear the previous frame's")
    }

    /** Over the cap the nearest to the eye win -- distance is what attenuation is about to divide
     * by, so the survivors are the ones that would have contributed most. */
    @Test
    fun pastTheCapTheNearestLightsWin() {
        val far = PointLight(Vec3f(100f, 0f, 0f), Vec3f(1f, 0f, 0f), range = 1f)
        val near = (1..MAX_POINT_LIGHTS).map {
            PointLight(Vec3f(it.toFloat(), 0f, 0f), Vec3f(0f, 1f, 0f), range = it.toFloat())
        }
        packPointLights(light(far, *near.toTypedArray()), eye = Vec3f.ZERO, into = slots)

        val packedX = (0 until MAX_POINT_LIGHTS).map { slots[it * VEC4] }.sorted()
        assertEquals(near.map { it.position.x }.sorted(), packedX)
    }

    @Test
    fun noLightsPacksAnAllZeroBlock() {
        packPointLights(light(), Vec3f.ZERO, slots)

        assertTrue(slots.all { it == 0f })
    }

    private companion object {
        const val VEC4 = 4
    }
}
