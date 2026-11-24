/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.math.ClipSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PagedTerrainShaderTest {
    @Test fun litAndDepthUseIdenticalCanonicalHeightLookup() {
        val lit = pagedTerrainShader(ClipSpace.Vulkan).emitWgsl()
        val depth = PagedTerrainShadowDepthShader.emitWgsl()
        val start = "fn pageHeightKnot"
        fun lookup(text: String) = text
            .substringAfter(start)
            .substringBefore("fn pageHeightAndNormal")
            .substringBefore("fn sampleSingleCascade")
            .substringBefore("@vertex")
            .trim()
        assertEquals(lookup(lit), lookup(depth))
        assertTrue("fn pageHeightAndNormal" in lit, "lit vertex shader should use fused height+normal stencil")
        assertTrue("fn pageHeightAndNormal" !in depth, "depth shader should not emit unused normal helper")
        assertTrue("@binding(32)" in lit && "@binding(33)" in lit)
        assertTrue("discard;" in depth)
    }

    @Test fun coreShaderOmitsDiscardForEarlyZ() {
        val core = pagedTerrainCoreShader(ClipSpace.Vulkan).emitWgsl()
        val regularCore = terrainCoreShader(ClipSpace.Vulkan).emitWgsl()
        assertTrue("discard;" !in core, "pagedTerrainCoreShader must not emit discard")
        assertTrue("discard;" !in regularCore, "terrainCoreShader must not emit discard")
    }
}
