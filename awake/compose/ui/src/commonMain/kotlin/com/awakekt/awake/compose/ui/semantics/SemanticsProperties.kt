/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.semantics

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.node.SemanticsModifierNode

/** What a node is, for a screen reader or a test that asks "find me the button". */
enum class SemanticsRole {
    /** A control that performs an action when activated. */
    Button,

    /** A control that toggles a checked state. */
    Checkbox,

    /** A control that toggles an on or off state. */
    Switch,

    /** One option in a mutually exclusive group. */
    RadioButton,

    /** A tab that selects which panel is shown. */
    Tab,

    /** A picture. */
    Image,

    /** Static text. */
    Text,

    /** A control that picks a value from a range. */
    Slider,

    /** A dialog or other modal surface. */
    Dialog,
}

/**
 * A typed slot in a [SemanticsConfiguration].
 *
 * Keys rather than `ui-core`'s flat 18-field `UiSemanticNode`: every new semantic concern there
 * meant another field on one shared type, carried by every widget that has no use for it -- the
 * same shape problem `UiModifier` had before it became a chain. A consumer declares its own key
 * without touching this file.
 *
 * @param T The type of value the key holds.
 * @property name The key's name, used in diagnostics and by [toString].
 */
class SemanticsPropertyKey<T>(val name: String) {
    override fun toString(): String = name
}

/** The standard keys a node's [SemanticsConfiguration] can carry, set through [semantics]. */
object SemanticsProperties {
    /** The tag a test uses to find the node, set by [testTag]. */
    val TestTag = SemanticsPropertyKey<String>("TestTag")

    /** What kind of control the node is. */
    val Role = SemanticsPropertyKey<SemanticsRole>("Role")

    /** The text a reader announces for the node. */
    val Label = SemanticsPropertyKey<String>("Label")

    /** Whether the node is in its selected or on state. */
    val Selected = SemanticsPropertyKey<Boolean>("Selected")

    /** Tri-state: absent means the widget has no indeterminate concept at all. */
    val Indeterminate = SemanticsPropertyKey<Boolean>("Indeterminate")

    /** Set to `true` by a disabled widget; an enabled widget leaves it unset. */
    val Disabled = SemanticsPropertyKey<Boolean>("Disabled")

    /**
     * The node edits a password, Compose's `SemanticsProperties.Password`.
     *
     * An accessibility service must not read its text aloud and announces it as a password field
     * instead. Published by a masked `BasicTextField`.
     */
    val Password = SemanticsPropertyKey<Boolean>("Password")

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

/** Declares semantics for this node, optionally absorbing everything beneath it into one node. */
inline fun Modifier.semantics(
    mergeDescendants: Boolean = false,
    properties: SemanticsConfiguration.() -> Unit,
): Modifier {
    val config = SemanticsConfiguration()
    config.isMergingDescendants = mergeDescendants
    config.properties()
    return appendSemantics(config)
}

@PublishedApi
internal fun Modifier.appendSemantics(config: SemanticsConfiguration): Modifier =
    this then SemanticsNodeElement(config)

/** The handle a test uses to find this node. */
fun Modifier.testTag(tag: String): Modifier = this then TestTagElement(tag)

/**
 * [testTag]'s element: just the tag. The configuration is built only when the node has none or holds
 * another, so a recomposition with the same tag allocates no configuration, entries or block, which
 * a `semantics { }` call builds every time (#1742).
 */
private class TestTagElement(private val tag: String) : ModifierNodeElement<SemanticsModifierNodeImpl>() {
    override fun create(): SemanticsModifierNodeImpl = SemanticsModifierNodeImpl()

    override fun update(node: SemanticsModifierNodeImpl) {
        if (!node.isConfigured || !node.semanticsConfiguration.holdsOnly(SemanticsProperties.TestTag, tag)) {
            node.semanticsConfiguration = SemanticsConfiguration().also { it[SemanticsProperties.TestTag] = tag }
        }
    }

    override fun toString(): String = "semantics([${SemanticsProperties.TestTag}])"
}

private class SemanticsNodeElement(
    private val semanticsConfiguration: SemanticsConfiguration,
) : ModifierNodeElement<SemanticsModifierNodeImpl>() {
    override fun create(): SemanticsModifierNodeImpl = SemanticsModifierNodeImpl()

    // A recomposition builds an equal configuration anew. Keeping the node's own one lets the
    // semantics tree see the node as unchanged and hand back what it built before (#1742).
    override fun update(node: SemanticsModifierNodeImpl) {
        if (!node.isConfigured || !node.semanticsConfiguration.sameAs(semanticsConfiguration)) {
            node.semanticsConfiguration = semanticsConfiguration
        }
    }
    override fun toString(): String = "semantics(${semanticsConfiguration.keys})"
}

private class SemanticsModifierNodeImpl :
    Modifier.Node(),
    SemanticsModifierNode {
    override lateinit var semanticsConfiguration: SemanticsConfiguration

    val isConfigured: Boolean get() = ::semanticsConfiguration.isInitialized
}

/**
 * Replaces everything beneath this node's semantics with [properties].
 *
 * `semantics(mergeDescendants = true)` absorbs descendants and fills its own gaps from them. This
 * discards them instead, which is what a decorative container of already-labelled parts needs: a
 * chart made of a hundred labelled bars should report "revenue by month", not a hundred bars.
 */
inline fun Modifier.clearAndSetSemantics(properties: SemanticsConfiguration.() -> Unit): Modifier {
    val config = SemanticsConfiguration()
    config.isMergingDescendants = true
    config.isClearingDescendants = true
    config.properties()
    return appendSemantics(config)
}
