/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import kotlin.test.Test
import kotlin.test.assertTrue

class AslUiShadersTest {
    @Test
    fun uiQuadShaderEmitsValidWgsl() {
        val wgsl = UiQuadShader.emitWgsl()
        assertTrue(wgsl.contains("screenToNdc"), "Should contain the live screenToNdc uniform")
        assertTrue(wgsl.contains("fn vertexMain"), "Should have vertex entry point")
        assertTrue(wgsl.contains("fn fragmentMain"), "Should have fragment entry point")
    }

    @Test
    fun uiTextureShaderEmitsValidWgsl() {
        val wgsl = UiTextureShader.emitWgsl()
        assertTrue(wgsl.contains("previewTexture"), "Should contain texture binding")
        assertTrue(wgsl.contains("previewSampler"), "Should contain sampler binding")
        assertTrue(wgsl.contains("screenToNdc"), "Should contain the live screenToNdc uniform")
    }

    @Test
    fun roundedQuadShaderEmitsDerivativeAaWithLiveAbi() {
        val wgsl = UiRoundedQuadShader.emitWgsl()
        assertTrue(wgsl.contains("screenToNdc"), "Should contain the live screenToNdc uniform")
        assertTrue(wgsl.contains("fwidth"), "Should use derivative-based corner AA")
        assertTrue(wgsl.contains("roundedDistance"), "Should preserve the smoothing branch")
    }

    @Test
    fun glyphShaderEmitsCoverageAndDistanceFieldPaths() {
        val wgsl = UiGlyphShader.emitWgsl()
        assertTrue(wgsl.contains("fontInfo"), "Should contain the live fontInfo uniform")
        assertTrue(wgsl.contains("textureDimensions"), "Should read the atlas dimensions")
        assertTrue(wgsl.contains("fwidth"), "Should use derivative-based glyph range")
        assertTrue(wgsl.contains("correctedCoverage"), "Should preserve gamma correction")
    }

    @Test
    fun targetCompositeShaderUsesSharedResourceAndModeBindings() {
        val wgsl = UiTargetCompositeShader.emitWgsl()
        assertTrue(wgsl.contains("@binding(4)"), "Should contain the shared mode uniform")
        assertTrue(wgsl.contains("vertex_index"), "Should use the full-screen triangle")
        assertTrue(wgsl.contains("destinationTexture"), "Should contain both target textures")
    }
}
