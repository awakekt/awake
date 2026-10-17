/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp

/**
 * [element] against its anchor in this box, inside [safeArea] unless it reaches under it, and moved,
 * scaled and faded as the player's [CanvasPlayerLayout] says. A scaled control lays out at a denser
 * dp, so it grows from its anchor and takes touches across all of its new size.
 */
context(_: Composer)
internal fun BoxScope.AnchoredElement(element: CanvasElement, tag: String, tree: CanvasTree, safeArea: CanvasInsets) {
    val inside = if (element.safeArea) safeArea else CanvasInsets.None
    val view = Modifier.size(element.width.dp, element.height.dp).testTag(tag)
    val adjust = tree.controls.playerLayout.adjustOf(element)
    // The inset wraps the element rather than sitting in its modifier chain, so the element's own
    // bounds (hit area, editor picking) are exactly its size.
    if (adjust == null) {
        Box(Modifier.align(element.anchor.alignment).anchorInset(element.anchor, element.offsetX, element.offsetY, inside)) {
            CanvasElementView(element, view, tree, tree.childrenOf(element))
        }
        return
    }
    // A player's offset is right and down on screen; an anchor's offset is inward from its edge.
    val x = element.offsetX + adjust.offsetX * if (element.anchor.column == 2) -1f else 1f
    val y = element.offsetY + adjust.offsetY * if (element.anchor.row == 2) -1f else 1f
    var placed = Modifier.align(element.anchor.alignment).anchorInset(element.anchor, x, y, inside)
    if (adjust.opacity < 1f) placed = placed.alpha(adjust.opacity)
    Box(placed) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides density * adjust.scale) {
            CanvasElementView(element, view, tree, tree.childrenOf(element))
        }
    }
}
