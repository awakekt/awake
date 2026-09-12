/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class PointShadowMatricesTest {
    @Test
    fun buildsSixDistinctUnitAspectFacesWithTheAuthoredRange() {
        val result = pointShadowMatrices(Vec3f(2f, 3f, 4f), 12f, ClipSpace.WebGpu)

        assertEquals(6, result.viewProjections.size)
        assertEquals(12f, result.farPlane)
        assertEquals(Vec3f(2f, 3f, 4f), result.position)
        assertNotEquals(result.viewProjections[0].data.toList(), result.viewProjections[1].data.toList())
        assertNotEquals(result.viewProjections[2].data.toList(), result.viewProjections[3].data.toList())
    }

    @Test
    fun usesTheBackendClipSpaceConvention() {
        val gl = pointShadowMatrices(Vec3f.ZERO, 10f, ClipSpace.OpenGl)
        val vulkan = pointShadowMatrices(Vec3f.ZERO, 10f, ClipSpace.Vulkan)

        assertEquals(-gl.viewProjections[0].data[5], vulkan.viewProjections[0].data[5])
    }

    @Test
    fun rejectsAClipRangeThatCannotContainTheLight() {
        assertFailsWith<IllegalArgumentException> {
            pointShadowMatrices(Vec3f.ZERO, range = 0.05f, clipSpace = ClipSpace.WebGpu)
        }
    }
}
