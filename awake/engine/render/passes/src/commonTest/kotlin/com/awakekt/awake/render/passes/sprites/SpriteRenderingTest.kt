/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.sprites

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.passes.uniforms.SpriteExtraUniformLayout
import com.awakekt.awake.render.passes.uniforms.SpriteFields
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpriteRenderingTest {
    private val input = SpriteDrawInput("sheet", Mat4().translate(3f, 4f, 5f), columns = 2, rows = 2, frame = 3, pixelsPerUnit = 8f)

    @Test
    fun cellPixelsDetermineLocalSizeWithoutScalingTranslation() {
        val model = spriteModel(input, 32, 16)
        assertEquals(2f, model.data[0])
        assertEquals(1f, model.data[5])
        assertEquals(3f, model.data[12])
        assertEquals(4f, model.data[13])
        assertEquals(5f, model.data[14])
        assertEquals(1f, input.model.data[0], "the caller's world transform stays intact")
    }

    @Test
    fun frameAndFlipsStayWithinTheSameCellAndCarryAlphaTint() {
        val normal = spriteUniforms(input, 32, 16)
        val flipped = spriteUniforms(input.copy(flipX = true, flipY = true, tint = Color(0.2f, 0.4f, 0.6f, 0.5f)), 32, 16)
        assertContentEquals(floatArrayOf(0.5f, 0.5f, 0.5f, 0f), normal.copyOfRange(0, 4))
        assertContentEquals(floatArrayOf(-0.5f, -0.5f, 1f, 0.5f), flipped.copyOfRange(0, 4))
        val tint = SpriteExtraUniformLayout.offsetOf(SpriteFields.Tint)
        assertContentEquals(floatArrayOf(0.2f, 0.4f, 0.6f, 0.5f), flipped.copyOfRange(tint, tint + 4))
    }

    @Test
    fun malformedSheetsFailBeforeTheyCanAddressAnotherCell() {
        for (bad in listOf(input.copy(columns = 0), input.copy(frame = 4), input.copy(columns = 65536, rows = 65536), input.copy(pixelsPerUnit = Float.NaN))) {
            assertFailsWith<IllegalArgumentException> { spriteUniforms(bad, 32, 16) }
        }
        assertFailsWith<IllegalArgumentException> { spriteUniforms(input, 31, 16) }
    }
}
