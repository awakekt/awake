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

class MaskedDepthShaderTest {
    @Test
    fun maskedTexturedDepthEmitsDiscardAndStandardMaterialBindings() {
        val vertex = (
            PackShaderSets.MaskedTexturedShadowDepth.webGpu[ShaderStage.VERTEX]
                as ShaderSource.InlineText
            ).sourceCode
        val fragment = (
            PackShaderSets.MaskedTexturedShadowDepth.webGpu[ShaderStage.FRAGMENT]
                as ShaderSource.InlineText
            ).sourceCode

        assertTrue("@binding(1)" in vertex || "@binding(1)" in fragment)
        assertTrue("@binding(2)" in vertex || "@binding(2)" in fragment)
        assertTrue("discard;" in fragment)
        assertTrue("pbrFactors.z" in fragment)
    }
}
