/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.FlexBox
import com.awakekt.awake.compose.foundation.layout.FlexBoxConfig
import com.awakekt.awake.compose.foundation.layout.FlexWrap
import com.awakekt.awake.compose.foundation.layout.FlowColumn
import com.awakekt.awake.compose.foundation.layout.FlowRow
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.layout.LayoutComposition
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Steady-state composition allocation gates for core layout containers and leaf widgets:
 * - [Box] with non-default alignment (single-alignment and axis-pair overloads)
 * - [Row] and [Column] with `Arrangement.spacedBy*` and non-default alignment
 * - [Image] with `contentDescription`
 * - [BasicTextField] with placeholder and `singleLine = true`
 *
 * Uses a retained [LayoutComposition] (the same path [com.awakekt.awake.compose.ui.platform.ComposeHost] runs)
 * so every allocation measured is the composable's own.
 */
class ComposePrimitivesAllocationTest {
    private val bitmap = ImageBitmap(16, 16, ByteArray(16 * 16 * 4))
    private val fixedSize = Modifier.size(120.dp, 24.dp)
    private val fieldStates = List(COUNT) { TextFieldState("field $it") }
    private val flexConfig = FlexBoxConfig {
        wrap = FlexWrap.Wrap
        gap(4.dp)
    }

    @Test
    fun alignedBoxAllocatesNothingInSteadyState() {
        val perPass = steadyStateBytes {
            for (i in 0 until COUNT) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(fixedSize)
                    }
                }
            }
        }
        println("ComposePrimitivesAllocationTest (Box aligned x$COUNT): $perPass B/pass")
        assertEquals(0L, perPass, "steady-state aligned Box allocated $perPass B/pass")
    }

    @Test
    fun flexBoxAndFlowAllocateNothingInSteadyState() {
        val perPass = steadyStateBytes {
            for (i in 0 until COUNT) {
                FlexBox(config = flexConfig) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedByHorizontal(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FlowColumn(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalArrangement = Arrangement.spacedByHorizontal(2.dp),
                        ) {
                            Box(fixedSize)
                        }
                    }
                }
            }
        }
        println("ComposePrimitivesAllocationTest (FlexBox+FlowRow+FlowColumn x$COUNT): $perPass B/pass")
        assertEquals(0L, perPass, "steady-state FlexBox/FlowRow/FlowColumn allocated $perPass B/pass")
    }

    @Test
    fun spacedRowAndColumnAllocateNothingInSteadyState() {
        val perPass = steadyStateBytes {
            for (i in 0 until COUNT) {
                Row(
                    horizontalArrangement = Arrangement.spacedByHorizontal(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(fixedSize)
                    }
                }
            }
        }
        println("ComposePrimitivesAllocationTest (Row+Column spacedBy x$COUNT): $perPass B/pass")
        assertEquals(0L, perPass, "steady-state spaced Row/Column allocated $perPass B/pass")
    }

    @Test
    fun describedImageAllocatesNothingInSteadyState() {
        val perPass = steadyStateBytes {
            for (i in 0 until COUNT) {
                Image(bitmap = bitmap, contentDescription = "badge", modifier = fixedSize)
            }
        }
        println("ComposePrimitivesAllocationTest (Image x$COUNT): $perPass B/pass")
        assertEquals(0L, perPass, "steady-state Image allocated $perPass B/pass")
    }

    @Test
    fun basicTextFieldAllocatesNothingInSteadyState() {
        val perPass = steadyStateBytes {
            for (i in 0 until COUNT) {
                BasicTextField(
                    state = fieldStates[i],
                    modifier = fixedSize,
                    placeholder = "Search",
                    singleLine = true,
                )
            }
        }
        println("ComposePrimitivesAllocationTest (BasicTextField x$COUNT): $perPass B/pass")
        assertEquals(0L, perPass, "steady-state BasicTextField allocated $perPass B/pass")
    }

    private fun steadyStateBytes(content: context(Composer) () -> Unit): Long {
        val root = LayoutNode(ColumnMeasurePolicy())
        val composition = LayoutComposition(root)
        repeat(WARMUP) { composition.compose(content) }
        return readAllocation(MEASURED) { repeat(MEASURED) { composition.compose(content) } }.perUnit
    }

    private companion object {
        const val COUNT = 16
        const val WARMUP = 3_000
        const val MEASURED = 1_000
    }
}
