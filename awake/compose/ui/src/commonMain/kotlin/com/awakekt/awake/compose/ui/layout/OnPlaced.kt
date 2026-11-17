/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.layout

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.node.OnPlacedModifierNode

/**
 * Reports this node's tree-space bounds after each layout pass.
 *
 * Fires on every pass, not only on change: a caller that wants change-detection can compare, and a
 * modifier that silently skipped a call would be the harder bug to find.
 *
 * A function type boxes its four `Int`s on each call. A node placed every frame, such as one of a
 * HUD's hundreds of elements, passes an [OnPlacedListener] instead, which takes them unboxed (#1512).
 */
fun Modifier.onPlaced(onPlaced: (x: Int, y: Int, width: Int, height: Int) -> Unit): Modifier =
    this then OnPlacedElement(onPlaced, null)

/** [onPlaced] with a [listener] that takes the bounds unboxed: nothing is allocated per pass. */
fun Modifier.onPlaced(listener: OnPlacedListener): Modifier = this then OnPlacedElement(null, listener)

/** Takes a node's tree-space bounds after each layout pass, as [Modifier.onPlaced] reports them. */
fun interface OnPlacedListener {
    /** Called after each layout pass with the node's tree-space position and resolved size. */
    fun onPlaced(x: Int, y: Int, width: Int, height: Int)
}

private class OnPlacedElement(
    private val callback: ((Int, Int, Int, Int) -> Unit)?,
    private val listener: OnPlacedListener?,
) : ModifierNodeElement<OnPlacedNode>() {
    override fun create(): OnPlacedNode = OnPlacedNode()

    override fun update(node: OnPlacedNode) {
        node.callback = callback
        node.listener = listener
    }
    override fun toString(): String = "onPlaced()"
}

private class OnPlacedNode :
    Modifier.Node(),
    OnPlacedModifierNode {
    var callback: ((Int, Int, Int, Int) -> Unit)? = null
    var listener: OnPlacedListener? = null

    override fun onPlaced(x: Int, y: Int, width: Int, height: Int) {
        val unboxed = listener
        if (unboxed != null) unboxed.onPlaced(x, y, width, height) else callback?.invoke(x, y, width, height)
    }

    override fun toString(): String = "onPlaced()"
}

/**
 * Reports this node's size, and only when it changes.
 *
 * [onPlaced] fires after every layout pass, which for a caller that only wants the size means a
 * callback per frame forever. This fires on change, so `remember`ing the value in response does not
 * re-run sixty times a second for a size that never moved.
 *
 * Compose has both for the same reason. `onGloballyPositioned` is deliberately not added alongside
 * them: it differs from `onPlaced` by reporting `LayoutCoordinates`, a type this engine has no
 * equivalent of, and without that it would be a second spelling of the same callback.
 */
fun Modifier.onSizeChanged(onSizeChanged: (width: Int, height: Int) -> Unit): Modifier =
    this then OnSizeChangedElement(onSizeChanged)

private class OnSizeChangedElement(
    private val callback: (Int, Int) -> Unit,
) : ModifierNodeElement<OnSizeChangedNode>() {
    override fun create(): OnSizeChangedNode = OnSizeChangedNode()

    override fun update(node: OnSizeChangedNode) {
        node.callback = callback
    }
    override fun toString(): String = "onSizeChanged()"
}

private class OnSizeChangedNode :
    Modifier.Node(),
    OnPlacedModifierNode {
    lateinit var callback: (Int, Int) -> Unit
    private var lastWidth = -1
    private var lastHeight = -1

    override fun onPlaced(x: Int, y: Int, width: Int, height: Int) {
        if (width == lastWidth && height == lastHeight) return
        lastWidth = width
        lastHeight = height
        callback.invoke(width, height)
    }

    override fun toString(): String = "onSizeChanged()"
}
