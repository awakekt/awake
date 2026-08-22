// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.semantics

import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.node.SemanticsModifierNode

/** What a node is, for a screen reader or a test that asks "find me the button". */
enum class SemanticsRole { Button, Checkbox, Switch, RadioButton, Tab, Image, Text, Slider }

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
    override val semanticsConfiguration: SemanticsConfiguration,
) : SemanticsModifierNode {
    override fun toString(): String = "semantics(${semanticsConfiguration.keys})"
}
