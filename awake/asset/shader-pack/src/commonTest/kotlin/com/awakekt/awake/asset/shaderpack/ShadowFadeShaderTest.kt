/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.math.ClipSpace
import kotlin.test.Test
import kotlin.test.assertTrue

/** The shadow fade reads the cascade count's fractional part, in every shader that samples the cascades. */
class ShadowFadeShaderTest {

    @Test
    fun theLitShadowShaderFadesTheShadowOverTheLastCascadesReach() {
        for (clipSpace in listOf(ClipSpace.WebGpu, ClipSpace.Vulkan)) {
            val wgsl = litShadowShader(clipSpace).emitWgsl()

            assertTrue(wgsl.contains("fract("), "$clipSpace reads the fade from the fractional part")
            assertTrue(wgsl.contains("i32(floor("), "$clipSpace and the count from the whole part")
            assertTrue(wgsl.contains("shadowReach"), "$clipSpace fades toward the last cascade's reach")
        }
    }

    @Test
    fun theInstancedAndSkinnedShadowShadersFadeToo() {
        assertTrue(instancedLitShadowShader(ClipSpace.WebGpu).emitWgsl().contains("shadowFade"))
        assertTrue(skinnedInstancedLitShadowShader(ClipSpace.WebGpu).emitWgsl().contains("shadowFade"))
    }
}
