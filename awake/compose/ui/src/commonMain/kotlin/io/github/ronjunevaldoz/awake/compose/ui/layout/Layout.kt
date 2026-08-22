// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.ronjunevaldoz.awake.compose.ui.layout

import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.Slot
import io.github.ronjunevaldoz.awake.compose.runtime.current
import io.github.ronjunevaldoz.awake.compose.runtime.node
import io.github.ronjunevaldoz.awake.compose.runtime.reconcile
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNodeApplier
import io.github.ronjunevaldoz.awake.compose.ui.platform.LocalDensity
import io.github.ronjunevaldoz.awake.compose.ui.platform.LocalFontScale

// PascalCase composables: Compose's own convention, and this is public API surface -- the same
// reason Constraints.Infinity keeps its casing. See 11-refinements.md rule 1.

/**
 * Declares a layout node: the primitive every container is built from.
 *
 * [nodeType] is the node's reconciliation identity alongside its position -- two different
 * composables declaring at the same index are not the same node. Pass a stable value, usually the
 * composable's own policy class or a marker object.
 */
context(composer: Composer)
fun Layout(
    nodeType: Any,
    modifier: Modifier = Modifier,
    measurePolicy: MeasurePolicy,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    // Read at composition, applied to the node -- a measure policy never touches composition state,
    // which is what lets a subtree be measured in isolation.
    val density = LocalDensity.current
    val fontScale = LocalFontScale.current
    composer.node(
        type = nodeType,
        update = { node ->
            node as LayoutNode
            node.measurePolicy = measurePolicy
            node.modifier = modifier
            node.density = density
            node.fontScale = fontScale
        },
        content = content?.let { { it(composer) } },
    )
}

/**
 * Declares an overlay: measured against the viewport rather than the enclosing constraints.
 *
 * It lands in the declaring node's [LayoutNode.layers] rather than its children, so the parent's
 * measure policy never sees it. See `docs/reference/compose-engine/07-overlay-layering.md`.
 *
 * [modal] bounds the focus ring to this layer: Tab cycles inside a dialog rather than walking out
 * into the page behind it.
 */
context(composer: Composer)
fun Layer(
    kind: LayerKind,
    modifier: Modifier = Modifier,
    modal: Boolean = false,
    measurePolicy: MeasurePolicy,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    composer.node(
        type = kind,
        slot = Slot.Layers,
        update = { node ->
            node as LayoutNode
            node.measurePolicy = measurePolicy
            node.modifier = modifier
            node.isModal = modal
        },
        content = content?.let { { it(composer) } },
    )
}

/** Paint and hit-test order. Within a kind, ordering is the order layers were declared. */
enum class LayerKind { Popup, Dialog, Tooltip, Toast }

/**
 * Builds or updates [root]'s subtree from [content], reusing every node that still matches.
 *
 * Call once per frame. Nodes survive across calls -- that is the whole point of a retained tree, and
 * what `ui-core` cannot do because it has no tree to keep.
 */
fun composeInto(root: LayoutNode, content: context(Composer) () -> Unit) {
    reconcile(LayoutNodeApplier(root), root, content)
}
