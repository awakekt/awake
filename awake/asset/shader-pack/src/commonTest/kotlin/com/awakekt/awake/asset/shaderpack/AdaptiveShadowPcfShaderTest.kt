/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.render.pipeline.ShaderSource
import kotlin.test.Test
import kotlin.test.assertTrue

/** Verifies [cascadeShadowSampling] emits the adaptive PCF fast paths for 512 and 1024 shadow maps (#1709). */
class AdaptiveShadowPcfShaderTest {

    @Test
    fun litShadowAndTerrainEmitAdaptivePcfThresholdsOnBothBackends() {
        val shaderSets = listOf(
            "lit_shadow" to PackShaderSets.LitShadow,
            "textured" to PackShaderSets.Textured,
            "terrain" to PackShaderSets.Terrain,
        )
        for ((name, set) in shaderSets) {
            for (stages in listOf(set.vulkan, set.webGpu)) {
                val wgsl = (stages[ShaderStage.FRAGMENT] as ShaderSource.InlineText).sourceCode
                assertTrue(
                    "SHADOW_PCF_MINIMAL_MAX_TEXELS" in wgsl,
                    "$name must declare minimal 1-tap PCF threshold",
                )
                assertTrue(
                    "SHADOW_PCF_REDUCED_MAX_TEXELS" in wgsl,
                    "$name must declare reduced 3x3 PCF threshold",
                )
                assertTrue(
                    "PCF_RADIUS_REDUCED" in wgsl,
                    "$name must declare reduced PCF radius 1",
                )
            }
        }
    }
}
