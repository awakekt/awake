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
        fun lookup(text: String) = text.substringAfter(start).substringBefore("fn sampleSingleCascade").substringBefore("@vertex").trim()
        assertEquals(lookup(lit), lookup(depth))
        assertTrue("@binding(32)" in lit && "@binding(33)" in lit)
        assertTrue("discard;" in depth)
    }
}
