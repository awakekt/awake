/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.awakekt.awake.render.pipeline.PipelineVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Both backends build a plan's keyed depth casters from this one answer. */
class KeyedCasterLayoutTest {
    @Test
    fun maskedTexturedCastersAreBuiltForTheirOwnVertexFormat() {
        assertEquals(
            VertexFormat.PositionNormalColorUv to PipelineVariant.Opaque,
            DepthRenderKey(DepthCasterKind.Ordinary, AlphaMode.Masked).keyedCasterLayout(),
        )
        assertEquals(
            VertexFormat.PositionNormalColorUv to PipelineVariant.Instanced,
            DepthRenderKey(DepthCasterKind.Instanced, AlphaMode.Masked).keyedCasterLayout(),
        )
        assertEquals(
            VertexFormat.PositionNormalColorUvSkin to PipelineVariant.Opaque,
            DepthRenderKey(DepthCasterKind.Skinned, AlphaMode.Masked).keyedCasterLayout(),
        )
    }

    @Test
    fun aKeyNoMaskedCasterServesBuildsNothing() {
        assertNull(DepthRenderKey(DepthCasterKind.Skinned, AlphaMode.Opaque).keyedCasterLayout())
        assertNull(DepthRenderKey(DepthCasterKind.Particle, AlphaMode.Masked).keyedCasterLayout())
    }
}
