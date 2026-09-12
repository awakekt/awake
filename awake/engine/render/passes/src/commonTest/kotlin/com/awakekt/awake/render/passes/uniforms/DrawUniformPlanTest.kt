/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.geometry.VertexFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class DrawUniformPlanTest {
    @Test
    fun selectsTheSharedAbiInPriorityOrder() {
        assertEquals(
            DrawUniformPlan.LitShadow,
            drawUniformPlan(
                format = VertexFormat.PositionNormalColorUv,
                materialUniformFloatCount = MaterialUniformLayouts.LitShadow.total,
                hasShadowCascades = true,
            ),
        )
        assertEquals(
            DrawUniformPlan.LitShadow,
            drawUniformPlan(
                format = VertexFormat.PositionNormalColorUv,
                materialUniformFloatCount = MaterialUniformLayouts.LitShadow.total,
                hasShadowCascades = false,
            ),
        )
        assertEquals(
            DrawUniformPlan.Skinned,
            drawUniformPlan(
                format = VertexFormat.PositionNormalColorSkin,
                materialUniformFloatCount = 0,
                hasShadowCascades = true,
            ),
        )
        assertEquals(
            DrawUniformPlan.TexturedPbr,
            drawUniformPlan(
                format = VertexFormat.PositionNormalColorUv,
                materialUniformFloatCount = MaterialUniformLayouts.PbrTextured.total,
                hasShadowCascades = false,
            ),
        )
    }

    @Test
    fun fallsBackToLitWhenSpecializedAbiIsUnavailable() {
        assertEquals(
            DrawUniformPlan.Lit,
            drawUniformPlan(
                format = VertexFormat.PositionNormalColor,
                materialUniformFloatCount = MaterialUniformLayouts.Lit.total - 1,
                hasShadowCascades = false,
            ),
        )
        assertEquals(
            DrawUniformPlan.Lit,
            drawUniformPlan(
                format = VertexFormat.PositionColor,
                materialUniformFloatCount = MaterialUniformLayouts.LitShadow.total,
                hasShadowCascades = true,
            ),
        )
    }
}
