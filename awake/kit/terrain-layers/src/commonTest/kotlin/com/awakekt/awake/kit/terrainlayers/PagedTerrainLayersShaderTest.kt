/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.core.math.ClipSpace
import kotlin.test.Test
import kotlin.test.assertTrue

class PagedTerrainLayersShaderTest {
    @Test fun bothControlWidthsEmitPageArraysAndCrossCellTaps() {
        for (slots in listOf(4, 8)) {
            val shader = terrainLayersShader(ClipSpace.Vulkan, slots, true).emitWgsl()
            assertTrue("texture_2d_array" in shader && "@binding(34)" in shader)
            assertTrue("pageHeightKnot" in shader && "tap3Layers" in shader)
        }
    }
}
