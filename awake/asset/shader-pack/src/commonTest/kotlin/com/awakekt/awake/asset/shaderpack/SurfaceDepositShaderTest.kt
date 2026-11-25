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
 * Verifies emitted WGSL across scene, skinned, lit, and terrain shaders includes all dynamic
 * surface deposit uniform fields and guards procedural hash-fBm, micro-bump, and glint math inside
 * an active-amount branch so clear frames pay zero fragment ALU on mobile tile-based GPUs.
 */
class SurfaceDepositShaderTest {
    @Test
    fun texturedSkinnedLitAndTerrainShadersGuardDepositMathBehindActiveBranch() {
        val shaders = listOf(
            texturedShader(ClipSpace.Vulkan),
            texturedSolidShader(ClipSpace.Vulkan),
            SkinnedTexturedShader,
            SkinnedTexturedSolidShader,
            SkinnedShader,
            litShadowShader(ClipSpace.Vulkan),
            terrainShader(ClipSpace.Vulkan),
            pagedTerrainCoreShader(ClipSpace.Vulkan),
        )
        for (def in shaders) {
            val wgsl = def.emitWgsl()
            assertTrue(wgsl.contains("surfaceDepositColor"), "${def.name} missing surfaceDepositColor uniform")
            assertTrue(wgsl.contains("surfaceDepositParams"), "${def.name} missing surfaceDepositParams uniform")
            assertTrue(wgsl.contains("surfaceDepositDetail"), "${def.name} missing surfaceDepositDetail uniform")
            assertTrue(wgsl.contains("surfaceDepositSeed"), "${def.name} missing surfaceDepositSeed uniform")
            assertTrue(wgsl.contains("fract("), "${def.name} should use hash-based non-repeating noise")
            assertTrue(
                wgsl.contains("> 0.001"),
                "${def.name} must guard surface deposit math behind an active-amount branch for zero idle GPU cost",
            )
        }
    }
}
