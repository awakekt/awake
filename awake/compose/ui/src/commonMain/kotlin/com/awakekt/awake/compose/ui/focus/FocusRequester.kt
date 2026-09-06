/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.focus

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.node.FocusPropertiesNode
import com.awakekt.awake.compose.ui.node.FocusTargetNode
import com.awakekt.awake.compose.ui.node.LayoutNode

/**
 * A handle a caller keeps so it can move focus to a node it does not otherwise hold.
 *
 * The dialog case: opening one should focus its first field, and the code doing the opening has no
 * reference to that field's node. `ui-core` solved this with `requestFocus(id)` on a string, which
 * two widgets could collide on; here the requester is an object, so a collision is impossible.
 *
 * Hold it across passes with `remember`, or the node it points at is forgotten every frame.
 */
class FocusRequester {
    internal var node: LayoutNode? = null

    /**
     * True if a focusable node is currently attached and took focus.
     *
     * [root] is threaded through so an open modal can refuse the request -- a requester held by code
     * behind a dialog must not be able to steal focus out of it.
     */
    fun requestFocus(owner: FocusOwner, root: LayoutNode): Boolean {
        val target = node
        return target != null && owner.requestFocus(root, target)
    }
}

/** Points [focusRequester] at this node, so a caller elsewhere can focus it. */
fun Modifier.focusRequester(focusRequester: FocusRequester): Modifier =
    this then FocusRequesterElement(focusRequester)

private class FocusRequesterElement(
    private val requester: FocusRequester,
) : ModifierNodeElement<FocusRequesterNode>() {
    override fun create(): FocusRequesterNode = FocusRequesterNode()

    override fun update(node: FocusRequesterNode) {
        node.requester = requester
    }

    override fun toString(): String = "focusRequester()"
}

private class FocusRequesterNode : Modifier.Node() {
    var requester: FocusRequester? = null
        set(value) {
            if (field === value) return
            if (field?.node === layoutNode) field?.node = null
            field = value
            if (layoutNode != null) value?.node = layoutNode
        }

    override fun onAttach() {
        requester?.node = layoutNode
    }

    override fun onDetach() {
        if (requester?.node === layoutNode) requester?.node = null
    }
}

/**
 * Reports focus arriving or leaving this node.
 *
 * Fires only on change, unlike [OnPlacedModifierNode], because focus is edge-triggered by nature --
 * a caller wants to scroll a field into view when it gains focus, not on every pass while it holds
 * it.
 */
fun Modifier.onFocusChanged(onFocusChanged: (Boolean) -> Unit): Modifier =
    this then OnFocusChangedElement(onFocusChanged)

private class OnFocusChangedElement(
    private val callback: (Boolean) -> Unit,
) : ModifierNodeElement<OnFocusChangedNode>() {
    override fun create(): OnFocusChangedNode = OnFocusChangedNode()

    override fun update(node: OnFocusChangedNode) {
        node.callback = callback
    }
    override fun toString(): String = "onFocusChanged()"
}

private class OnFocusChangedNode :
    Modifier.Node(),
    FocusTargetNode {
    // False: observing focus is not the same as accepting it. A node that reported focus changes
    // while silently joining the tab ring would put unfocusable containers in the Tab order.
    override val canFocus: Boolean get() = false

    lateinit var callback: (Boolean) -> Unit

    override fun onFocusChanged(focused: Boolean) = callback.invoke(focused)

    override fun toString(): String = "onFocusChanged()"
}

/**
 * Makes this node able to hold focus.
 *
 * The primitive under `foundation`'s `focusable`, which adds interaction reporting on top. Compose
 * keeps the same split, and it matters here for the same reason: `:ui` owns the focus tree, while
 * an `InteractionSource` is a styling concern that belongs a layer up.
 */
fun Modifier.focusTarget(enabled: Boolean = true): Modifier = this then FocusTargetElement(enabled)

private class FocusTargetElement(
    private val enabled: Boolean,
) : ModifierNodeElement<FocusTargetNodeImpl>() {
    override fun create(): FocusTargetNodeImpl = FocusTargetNodeImpl()

    override fun update(node: FocusTargetNodeImpl) {
        node.enabled = enabled
    }
}

private class FocusTargetNodeImpl :
    Modifier.Node(),
    FocusTargetNode {
    var enabled: Boolean = true
    override val canFocus: Boolean get() = enabled

    override fun onFocusChanged(focused: Boolean) = Unit

    override fun toString(): String = "focusTarget(enabled=$enabled)"
}

/** Mutable receiver used only while constructing a [focusProperties] modifier. */
class FocusProperties {
    /** `null` preserves the target's own enabled state. */
    var canFocus: Boolean? = null
    var next: FocusRequester? = null
    var previous: FocusRequester? = null
    var up: FocusRequester? = null
    var down: FocusRequester? = null
    var left: FocusRequester? = null
    var right: FocusRequester? = null
}

/**
 * Overrides this node's focus traversal policy.
 *
 * The policy is declarative, so it is resolved by [FocusOwner] from the placed tree rather than
 * retaining mutable traversal state in a modifier link.
 */
fun Modifier.focusProperties(configure: FocusProperties.() -> Unit): Modifier {
    val properties = FocusProperties().apply(configure)
    return this then FocusPropertiesElement(properties)
}

private class FocusPropertiesElement(
    private val properties: FocusProperties,
) : ModifierNodeElement<FocusPropertiesNodeImpl>() {
    override fun create(): FocusPropertiesNodeImpl = FocusPropertiesNodeImpl()

    override fun update(node: FocusPropertiesNodeImpl) {
        node.canFocusOverride = properties.canFocus
        node.nextRequester = properties.next
        node.previousRequester = properties.previous
        node.upRequester = properties.up
        node.downRequester = properties.down
        node.leftRequester = properties.left
        node.rightRequester = properties.right
    }

    override fun toString(): String = "focusProperties()"
}

private class FocusPropertiesNodeImpl :
    Modifier.Node(),
    FocusPropertiesNode {
    override var canFocusOverride: Boolean? = null
    override var nextRequester: Any? = null
    override var previousRequester: Any? = null
    override var upRequester: Any? = null
    override var downRequester: Any? = null
    override var leftRequester: Any? = null
    override var rightRequester: Any? = null
}

/**
 * Establishes a sequential-focus boundary.
 *
 * Awake currently exposes Tab / Shift+Tab only. Its depth-first ring already visits every declared
 * descendant before continuing with the next sibling, exactly the effect Compose's focusGroup has
 * for sequential traversal. The marker makes that guarantee explicit and leaves a canonical node
 * for future spatial focus without changing today's ordering.
 */
fun Modifier.focusGroup(): Modifier = this then FocusGroupElement

private object FocusGroupElement : Modifier.Element {
    override fun toString(): String = "focusGroup()"
}
