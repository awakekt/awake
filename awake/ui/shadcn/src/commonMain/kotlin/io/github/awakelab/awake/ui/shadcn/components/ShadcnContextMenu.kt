/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "MatchingDeclarationName", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.gestures.onSecondaryPress
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.BoxMeasurePolicy
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.draw.alpha
import io.github.awakelab.awake.compose.ui.layout.Layer
import io.github.awakelab.awake.compose.ui.layout.LayerKind
import io.github.awakelab.awake.compose.ui.layout.onPlaced

/**
 * shadcn's `ContextMenu`: the same menu as a dropdown, opened by a right-click at the pointer.
 *
 * The difference from every other popup here is what it anchors to. A dropdown hangs off its
 * trigger's box; a context menu hangs off the point that was clicked, which is why the anchor it
 * builds is a zero-sized one at the pointer -- the shared provider then flips and clamps it against
 * the viewport exactly as it does for a trigger.
 *
 * [content] is whatever the menu belongs to. It is wrapped, not replaced, so the right-click target
 * is the caller's own subtree.
 */
/**
 * `ShadcnContextMenu`: Displays a menu to the user — such as a set of actions or functions — triggered by a right-click.
 *
 * **Tailwind Reference**: `z-50 min-w-[8rem] overflow-hidden rounded-md border bg-popover p-1 text-popover-foreground shadow-md animate-in fade-in-80`.
 */
context(_: Composer)
fun ShadcnContextMenu(
    entries: List<ShadcnMenuEntry>,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    menuModifier: Modifier = Modifier,
    id: String? = null,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    shadcnContextMenu(
        entries = entries,
        onItemSelected = onItemSelected,
        modifier = modifier,
        menuModifier = menuModifier,
        id = id,
        content = content,
    )
}

context(_: Composer)
fun shadcnContextMenu(
    entries: List<ShadcnMenuEntry>,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    menuModifier: Modifier = Modifier,
    id: String? = null,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    val state = remember { ContextMenuState() }
    val origin = remember { PopupAnchor() }

    Box(
        modifier
            .onPlaced { x, y, _, _ ->
                origin.x = x
                origin.y = y
            }
            // Local coordinates, so the caller's own placed origin turns them into root space.
            .onSecondaryPress { x, y ->
                state.anchor.x = origin.x + x
                state.anchor.y = origin.y + y
                state.open = true
            },
    ) {
        content?.let { it() }
        val alpha = rememberOverlayAlpha(state.open)
        if (isPresent(state.open, alpha)) {
            Layer(
                kind = LayerKind.Popup,
                dismissOnOutsideClick = true,
                onDismissRequest = { state.open = false },
                // A zero-sized anchor at the pointer: "below" a point is at the point.
                positionProvider = remember {
                    AnchoredBelowPositionProvider(
                        state.anchor,
                        gap = 0,
                    )
                },
                measurePolicy = BoxMeasurePolicy(),
            ) {
                ShadcnDropdownMenu(
                    entries,
                    modifier = menuModifier.alpha(alpha),
                    id = id,
                )?.let { selected ->
                    onItemSelected(selected)
                    state.open = false
                }
            }
        }
    }
}

/**
 * Where the menu was opened, and whether it is.
 *
 * The anchor is held here rather than rebuilt per press so the position provider, which is
 * remembered once, keeps reading the same instance.
 */
private class ContextMenuState {
    val anchor = PopupAnchor()
    var open: Boolean = false
}
