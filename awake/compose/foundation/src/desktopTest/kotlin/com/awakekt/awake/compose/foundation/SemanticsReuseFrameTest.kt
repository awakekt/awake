/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsTreeBuilder
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.IntOffset
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The semantics a skipping host hands back is the tree a full rebuild of the same frame makes (#1512).
 *
 * The host reuses its last tree when nothing it was built from changed, and keeps the merged
 * configurations when only bounds moved. Semantics feeds hit testing, accessibility and UI tests, so a
 * stale bound or child list there is a wrong answer, not a slow one. Each frame of a seeded mix does
 * one of these:
 * - nothing;
 * - moves some nodes while placing (`Modifier.offset { }` over plain fields, no composition, no measure);
 * - places or unplaces a node;
 * - scrolls.
 *
 * After every frame, the host's tree must equal, node by node and property by property, a fresh
 * builder's tree of the same placed nodes. Each path, handed back, rebuilt around kept configurations,
 * and built afresh, must have run.
 */
class SemanticsReuseFrameTest {
    private val input = FrameInput(320, 240, deltaSeconds = 0f)
    private val shifts = IntArray(ITEMS)
    private val shown = mutableStateOf(true)
    private val scroll = ScrollState()

    context(_: Composer)
    private fun Content() {
        Column(Modifier.size(200.dp, 120.dp).verticalScroll(scroll).testTag("list")) {
            for (index in 0 until ITEMS) {
                Box(Modifier.offset { IntOffset(shifts[index], 0) }.size(40.dp, 20.dp).testTag("item$index"))
            }
            if (shown.value) Box(Modifier.size(30.dp).testTag("toggled"))
            Box(Modifier.size(30.dp).semantics(mergeDescendants = true) { this[SemanticsProperties.TestTag] = "merging" }) {
                Box(Modifier.offset { IntOffset(shifts[0], 0) }.semantics { this[SemanticsProperties.Label] = "hp" })
            }
        }
    }

    private fun List<SemanticsNode>.render(): String = joinToString(prefix = "[", postfix = "]") { node ->
        val properties = node.config.keys.map { "$it=${node.config[it]}" }.sorted()
        "$properties@${node.x},${node.y} ${node.width}x${node.height}${node.children.render()}"
    }

    @Test
    fun aSkippingHostsSemanticsIsAFullRebuildOfTheSameFrame() {
        val host = ComposeHost().apply {
            skipUnchangedComposition = true
            skipUnchangedMeasure = true
            retainUnchangedDraw = true
        }
        val random = Random(SEED)
        var previous = host.frame(input) { Content() }.semantics
        var handedBack = 0
        var rebound = 0
        var rebuilt = 0
        repeat(FRAMES) { frame ->
            when (random.nextInt(4)) {
                0 -> Unit
                1 -> repeat(1 + random.nextInt(3)) { shifts[random.nextInt(ITEMS)] = random.nextInt(-8, 9) }
                2 -> shown.value = !shown.value
                else -> scroll.scrollBy(random.nextInt(-30, 31))
            }
            val semantics = host.frame(input) { Content() }.semantics
            assertEquals(SemanticsTreeBuilder().build(host.root).render(), semantics.render(), "frame $frame")
            when {
                semantics === previous -> handedBack++
                semantics.firstOrNull()?.config === previous.firstOrNull()?.config -> rebound++
                else -> rebuilt++
            }
            previous = semantics
        }
        assertTrue(handedBack > 0 && rebound > 0 && rebuilt > 0, "handed back $handedBack, rebound $rebound, rebuilt $rebuilt")
    }

    private companion object {
        const val ITEMS = 12
        const val FRAMES = 300
        const val SEED = 1512
    }
}
