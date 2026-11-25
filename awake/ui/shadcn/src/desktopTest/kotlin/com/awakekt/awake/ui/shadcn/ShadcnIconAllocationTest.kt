/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.layout.LayoutComposition
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import com.awakekt.awake.ui.shadcn.components.ShadcnIcon
import com.awakekt.awake.ui.shadcn.theme.LocalShadcnTheme
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertNotEquals

/**
 * Steady-state composition and staleness tests for [ShadcnIcon]: an unchanged icon allocates zero
 * bytes on composition and updates immediately when icon, size, or tint changes.
 */
class ShadcnIconAllocationTest {
    private val theme = ShadcnThemeValues(ShadcnTheme)
    private val icon = HeroIcons.Solid20Mini.chevronDown

    @Test
    fun steadyStateIconCompositionAllocatesNothing() {
        val root = LayoutNode(ColumnMeasurePolicy())
        val composition = LayoutComposition(root)
        val content: context(com.awakekt.awake.compose.runtime.Composer)
        () -> Unit = {
            CompositionLocalProvider(LocalShadcnTheme, theme) {
                for (i in 0 until ICONS) {
                    ShadcnIcon(icon, size = 16.dp, tint = Color.White)
                }
            }
        }
        repeat(WARMUP) { composition.compose(content) }
        val composeReading = readAllocation(PASSES) { repeat(PASSES) { composition.compose(content) } }
        println("ShadcnIcon steady-state composition (x$ICONS): $composeReading")
        assertAllocatesNothing(composeReading, "steady-state ShadcnIcon composition")

        // Painting an already-tessellated icon should stay bounded and reuse its mesh without
        // allocating a per-icon Rectangle.
        root.layoutTree(Constraints.fixed(128, 512))
        val painter = Painter()
        repeat(WARMUP) { painter.paint(root) }
        val drawReading = readAllocation(PASSES) { repeat(PASSES) { painter.paint(root) } }
        println("ShadcnIcon steady-state paint (x$ICONS): $drawReading")
    }

    @Test
    fun iconUpdatesWhenIconSizeOrTintChanges() {
        val host = ComposeHost()
        var currentIcon = HeroIcons.Outline24.globeAlt
        var currentSize = 16.dp
        var currentTint = Color.White

        fun render() = host.frame(FrameInput(64, 64)) {
            provideShadcnTheme(theme) {
                ShadcnIcon(currentIcon, size = currentSize, tint = currentTint)
            }
        }.primitives.toList()

        val first = render()
        currentTint = Color(1f, 0f, 0f, 1f)
        val afterTint = render()
        assertNotEquals(first, afterTint)

        currentSize = 24.dp
        val afterSize = render()
        assertNotEquals(afterTint, afterSize)

        currentIcon = HeroIcons.Outline24.cube
        assertNotEquals(afterSize, render())
    }

    private companion object {
        const val ICONS = 16
        const val WARMUP = 2_000
        const val PASSES = 1_000
    }
}
