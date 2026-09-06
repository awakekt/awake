/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.lazy.LazyColumn
import com.awakekt.awake.compose.foundation.lazy.LazyListState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val red = Color(1f, 0f, 0f, 1f)

private const val ITEMS = 1000

private const val ITEM_HEIGHT = 20

/** A 100-tall viewport over 1000 items of 20 each: 20,000 px of content, 5 items visible. */
private fun host(state: LazyListState, built: MutableList<Int>? = null): Pair<
    ComposeHost,
    context(Composer)
    () -> Unit,
    > {
    val host = ComposeHost()
    val content: context(Composer)
    () -> Unit = {
        LazyColumn(itemCount = ITEMS, modifier = Modifier.size(100.dp, 100.dp), state = state) { index ->
            built?.add(index)
            Spacer(Modifier.size(100.dp, ITEM_HEIGHT.dp).background(red))
        }
    }
    return host to content
}

private val still = FrameInput(viewportWidth = 200, viewportHeight = 200, pointerX = 10, pointerY = 10)

/**
 * Virtualization, measured rather than assumed.
 *
 * The claim is that a 1000-item list costs the same as a 10-item one. A test that only checked
 * "the right items are on screen" would pass with every item built, which is the whole cost.
 */
class LazyColumnTest {

    @Test
    fun onlyTheVisibleWindowIsBuilt() {
        val built = mutableListOf<Int>()
        val state = LazyListState()
        val (host, content) = host(state, built)

        host.frame(still, content)
        host.frame(still, content)
        built.clear()
        host.frame(still, content)

        assertTrue(built.size < 12, "built ${built.size} of $ITEMS items")
        assertTrue(built.isNotEmpty())
    }

    @Test
    fun aThousandItemsCostTheSameAsAScreenful() {
        // The virtualization claim as a number: node count tracks the viewport, not the list.
        val state = LazyListState()
        val (host, content) = host(state)
        repeat(3) { host.frame(still, content) }

        val list = host.root.children[0]
        assertTrue(list.children.size < 12, "the tree holds ${list.children.size} nodes")
    }

    @Test
    fun itemsMayDifferInHeight() {
        // `ui-core`'s LazyList forced one shared height on every row, purely to dodge trial cost.
        val state = LazyListState()
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            LazyColumn(itemCount = 10, modifier = Modifier.size(100.dp, 100.dp), state = state) { index ->
                Spacer(Modifier.size(100.dp, ((index + 1) * 10).dp).background(red))
            }
        }
        repeat(3) { host.frame(still, content) }

        val items = host.root.children[0].children
        assertEquals(10, items[0].height)
        assertEquals(20, items[1].height)
        assertEquals(30, items[2].height, "every row took the first row's height")
    }

    @Test
    fun scrollingMovesTheWindowNotJustTheOffset() {
        val built = mutableListOf<Int>()
        val state = LazyListState()
        val (host, content) = host(state, built)
        repeat(2) { host.frame(still, content) }

        state.scrollBy(ITEM_HEIGHT * 100)
        built.clear()
        host.frame(still, content)

        assertEquals(100, state.firstVisibleItemIndex)
        assertTrue(built.all { it >= 100 }, "still building items above the viewport: ${built.take(3)}")
    }

    @Test
    fun scrollingLandsBetweenItemsNotOnlyOnThem() {
        val state = LazyListState()
        val (host, content) = host(state)
        repeat(2) { host.frame(still, content) }

        state.scrollBy(ITEM_HEIGHT * 3 + 7)

        assertEquals(3, state.firstVisibleItemIndex)
        assertEquals(7, state.firstVisibleItemScrollOffset, "the offset within the item was lost")
    }

    @Test
    fun scrollToItemJumps() {
        val state = LazyListState()
        val (host, content) = host(state)
        repeat(2) { host.frame(still, content) }

        state.scrollToItem(500)
        host.frame(still, content)

        assertEquals(500, state.firstVisibleItemIndex)
    }

    @Test
    fun theEndsAreReported() {
        val state = LazyListState()
        val (host, content) = host(state)
        repeat(2) { host.frame(still, content) }

        assertFalse(state.canScrollBackward)
        assertTrue(state.canScrollForward)

        state.scrollBy(ITEM_HEIGHT * ITEMS)
        assertTrue(state.canScrollBackward)
        assertFalse(state.canScrollForward, "scrolled past the end")
    }

    @Test
    fun aWheelOverTheListScrollsItThroughTheFrame() {
        val state = LazyListState()
        val (host, content) = host(state)
        repeat(2) { host.frame(still, content) }

        val output = host.frame(
            FrameInput(viewportWidth = 200, viewportHeight = 200, pointerX = 10, pointerY = 10, scrollDeltaY = -3f),
            content,
        )

        assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
        assertTrue(output.ownership.isScrollConsumed)
    }

    @Test
    fun anItemKeepsItsNodeWhenTheWindowSlides() {
        // Keyed by item index, not by position. Positional identity would hand item 7's node to
        // item 8 the moment the window moved by one.
        val state = LazyListState()
        val (host, content) = host(state)
        repeat(2) { host.frame(still, content) }
        val itemTwo = host.root.children[0].children[2]

        state.scrollBy(ITEM_HEIGHT)
        host.frame(still, content)

        val after = host.root.children[0].children
        var kept = false
        for (i in after.indices) if (after[i] === itemTwo) kept = true
        assertTrue(kept, "every node was rebuilt when the window slid by one item")
    }
}
