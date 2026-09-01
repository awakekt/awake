/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.render.renderer.MAX_POINT_LIGHTS
import io.github.awakelab.awake.render.renderer.UniformFields
import kotlin.test.Test
import kotlin.test.assertEquals

/** Regression on the exact totals `prepareDrawCalls` (both backends) already depends on --
 * these must never silently drift, since the two backends' uniform-buffer writes are hand-
 * concatenated to match, not generated from this layout. */
class UniformLayoutsTest {
    // 60 + 32 and 68 + 32: MAX_POINT_LIGHTS slots x two vec4 arrays (positions+range, colours).
    // These numbers are the shader's struct size, so a change here without the matching .wgsl
    // edit is the mismatch the test exists to catch.

    @Test
    fun texturedUniformLayoutTotalIsNinetyTwo() {
        assertEquals(92, TexturedUniformLayout.total)
    }

    @Test
    fun litShadowUniformLayoutTotalIncludesEveryCascade() {
        // 104 before cascades, when one lightMvp covered the whole shadow. The block now carries
        // MAX_SHADOW_CASCADES world-to-light matrices instead of that one (+48), and one vec4 per
        // cascade of depth scale (+16) so a world-space bias means the same offset in each --
        // the price of the cascade set being uniform data rather than a second binding.
        assertEquals(168, LitShadowUniformLayout.total)
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
