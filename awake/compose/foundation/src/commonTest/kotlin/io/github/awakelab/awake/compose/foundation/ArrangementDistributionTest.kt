/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.platform.ComposeHost
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where the leftover space goes.
 *
 * `Arrangement` carried only a gap before, so every one of these read as `Start` -- a shell that
 * asked for `SpaceBetween` got its children packed at the left and looked merely mis-designed
 * rather than broken. A distribution that quietly does nothing passes every other layout test,
 * which is why these assert positions rather than sizes.
 */
class ArrangementDistributionTest {

    private fun xs(arrangement: Arrangement.Horizontal): List<Int> {
        val seen = mutableListOf<SemanticsNode>()
        val frame = ComposeHost().frame(FrameInput(viewportWidth = 100, viewportHeight = 20)) {
            Row(Modifier.size(100.dp, 20.dp), horizontalArrangement = arrangement) {
                Box(Modifier.size(20.dp).testTag("a"))
                Box(Modifier.size(20.dp).testTag("b"))
            }
        }
        fun walk(nodes: List<SemanticsNode>) {
            nodes.forEach {
                seen += it
                walk(it.children)
            }
        }
        walk(frame.semantics)
        return listOf("a", "b").map { tag -> seen.first { it.testTag == tag }.x }
    }

    @Test
    fun startPacksAtZero() {
        assertEquals(listOf(0, 20), xs(Arrangement.Start))
    }

    @Test
    fun endPutsTheLeftoverInFront() {
        // 100 wide holding 40 of children: both slide right by the full 60.
        assertEquals(listOf(60, 80), xs(Arrangement.End))
    }

    @Test
    fun centerSplitsTheLeftover() {
        assertEquals(listOf(30, 50), xs(Arrangement.CenterHorizontally))
    }

    @Test
    fun spaceBetweenPinsBothEnds() {
        // First at the start, last flush to the end: the 60 goes into the single gap.
        assertEquals(listOf(0, 80), xs(Arrangement.SpaceBetweenHorizontal))
    }
}
