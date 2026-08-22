// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.lazy

import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.remember

/**
 * Which item is at the top of a lazy list, and how far into it the viewport starts.
 *
 * Heights are recorded as items are measured, so scrolling walks real measurements rather than the
 * one shared height `ui-core`'s `LazyList` forced on every row. That compromise existed only to
 * dodge trial-measure cost -- there is no trial pass here, so items can differ in height.
 */
class LazyListState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
) {
    var firstVisibleItemIndex: Int = firstVisibleItemIndex
        internal set

    /** How far the first visible item is scrolled past the top edge, in pixels. */
    var firstVisibleItemScrollOffset: Int = firstVisibleItemScrollOffset
        internal set

    /** Measured last pass. Sparse on purpose -- an unvisited item has no height to know. */
    private val heights = HashMap<Int, Int>()

    internal var viewportSize: Int = 0

    internal var itemCount: Int = 0

    internal fun recordHeight(index: Int, height: Int) {
        heights[index] = height
    }

    internal fun heightOf(index: Int): Int = heights[index] ?: estimatedItemHeight

    /**
     * The average of what has been measured, or a guess before anything has.
     *
     * Only used for items never yet on screen, which is the price of choosing the visible window
     * before measuring rather than during -- see [LazyColumn]'s note.
     */
    internal val estimatedItemHeight: Int
        get() = if (heights.isEmpty()) DEFAULT_ESTIMATE else heights.values.sum() / heights.size

    /** Total extent, real where measured and estimated elsewhere. For a scrollbar, not for layout. */
    val estimatedContentHeight: Int
        get() = (0 until itemCount).sumOf { heightOf(it) }

    val canScrollBackward: Boolean
        get() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

    val canScrollForward: Boolean
        get() = estimatedContentHeight - consumedBefore(firstVisibleItemIndex) -
            firstVisibleItemScrollOffset > viewportSize

    private fun consumedBefore(index: Int): Int = (0 until index).sumOf { heightOf(it) }

    /**
     * Scrolls by [delta] pixels and returns what was consumed.
     *
     * Walks item by item rather than dividing by a shared height, which is the whole difference
     * from the old list: with rows of different heights, a division lands somewhere arbitrary.
     */
    fun scrollBy(delta: Int): Int {
        if (delta == 0 || itemCount == 0) return 0
        val before = absoluteOffset()
        val maxOffset = (estimatedContentHeight - viewportSize).coerceAtLeast(0)
        val target = (before + delta).coerceIn(0, maxOffset)
        seekTo(target)
        return target - before
    }

    private fun absoluteOffset(): Int = consumedBefore(firstVisibleItemIndex) + firstVisibleItemScrollOffset

    private fun seekTo(offset: Int) {
        var index = 0
        var remaining = offset
        while (index < itemCount - 1 && remaining >= heightOf(index)) {
            remaining -= heightOf(index)
            index++
        }
        firstVisibleItemIndex = index
        firstVisibleItemScrollOffset = remaining.coerceAtLeast(0)
    }

    /** Jumps straight to an item, the way a "scroll to selection" button needs. */
    fun scrollToItem(index: Int, offset: Int = 0) {
        firstVisibleItemIndex = index.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        firstVisibleItemScrollOffset = offset.coerceAtLeast(0)
    }

    override fun toString(): String =
        "LazyListState(index=$firstVisibleItemIndex, offset=$firstVisibleItemScrollOffset)"

    private companion object {
        /** Only ever used before a single item has been measured. */
        const val DEFAULT_ESTIMATE = 48
    }
}

/** A [LazyListState] that survives the next pass. */
context(_: Composer)
fun rememberLazyListState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
): LazyListState = remember { LazyListState(firstVisibleItemIndex, firstVisibleItemScrollOffset) }
