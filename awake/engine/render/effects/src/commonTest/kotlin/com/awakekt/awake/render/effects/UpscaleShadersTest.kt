/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UpscaleShadersTest {
    @Test
    fun casSpecsAreRegisteredAndEmitWgsl() {
        assertSame(UpscaleShaders.CasSharpenSpec, ImagePassShaderRegistry[UpscaleShaders.CAS_SHARPEN_SHADER_ID])
        assertSame(UpscaleShaders.CasUpscaleSpec, ImagePassShaderRegistry[UpscaleShaders.CAS_UPSCALE_SHADER_ID])
        assertTrue(UpscaleShaders.CasSharpenSpec.wgslSource.contains("@fragment"))
        assertTrue(UpscaleShaders.CasUpscaleSpec.wgslSource.contains("@fragment"))
    }

    @Test
    fun casPeakAndUniformPackingMatchReferenceRange() {
        assertEquals(-0.125f, UpscaleShaders.casPeak(0f), 1e-6f)
        assertEquals(-0.2f, UpscaleShaders.casPeak(1f), 1e-6f)
        assertEquals(-0.125f, UpscaleShaders.casPeak(-1f), 1e-6f)
        assertEquals(-0.2f, UpscaleShaders.casPeak(2f), 1e-6f)

        val sharpen = UpscaleShaders.packCasSharpenUniforms(128, 64, 0.5f)
        assertEquals(1f / 128f, sharpen[0], 1e-6f)
        assertEquals(1f / 64f, sharpen[1], 1e-6f)
        assertEquals(UpscaleShaders.casPeak(0.5f), sharpen[2], 1e-6f)

        val upscale = UpscaleShaders.packCasUpscaleUniforms(96, 48, 0.8f)
        assertEquals(96f, upscale[0], 1e-6f)
        assertEquals(48f, upscale[1], 1e-6f)
        assertEquals(1f / 96f, upscale[2], 1e-6f)
        assertEquals(1f / 48f, upscale[3], 1e-6f)
        assertEquals(UpscaleShaders.casPeak(0.8f), upscale[4], 1e-6f)
    }
}
