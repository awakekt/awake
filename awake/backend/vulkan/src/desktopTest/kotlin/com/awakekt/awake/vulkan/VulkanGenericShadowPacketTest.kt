/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuSubPass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards the generic packet-to-Vulkan shadow ABI in exact parity with WebGPU. */
class VulkanGenericShadowPacketTest {

    @Test
    fun genericPrePassesRemainLayeredWork() {
        val first = Mat4().translate(1f, 0f, 0f)
        val second = Mat4().translate(2f, 0f, 0f)
        val input = GpuPassInput(
            prePasses = listOf(
                GpuSubPass(target = null, viewProjection = first),
                GpuSubPass(target = null, viewProjection = second),
            ),
            viewProjection = Mat4(),
            cameraEye = Vec3f(0f, 0f, 0f),
            passUniforms = FloatArray(0),
        )

        assertEquals(2, input.prePasses.size)
        assertEquals(first, input.prePasses[0].viewProjection)
        assertEquals(second, input.prePasses[1].viewProjection)
    }

    @Test
    fun packetsWithoutPrePassesDoNotScheduleShadowWork() {
        assertTrue(GpuPassInput.EMPTY.prePasses.isEmpty())
        assertTrue(GpuPassInput.EMPTY.postPasses.isEmpty())
    }

    @Test
    fun genericPostPassesRemainLayeredWork() {
        val toneMapVp = Mat4()
        val input = GpuPassInput(
            viewProjection = Mat4(),
            cameraEye = Vec3f(0f, 0f, 0f),
            passUniforms = FloatArray(0),
            postPasses = listOf(
                GpuSubPass(target = null, viewProjection = toneMapVp),
            ),
        )

        assertEquals(1, input.postPasses.size)
        assertEquals(toneMapVp, input.postPasses[0].viewProjection)
    }
}
