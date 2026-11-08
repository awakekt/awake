/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.gestures

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.node.PointerInputNode
import com.awakekt.awake.compose.ui.platform.FrameClock
import com.awakekt.awake.compose.ui.platform.LocalFrameClock

context(_: Composer)
/**
 * Fires [onDoubleClick] when two clicks land on this node in quick succession, and [onClick], when
 * given, for every click including both of a double click.
 *
 * The first click is not held back to see whether a second follows, unlike Compose's
 * `combinedClickable`, which delays it: here a single click acts at once, so pair a cheap,
 * repeatable single click (select) with a double click that builds on it (open). Timing reads the
 * composition's [LocalFrameClock], so it follows the host's frames and is deterministic in tests.
 *
 * A click is a press and release that both land on this node, as for `clickable`; consumes, so an
 * ancestor that also handles clicks does not see the same one.
 */
fun Modifier.doubleClickable(
    onClick: (() -> Unit)? = null,
    maxIntervalSeconds: Float = DoubleClickTracker.DEFAULT_MAX_INTERVAL_SECONDS,
    slopPx: Int = DoubleClickTracker.DEFAULT_SLOP_PX,
    onDoubleClick: () -> Unit,
): Modifier = this then DoubleClickableElement(LocalFrameClock.current, maxIntervalSeconds, slopPx, onClick, onDoubleClick)

private class DoubleClickableElement(
    private val clock: FrameClock,
    private val maxIntervalSeconds: Float,
    private val slopPx: Int,
    private val onClick: (() -> Unit)?,
    private val onDoubleClick: () -> Unit,
) : ModifierNodeElement<DoubleClickableNode>() {
    override fun create(): DoubleClickableNode = DoubleClickableNode()

    override fun update(node: DoubleClickableNode) {
        if (node.tracker.maxIntervalSeconds != maxIntervalSeconds || node.tracker.slopPx != slopPx) {
            node.tracker = DoubleClickTracker(maxIntervalSeconds, slopPx)
        }
        node.clock = clock
        node.onClick = onClick
        node.onDoubleClick = onDoubleClick
    }

    override fun toString(): String = "doubleClickable()"
}

private class DoubleClickableNode :
    Modifier.Node(),
    PointerInputNode {
    var tracker = DoubleClickTracker()
    lateinit var clock: FrameClock
    var onClick: (() -> Unit)? = null
    lateinit var onDoubleClick: () -> Unit

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main || event.isConsumed) return
        when (event.type) {
            PointerEventType.Press -> event.consume()
            PointerEventType.Release -> if (event.isCaptureHolder) {
                if (event.isInBounds && !event.longPressConsumed) {
                    onClick?.invoke()
                    if (tracker.onClick(clock.totalSeconds, event.x, event.y)) onDoubleClick()
                    event.consume()
                } else {
                    // A drag off the node or a long press something else used breaks the pair.
                    tracker.reset()
                }
            }
            else -> Unit
        }
    }

    override fun toString(): String = "doubleClickable()"
}
