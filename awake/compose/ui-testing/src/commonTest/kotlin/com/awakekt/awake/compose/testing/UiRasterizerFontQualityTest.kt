/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import kotlin.test.Test
import kotlin.test.assertEquals

class UiRasterizerFontQualityTest {

    @Test
    fun glyphSamplingRoundsUpEachSourceTexelFootprint() {
        assertEquals(2, glyphSampleCount(sourceTexels = 1.9f, screenPixels = 1f))
        assertEquals(6, glyphSampleCount(sourceTexels = 12f, screenPixels = 1f))
        assertEquals(1, glyphSampleCount(sourceTexels = 0.2f, screenPixels = 4f))
    }
}
