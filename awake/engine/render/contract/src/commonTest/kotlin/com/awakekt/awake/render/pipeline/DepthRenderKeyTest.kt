/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DepthRenderKeyTest {
    @Test
    fun defaultsToOpaqueOrdinaryCaster() {
        assertEquals(DepthRenderKey(DepthCasterKind.Ordinary), DepthRenderKey(DepthCasterKind.Ordinary))
        assertEquals(AlphaMode.Opaque, DepthRenderKey(DepthCasterKind.Ordinary).alphaMode)
    }

    @Test
    fun maskedFamilyRemainsAnIndependentPipelineKey() {
        val masked = DepthRenderKey(DepthCasterKind.Sprite, AlphaMode.Masked)
        assertEquals(DepthCasterKind.Sprite, masked.kind)
        assertEquals(AlphaMode.Masked, masked.alphaMode)
        assertNotEquals(masked, DepthRenderKey(DepthCasterKind.Sprite, AlphaMode.Opaque))
    }
}
