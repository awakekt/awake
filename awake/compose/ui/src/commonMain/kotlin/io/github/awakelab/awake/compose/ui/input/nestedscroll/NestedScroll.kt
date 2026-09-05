/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.input.nestedscroll

import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.node.LayoutNode

enum class NestedScrollSource {
    UserInput,
    SideEffect,
}

/**
 * Interface to participate in nested scroll hierarchies.
 *
 * Ancestor nodes intercept scroll deltas before children consume them ([onPreScroll])
 * and consume any unconsumed leftovers after children finish ([onPostScroll]).
 */
interface NestedScrollConnection {
    /**
     * Pre-scroll pass: ancestor has first opportunity to consume [available] delta before the child.
     * Returns the consumed delta.
     */
    fun onPreScroll(availableX: Float, availableY: Float, source: NestedScrollSource): FloatArray =
        ZERO_DELTA

    /**
     * Post-scroll pass: called with [consumed] delta taken by child and [available] remaining delta.
     * Returns any additional delta consumed by this connection.
     */
    fun onPostScroll(
        consumedX: Float,
        consumedY: Float,
        availableX: Float,
        availableY: Float,
        source: NestedScrollSource,
    ): FloatArray = ZERO_DELTA

    companion object {
        private val ZERO_DELTA = floatArrayOf(0f, 0f)
    }
}

/**
 * Nested scroll dispatcher used by scroll containers to bubble scroll deltas up the ancestor chain.
 */
class NestedScrollDispatcher {
    internal var node: LayoutNode? = null

    /**
     * Dispatches pre-scroll up the ancestor tree. Returns [FloatArray(2)] containing consumed `(dx, dy)`.
     */
    fun dispatchPreScroll(availableX: Float, availableY: Float, source: NestedScrollSource): FloatArray {
        var current = node?.parent
        var remX = availableX
        var remY = availableY
        var totalConsumedX = 0f
        var totalConsumedY = 0f

        while (current != null) {
            val connections = current.nestedScrollConnections
            for (i in connections.indices) {
                val consumed = connections[i].onPreScroll(remX, remY, source)
                val cx = consumed[0]
                val cy = consumed[1]
                totalConsumedX += cx
                totalConsumedY += cy
                remX -= cx
                remY -= cy
            }
            current = current.parent
        }
        return floatArrayOf(totalConsumedX, totalConsumedY)
    }

    /**
     * Dispatches post-scroll up the ancestor tree. Returns [FloatArray(2)] containing consumed `(dx, dy)`.
     */
    fun dispatchPostScroll(
        consumedX: Float,
        consumedY: Float,
        availableX: Float,
        availableY: Float,
        source: NestedScrollSource,
    ): FloatArray {
        var current = node?.parent
        var remX = availableX
        var remY = availableY
        var totalConsumedX = 0f
        var totalConsumedY = 0f

        while (current != null) {
            val connections = current.nestedScrollConnections
            for (i in connections.indices) {
                val consumed = connections[i].onPostScroll(consumedX, consumedY, remX, remY, source)
                val cx = consumed[0]
                val cy = consumed[1]
                totalConsumedX += cx
                totalConsumedY += cy
                remX -= cx
                remY -= cy
            }
            current = current.parent
        }
        return floatArrayOf(totalConsumedX, totalConsumedY)
    }
}

/**
 * Attaches a [NestedScrollConnection] to participate in nested scroll deltas.
 */
fun Modifier.nestedScroll(
    connection: NestedScrollConnection,
    dispatcher: NestedScrollDispatcher? = null,
): Modifier = this then NestedScrollElement(connection, dispatcher)

private class NestedScrollElement(
    private val connection: NestedScrollConnection,
    private val dispatcher: NestedScrollDispatcher?,
) : ModifierNodeElement<NestedScrollNode>() {
    override fun create(): NestedScrollNode = NestedScrollNode(connection, dispatcher)

    override fun update(node: NestedScrollNode) {
        node.connection = connection
        node.dispatcher = dispatcher
    }

    override fun toString(): String = "nestedScroll()"
}

internal class NestedScrollNode(
    var connection: NestedScrollConnection,
    var dispatcher: NestedScrollDispatcher?,
) : Modifier.Node(),
    NestedScrollModifierNode {

    override fun onAttachedTo(node: LayoutNode) {
        dispatcher?.node = node
    }
}

internal interface NestedScrollModifierNode {
    fun onAttachedTo(node: LayoutNode)
}
