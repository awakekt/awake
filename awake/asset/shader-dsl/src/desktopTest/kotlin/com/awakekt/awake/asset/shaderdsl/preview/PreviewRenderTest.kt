/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl.preview

import com.awakekt.awake.asset.shaderdsl.CheckerShader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewRenderTest {

    @Test
    fun rendersHalfBlockRowsWithBothCheckerColors() {
        val uniforms = mapOf(
            "uniforms.tiles" to floatArrayOf(2f, 0f, 0f, 0f),
            "uniforms.colorA" to floatArrayOf(1f, 0f, 0f, 1f),
            "uniforms.colorB" to floatArrayOf(0f, 0f, 1f, 1f),
        )
        val output = renderAnsi(CheckerShader, uniforms, width = 8, height = 8)
        val lines = output.trimEnd('\n').split("\n")
        assertEquals(4, lines.size, "8 pixel rows pack into 4 half-block lines")
        assertEquals(8, lines[0].count { it == '▀' })
        assertTrue("38;2;255;0;0" in output, "colorA missing from render")
        assertTrue("38;2;0;0;255" in output, "colorB missing from render")
    }
}
