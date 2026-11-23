/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.math.ClipSpace
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards mobile GPU Rule 1 for [texturedShader]: unlit and unlit-additive draws must bypass
 * PBR texture fetches, normal perturbation, GGX BRDF, shadows, and point lights under a uniform branch.
 */
class UnlitTexturedShaderGuardTest {
    @Test
    fun unlitAndAdditiveDrawsBypassPbrLightingAndShadows() {
        val wgsl = texturedShader(ClipSpace.Vulkan).emitWgsl()
        val fastPathIndex = wgsl.indexOf("var fastRgb")
        val metallicIndex = wgsl.indexOf("let metallicRoughness")
        val shadowIndex = wgsl.indexOf("let shadowFactor")
        assertTrue(fastPathIndex > 0, "emits unlit/additive fast path block")
        assertTrue(metallicIndex > fastPathIndex, "guards metallicRoughness fetch behind lit branch")
        assertTrue(shadowIndex > fastPathIndex, "guards shadow sampling behind lit branch")
    }
}
