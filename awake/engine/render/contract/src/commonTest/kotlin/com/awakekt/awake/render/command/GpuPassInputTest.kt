/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import kotlin.test.Test
import kotlin.test.assertEquals

class GpuPassInputTest {
    @Test
    fun emptyInputIsAResolvedPacketForSceneLessPresentation() {
        assertEquals(true, GpuPassInput.EMPTY.resolvedPath)
    }

    @Test
    fun resolvedDrawsNeverReadsLegacyLists() {
        val resolved = GpuResolvedDraw(
            pipeline = object : PipelineHandle {},
            materialBinding = object : MaterialBinding {},
            vertexBuffer = object : BufferHandle {},
            indexBuffer = null,
            elementCount = 3,
        )

        val input = GpuPassInput.EMPTY.copy(
            resolvedOpaqueDraws = listOf(resolved),
            resolvedTransparentDraws = emptyList(),
            resolvedPath = true,
        )

        assertEquals(listOf(resolved), input.resolvedDraws)
    }
}
