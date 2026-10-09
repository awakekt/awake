/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.hovered
import com.awakekt.awake.compose.foundation.style.rememberStyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.layout.Layer
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.layout.LayerPosition
import com.awakekt.awake.compose.ui.layout.LayerPositionProvider
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.ShadcnThemeValues
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * A scrimmed modal layer: `bg-black/50` over everything, one panel aligned within it.
 *
 * Shared by the alert dialog, the sheet and the drawer, which differ only in where the panel sits
 * and whether the backdrop closes it.
 *
 * **The scrim is why the backdrop is a click target rather than a layer flag.** `Layer`'s
 * `dismissOnOutsideClick` asks whether a press landed outside the layer's own bounds, and a
 * full-viewport scrim *is* the layer's bounds -- so for anything scrimmed that test is never true.
 * [scrimModifier] carries the working version of the same intent: a scrim built with a click
 * handler closes, one built without does not, which is the difference upstream draws between a
 * dialog and an alert dialog.
 *
 * The scrim's coverage, not [Layer]'s `modal`, is what stops a click reaching the page underneath.
 * Modality still earns its place for focus, which `ModalLayerTest` covers directly.
 */
context(_: Composer)
internal fun shadcnModalLayer(
    visible: Boolean,
    alignment: Alignment,
    onDismissRequest: () -> Unit,
    scrimModifier: Modifier,
    panel: (context(Composer) () -> Unit),
) {
    val theme = shadcnTheme
    // Held open while it fades: the caller no longer removes the subtree, so there is something
    // left to animate. See `rememberOverlayAlpha`.
    val alpha = rememberOverlayAlpha(visible)
    if (!isPresent(visible, alpha)) return
    Layer(
        kind = LayerKind.Dialog,
        modal = true,
        // Inert while a scrim covers the layer; see the note above. Escape is the live one.
        dismissOnOutsideClick = false,
        dismissOnEscape = true,
        onDismissRequest = onDismissRequest,
        // Modal content is portal-like: it owns the viewport, even when it is declared inside a
        // page preview or another nested layout. Anchored popups intentionally keep their parent
        // origin; a scrimmed modal must cancel that origin so its backdrop and panel share the
        // actual window bounds.
        positionProvider = GlobalModalPositionProvider,
        measurePolicy = BoxMeasurePolicy(alignment),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .alpha(alpha)
                .background(theme.palette.overlay)
                .then(scrimModifier),
        )
        Box(Modifier.alpha(alpha)) { panel() }
    }
}

private val GlobalModalPositionProvider = LayerPositionProvider { parentX, parentY, _, _, _, _ ->
    LayerPosition(-parentX, -parentY)
}

/**
 * The backdrop: addressable so a test can press it, and clickable only when it should dismiss.
 *
 * A null [onScrimClick] is the alert dialog, and passing one is a dialog or a sheet. Stated at the
 * call site rather than through a boolean, because it is the one behavioural difference between
 * them and reads better as the presence of a handler than as `dismissOnScrimClick = false`.
 */
internal fun scrimModifier(id: String?, onScrimClick: (() -> Unit)?): Modifier = Modifier
    .let { if (onScrimClick == null) it else it.clickable { onScrimClick() } }
    .semantics { if (id != null) this[SemanticsProperties.TestTag] = "$id.scrim" }

/**
 * The close control upstream's `DialogContent` and `SheetContent` draw unless `showCloseButton` is
 * off: `absolute top-4 right-4 rounded-xs opacity-70 hover:opacity-100` around a `size-4` X, read
 * out as `sr-only` "Close". Addressable as `<id>.close`, like the scrim.
 *
 * [placement] puts it in the corner, since the dialog pads its panel and the sheet its content. It
 * goes on a box around the control rather than on the control: an offset or padding there moves
 * what the control draws but not the bounds a press and a test find it by.
 */
context(_: Composer)
internal fun shadcnOverlayClose(placement: Modifier, id: String?, onClick: () -> Unit) {
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }
    val state = rememberStyleState(interaction)
    val style = remember(theme) { theme.overlayCloseStyle() }
    Box(placement) {
        Box(
            Modifier
                .hoverable(interaction)
                .clickable(interaction) { onClick() }
                .styleable(state, style)
                .semantics {
                    this[SemanticsProperties.Role] = SemanticsRole.Button
                    this[SemanticsProperties.Label] = "Close"
                    if (id != null) this[SemanticsProperties.TestTag] = "$id.close"
                },
        ) {
            ShadcnIcon(ShadcnIcons.x, tint = theme.palette.foreground)
        }
    }
}

internal fun ShadcnThemeValues.overlayCloseStyle(): Style = Style {
    cornerRadius(radii.xs)
    alpha(OVERLAY_CLOSE_ALPHA)
    hovered(Style { alpha(1f) })
}

/** `opacity-70` until hovered. */
private const val OVERLAY_CLOSE_ALPHA = 0.7f

/** `top-4 right-4`: the close control's inset from the panel's edges. */
internal val OverlayCloseInset: Dp = Tw.Spacing.s4
