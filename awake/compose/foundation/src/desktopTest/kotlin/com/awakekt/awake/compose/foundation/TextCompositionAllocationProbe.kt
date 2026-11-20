/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextOutline
import com.awakekt.awake.core.text.theme.TextShadow
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.testing.allocation.readAllocation
import com.sun.management.HotSpotDiagnosticMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What composing one unchanged [Text] costs (#1813): the bytes a composition of 2N labels allocates beyond one of N,
 * over N. The rest of the frame (the column, the provider) is the same in both and cancels out.
 *
 * Composition alone, nothing measured or drawn, so the figure is `Text`'s own per-call objects and nothing else:
 * the merged style, the measure policy, the paint element, the modifier chain and the node's update.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class TextCompositionAllocationProbe {
    private val labels = List(MAX_LABELS) { "Label ${it + 1}" }
    private val cellModifier = Modifier.width(120.dp)
    private val ambient = TextStyle(color = Color.White, size = 13.sp)
    private val decorated = TextStyle(outline = TextOutline(Color.Black, 1f), shadow = TextShadow(Color.Black, 1f, 1f))

    /** A label with the default style and no modifier: no merge, no chain to build. */
    @Test
    fun anUnchangedPlainTextAllocatesNothing() {
        assertEquals(0L, bytesPerText("plain") { label -> Text(label) })
    }

    /**
     * A HUD label: a themed ambient style, a decorated style merged over it, which builds a new style, and a modifier
     * from the caller, which builds a new chain.
     */
    @Test
    fun anUnchangedDecoratedHudTextAllocatesNothing() {
        assertEquals(0L, bytesPerText("decorated HUD") { label -> Text(label, cellModifier, style = decorated) })
    }

    private fun bytesPerText(case: String, text: context(Composer) (String) -> Unit): Long {
        val few = bytesPerFrame(FEW, text)
        val many = bytesPerFrame(MAX_LABELS, text)
        val perText = (many - few) / (MAX_LABELS - FEW)
        println(
            "TextCompositionAllocationProbe ($case): $perText B a Text " +
                "($few B/frame for $FEW, $many B/frame for $MAX_LABELS; escape analysis ${escapeAnalysis()})",
        )
        return perText
    }

    private fun bytesPerFrame(count: Int, text: context(Composer) (String) -> Unit): Long {
        val root = LayoutNode(ColumnMeasurePolicy())
        val frame = {
            composeInto(root) {
                CompositionLocalProvider(LocalTextStyle provides ambient) {
                    // No key: `key` is not inline, and its lambda would be charged to each label. The list never changes.
                    for (i in 0 until count) text(labels[i])
                }
            }
        }
        repeat(WARMUP_FRAMES) { frame() }
        return readAllocation(MEASURED_FRAMES) { repeat(MEASURED_FRAMES) { frame() } }.perUnit
    }

    private fun escapeAnalysis(): String =
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean::class.java).getVMOption("DoEscapeAnalysis").value

    private companion object {
        const val FEW = 20
        const val MAX_LABELS = 60
        const val WARMUP_FRAMES = 3_000
        const val MEASURED_FRAMES = 1_000
    }
}
