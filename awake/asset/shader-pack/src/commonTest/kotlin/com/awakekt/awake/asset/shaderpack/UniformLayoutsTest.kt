/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.render.passes.uniforms.MAX_POINT_LIGHTS
import com.awakekt.awake.render.renderer.UniformFields
import kotlin.test.Test
import kotlin.test.assertEquals

/** Regression on the exact totals `prepareDrawCalls` (both backends) already depends on --
 * these must never silently drift, since the two backends' uniform-buffer writes are hand-
 * concatenated to match, not generated from this layout. */
class UniformLayoutsTest {
    // 64 + 32 and 72 + 32: MAX_POINT_LIGHTS slots x two vec4 arrays (positions+range, colours).
    // These numbers are the shader's struct size, so a change here without the matching .wgsl
    // edit is the mismatch the test exists to catch.

    @Test
    fun texturedUniformLayoutTotalIsOneHundredEightyEight() {
        // 104, then the sun's cascades: four matrices (+64), their depth scales (+16) and camera forward (+4).
        assertEquals(188, TexturedUniformLayout.total)
    }

    @Test
    fun litShadowUniformLayoutTotalIncludesEveryCascade() {
        // 104 before cascades, when one lightMvp covered the whole shadow. The block now carries
        // MAX_SHADOW_CASCADES world-to-light matrices (+48), depth scale/split data (+16), and a
        // camera-forward vector for split-aligned blending (+4), then the debug view (+4).
        assertEquals(176, LitShadowUniformLayout.total)
    }

    @Test
    fun bothPbrLayoutsReservePointLightSlots() {
        listOf(TexturedUniformLayout, LitShadowUniformLayout).forEach { layout ->
            listOf(UniformFields.PointLightPositions, UniformFields.PointLightColors)
                .forEach { field ->
                    assertEquals(
                        MAX_POINT_LIGHTS * 4,
                        layout.fields.single { it === field }.floats,
                    )
                }
        }
    }
}
