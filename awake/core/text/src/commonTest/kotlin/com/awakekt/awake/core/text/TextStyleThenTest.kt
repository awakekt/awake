/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * `then` returns an existing instance when a merge changes nothing. That shortcut must never
 * change the result, so every pair below is checked against the merge written out field by field.
 */
class TextStyleThenTest {
    private val styles = listOf(
        TextStyle(),
        TextStyle(size = 14.sp),
        TextStyle(color = Color.White),
        TextStyle(color = Color.White, size = 14.sp, lineHeight = 20.sp),
        TextStyle(size = 12.sp, weight = FontWeight.Bold),
        TextStyle(scale = 2f),
        TextStyle(letterSpacing = 1.sp),
        TextStyle(color = Color.Black, size = 16.sp, lineHeight = 24.sp, scale = 2f, weight = FontWeight.Bold, letterSpacing = 1.sp),
    )

    private fun reference(base: TextStyle, other: TextStyle) = TextStyle(
        color = other.color ?: base.color,
        size = other.size ?: base.size,
        lineHeight = other.lineHeight ?: base.lineHeight,
        scale = other.scale,
        weight = other.weight,
        letterSpacing = other.letterSpacing,
    )

    @Test
    fun everyMergeEqualsTheFieldByFieldMerge() {
        for (base in styles) {
            for (other in styles) {
                assertEquals(reference(base, other), base then other, "$base then $other")
            }
        }
    }

    @Test
    fun aNoOpMergeReturnsAnExistingInstance() {
        val inherited = TextStyle(size = 14.sp)
        assertSame(inherited, inherited then TextStyle.Default)
        val full = TextStyle(color = Color.White, size = 14.sp, lineHeight = 20.sp)
        assertSame(full, TextStyle(size = 12.sp) then full)
    }
}
