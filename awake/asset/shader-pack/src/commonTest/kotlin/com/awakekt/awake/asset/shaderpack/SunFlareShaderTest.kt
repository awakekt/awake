/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.math.ClipSpace
import kotlin.test.Test
import kotlin.test.assertTrue

class SunFlareShaderTest {
    @Test
    fun depthOccludedSunFlareEmitsValidWgslForBothClipSpaces() {
        for (clipSpace in ClipSpace.entries) {
            val wgsl = sunFlareShader(clipSpace, samplesDepth = true).emitWgsl()
            assertTrue(wgsl.contains("texture_depth_2d"), "$clipSpace should sample scene depth")
            assertTrue(wgsl.contains("sunScreen"), "$clipSpace should declare sunScreen uniform")
            assertTrue(wgsl.contains("elementGeometry"), "$clipSpace should declare elementGeometry array")
        }
    }

    @Test
    fun unoccludedSunFlareOmitsDepthBinding() {
        val wgsl = sunFlareShader(ClipSpace.Vulkan, samplesDepth = false).emitWgsl()
        assertTrue(!wgsl.contains("texture_depth_2d"), "unoccluded variant must not declare scene depth")
        assertTrue(wgsl.contains("sunScreen"))
    }
}
