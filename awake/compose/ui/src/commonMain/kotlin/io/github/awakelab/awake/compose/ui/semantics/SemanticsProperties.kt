/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.semantics

import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.node.SemanticsModifierNode

/** What a node is, for a screen reader or a test that asks "find me the button". */
enum class SemanticsRole { Button, Checkbox, Switch, RadioButton, Tab, Image, Text, Slider, Dialog }

/**
 * A typed slot in a [SemanticsConfiguration].
 *
 * Keys rather than `ui-core`'s flat 18-field `UiSemanticNode`: every new semantic concern there
 * meant another field on one shared type, carried by every widget that has no use for it -- the
 * same shape problem `UiModifier` had before it became a chain. A consumer declares its own key
 * without touching this file.
 */
class SemanticsPropertyKey<T>(val name: String) {
    override fun toString(): String = name
}

object SemanticsProperties {
    val TestTag = SemanticsPropertyKey<String>("TestTag")
    val Role = SemanticsPropertyKey<SemanticsRole>("Role")
    val Label = SemanticsPropertyKey<String>("Label")
    val Selected = SemanticsPropertyKey<Boolean>("Selected")

    /** Tri-state: absent means the widget has no indeterminate concept at all. */
    val Indeterminate = SemanticsPropertyKey<Boolean>("Indeterminate")
    val Disabled = SemanticsPropertyKey<Boolean>("Disabled")

    /**
     * Marks a container whose children are mutually exclusive options.
     *
     * A marker on the group, not a role: the group is not itself a radio button, and giving it one
     * would announce a sixth control that does not exist. What it carries is the fact that lets a
     * reader say "option 2 of 5", which the tree knows and no child can state alone.
     */
    val SelectableGroup = SemanticsPropertyKey<Boolean>("SelectableGroup")

    /**
     * The inset from this node's own edge to its content, in **Dp**, as `(horizontal, vertical)`.
     *
     * A parity diagnostic, not an accessibility property, and the one thing here a screen reader has
     * no use for. It exists because the CSS box a reference capture reports has a *content box* --
     * border box minus padding -- and nothing in a placed tree can reconstruct that after the fact:
     * a child's bounds are where the content landed, not where the box allows it to go, so a centred
     * or intrinsically-narrow label reads as a much smaller content box than the DOM's.
     *
     * `ui-core` reached the identical conclusion and shipped `UiSemanticNode.contentPadding`
     * "retained so diagnostics do not infer it from text bounds". Published by `styleable`, which is
     * the only thing that knows the resolved value.
     *
     * Dp, not pixels, because a semantics configuration is assembled before measurement and has no
     * density to convert with. The reader scales it.
     */
    val ContentPadding = SemanticsPropertyKey<Pair<Float, Float>>("ContentPadding")

    /** Resolved border width in dp, published by a styled surface for visual parity diagnostics. */
    val BorderWidth = SemanticsPropertyKey<Float>("BorderWidth")

    /** Resolved uniform corner radius in dp, published by a styled surface for visual parity diagnostics. */
    val CornerRadius = SemanticsPropertyKey<Float>("CornerRadius")
}

/**
 * The properties one node declares.
 *
 * [isMergingDescendants] is the answer to "a button with a text child should be one node, not two".
 * A merging node absorbs its descendants' properties and they stop appearing separately -- every
 * `ui-headless` control has that shape, so the decision had to land before any of them are ported.
 */
class SemanticsConfiguration {
    private val values = mutableMapOf<SemanticsPropertyKey<*>, Any?>()

    var isMergingDescendants: Boolean = false

    /**
     * Drops descendants' semantics rather than absorbing them.
     *
     * Implies [isMergingDescendants]: a node that replaces what is beneath it is by definition the
     * only node reported for that subtree.
     */
    var isClearingDescendants: Boolean = false

    val keys: Set<SemanticsPropertyKey<*>> get() = values.keys

    operator fun <T> set(key: SemanticsPropertyKey<T>, value: T) {
        values[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: SemanticsPropertyKey<T>): T? = values[key] as T?

    operator fun contains(key: SemanticsPropertyKey<*>): Boolean = values.containsKey(key)

    /**
     * Copies [other]'s properties in without overwriting what is already here.
     *
     * The merging node wins: a button labelled "Save" that contains a text reading "Save now"
     * reports "Save". An ancestor states intent; a descendant only fills gaps.
     */
    fun mergeFrom(other: SemanticsConfiguration) {
        for (key in other.keys) {
            if (key !in this) values[key] = other.values[key]
        }
    }
}

/** Declares semantics for this node, optionally absorbing everything beneath it into one node. */
fun Modifier.semantics(
    mergeDescendants: Boolean = false,
    properties: SemanticsConfiguration.() -> Unit,
): Modifier {
    val config = SemanticsConfiguration()
    config.isMergingDescendants = mergeDescendants
    config.properties()
    return this then SemanticsNodeElement(config)
}

/** The handle a test uses to find this node. */
fun Modifier.testTag(tag: String): Modifier = semantics { this[SemanticsProperties.TestTag] = tag }

private class SemanticsNodeElement(
    private val semanticsConfiguration: SemanticsConfiguration,
) : ModifierNodeElement<SemanticsModifierNodeImpl>() {
    override fun create(): SemanticsModifierNodeImpl = SemanticsModifierNodeImpl()
    override fun update(node: SemanticsModifierNodeImpl) {
        node.semanticsConfiguration = semanticsConfiguration
    }
    override fun toString(): String = "semantics(${semanticsConfiguration.keys})"
}

private class SemanticsModifierNodeImpl : Modifier.Node(), SemanticsModifierNode {
    override lateinit var semanticsConfiguration: SemanticsConfiguration
}

/**
 * Replaces everything beneath this node's semantics with [properties].
 *
 * `semantics(mergeDescendants = true)` absorbs descendants and fills its own gaps from them. This
 * discards them instead, which is what a decorative container of already-labelled parts needs: a
 * chart made of a hundred labelled bars should report "revenue by month", not a hundred bars.
 */
fun Modifier.clearAndSetSemantics(properties: SemanticsConfiguration.() -> Unit): Modifier {
    val config = SemanticsConfiguration()
    config.isMergingDescendants = true
    config.isClearingDescendants = true
    config.properties()
    return this then SemanticsNodeElement(config)
}
