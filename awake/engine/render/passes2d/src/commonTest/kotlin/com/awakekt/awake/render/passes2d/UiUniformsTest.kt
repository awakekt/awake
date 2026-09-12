/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.math.Vec4
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UiUniformsTest {

    @Test
    fun uiUniformPayloadUsesTheDeclaredBufferLayout() {
        val values = uiUniformFloats(
            screenToNdc = Vec4(2f, -2f, -1f, 1f),
            fontInfo = Vec4(1f, 4f, 0f, 0f),
        )

        assertEquals(UiUniformLayouts.Buffer.total, values.size)
        assertContentEquals(floatArrayOf(2f, -2f, -1f, 1f, 1f, 4f, 0f, 0f), values)
    }

    @Test
    fun targetCompositeModeUsesADeclaredPaddedBlock() {
        assertEquals(4, UiTargetCompositeUniformLayout.total)
    }
}
