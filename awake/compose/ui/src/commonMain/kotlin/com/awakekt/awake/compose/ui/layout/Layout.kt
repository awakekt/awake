/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.ui.layout

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.Composition
import com.awakekt.awake.compose.runtime.Slot
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.node
import com.awakekt.awake.compose.runtime.reconcile
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.node.LayoutNodeApplier
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalFontScale

// PascalCase composables: Compose's own convention, and this is public API surface -- the same
// reason Constraints.Infinity keeps its casing. See 11-refinements.md rule 1.

context(composer: Composer)
/**
 * Declares a layout node: the primitive every container is built from.
 *
 * [nodeType] is the node's reconciliation identity alongside its position -- two different
 * composables declaring at the same index are not the same node. Pass a stable value, usually the
 * composable's own policy class or a marker object.
 */
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
    val layoutDirection = com.awakekt.awake.compose.ui.unit.LocalLayoutDirection.current
    composer.node(
        type = nodeType,
        update = { node ->
            node as LayoutNode
            node.measurePolicy = measurePolicy
            node.modifier = modifier
            node.density = density
            node.fontScale = fontScale
            node.layoutDirection = layoutDirection
        },
        content = content?.let { { it(composer) } },
    )
}

context(composer: Composer)
/**
 * Declares an overlay: measured against the viewport rather than the enclosing constraints.
 *
 * It lands in the declaring node's [LayoutNode.layers] rather than its children, so the parent's
 * measure policy never sees it. See `docs/reference/compose-engine/07-overlay-layering.md`.
 *
 * [modal] bounds the focus ring to this layer: Tab cycles inside a dialog rather than walking out
 * into the page behind it.
 *
 * A [dismissOnOutsideClick] layer consumes an outside press and invokes [onDismissRequest], and a
 * [dismissOnEscape] one does the same for Escape before the focused widget sees the key. They
 * default together because most overlays want both; an alert dialog is the case that does not, and
 * upstream makes exactly that distinction between it and an ordinary dialog.
 */
fun Layer(
    kind: LayerKind,
    modifier: Modifier = Modifier,
    modal: Boolean = false,
    dismissOnOutsideClick: Boolean = false,
    dismissOnEscape: Boolean = dismissOnOutsideClick,
    onDismissRequest: (() -> Unit)? = null,
    positionProvider: LayerPositionProvider? = null,
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
            node.dismissOnOutsideClick = dismissOnOutsideClick
            node.dismissOnEscape = dismissOnEscape
            node.onDismissRequest = onDismissRequest
            node.layerPositionProvider = positionProvider
        },
        content = content?.let { { it(composer) } },
    )
}

/** Paint and hit-test order. Within a kind, ordering is the order layers were declared. */
enum class LayerKind {
    /** Anchored overlays such as menus and popovers; painted first, so below the other kinds. */
    Popup,

    /** Dialogs and similar overlays; painted above popups. */
    Dialog,

    /** Hover hints; painted above dialogs. */
    Tooltip,

    /** Transient notifications; painted last, above every other kind. */
    Toast,
}

/**
 * A viewport-aware position for a [Layer], expressed relative to its declaring node.
 *
 * @property x Horizontal offset from the declaring node's left edge, in pixels.
 * @property y Vertical offset from the declaring node's top edge, in pixels.
 */
data class LayerPosition(val x: Int, val y: Int)

/**
 * Computes a layer's placement after both its content and its declaring node have been laid out.
 *
 * This is the engine seam behind anchored popups: the provider can align to a trigger's global
 * bounds and clamp the result to the viewport without making popup content participate in parent
 * layout.
 */
fun interface LayerPositionProvider {
    /**
     * Returns where to place a measured layer, relative to its declaring node.
     *
     * @param parentX Tree-space x of the declaring node, in pixels.
     * @param parentY Tree-space y of the declaring node, in pixels.
     * @param layerWidth Measured width of the layer, in pixels.
     * @param layerHeight Measured height of the layer, in pixels.
     * @param viewportWidth Width of the viewport the layer was measured against, in pixels.
     * @param viewportHeight Height of the viewport the layer was measured against, in pixels.
     * @return The offset of the layer's top-left corner from the declaring node's top-left corner.
     */
    fun position(
        parentX: Int,
        parentY: Int,
        layerWidth: Int,
        layerHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ): LayerPosition
}

/**
 * Builds or updates [root]'s subtree from [content], reusing every node that still matches.
 *
 * Call once per frame. Nodes survive across calls -- that is the whole point of a retained tree, and
 * what `ui-core` cannot do because it has no tree to keep.
 */
fun composeInto(root: LayoutNode, content: context(Composer) () -> Unit) {
    reconcile(LayoutNodeApplier(root), root, content)
}

/** Retains runtime-only restart scopes while composing one [root] across frames. */
class LayoutComposition(root: LayoutNode) {
    private val composition = Composition(LayoutNodeApplier(root), root)

    /**
     * Runs one pass of [content] against the retained tree, reusing nodes that still match, and
     * returns the [Composer] it ran with.
     */
    fun compose(content: context(Composer) () -> Unit): Composer = composition.reconcile(content)
}
