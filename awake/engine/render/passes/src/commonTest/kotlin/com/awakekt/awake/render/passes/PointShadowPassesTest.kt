/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.texture.RenderTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PointShadowPassesTest {
    private val target = object : RenderTarget {
        override val width = 64
        override val height = 64
        override fun destroy() = Unit
    }

    @Test
    fun createsOneSubpassPerCubemapFaceInSharedOrder() {
        val matrices = pointShadowMatrices(Vec3f.ZERO, 10f, ClipSpace.OpenGl)
        val draws = emptyList<GpuResolvedDraw>()

        val passes = matrices.toSubPasses(target, draws)

        assertEquals(6, passes.size)
        assertEquals((0..5).toList(), passes.map { it.targetLayer })
        assertEquals(matrices.viewProjections, passes.map { it.viewProjection })
        passes.forEach { pass ->
            assertSame(target, pass.target)
            assertSame(draws, pass.resolvedDraws)
        }
    }
}
