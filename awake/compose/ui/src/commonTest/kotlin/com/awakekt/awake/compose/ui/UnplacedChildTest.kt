/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A layout may leave a child unplaced for a frame, and then that child is not drawn or announced;
 * placed again, it is both (#1512). Before, an unplaced child was drawn and announced where it was
 * last placed, so a layout had to measure a hidden child at 0x0 and park it off screen.
 */
class UnplacedChildTest {
    private object Placing

    private var placeSecond = true

    private val policy = MeasurePolicy { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables[0].placeAt(0, 0)
            if (placeSecond) placeables[1].placeAt(20, 0)
        }
    }

    @Test
    fun anUnplacedChildIsNotDrawnOrAnnouncedUntilItIsPlacedAgain() {
        val host = ComposeHost()

        // Counted as each frame is made: the host reuses its primitive lists from frame to frame.
        fun frame(): Pair<List<String>, Int> = host.frame(FrameInput(WIDTH, HEIGHT)) { twoBoxes() }.let { tags(it) to it.primitives.size }

        val both = frame()
        placeSecond = false
        val one = frame()
        placeSecond = true
        val again = frame()

        assertEquals(listOf("first", "second"), both.first)
        assertEquals(listOf("first"), one.first, "the unplaced child is not announced")
        assertEquals(both.second - 1, one.second, "nor drawn")
        assertEquals(both, again, "placed again, it is both")
    }

    private fun tags(output: FrameOutput): List<String> {
        val found = mutableListOf<String>()
        fun walk(nodes: List<SemanticsNode>) {
            nodes.forEach { node ->
                node.testTag?.let { found += it }
                walk(node.children)
            }
        }
        walk(output.semantics)
        return found
    }

    context(_: Composer)
    private fun twoBoxes() {
        Layout(Placing, Modifier.size(60.dp, 20.dp), policy) {
            Box(Modifier.size(10.dp).background(Color(1f, 0f, 0f, 1f)).testTag("first"))
            Box(Modifier.size(10.dp).background(Color(0f, 0f, 1f, 1f)).testTag("second"))
        }
    }

    private companion object {
        const val WIDTH = 100
        const val HEIGHT = 40
    }
}
