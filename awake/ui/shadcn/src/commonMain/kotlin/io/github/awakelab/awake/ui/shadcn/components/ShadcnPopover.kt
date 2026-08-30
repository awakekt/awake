/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.BoxMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.foundation.style.StyleState
import io.github.awakelab.awake.compose.foundation.style.styleable
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.draw.alpha
import io.github.awakelab.awake.compose.ui.layout.Layer
import io.github.awakelab.awake.compose.ui.layout.LayerKind
import io.github.awakelab.awake.compose.ui.platform.LocalDensity
import io.github.awakelab.awake.compose.ui.platform.LocalTextStyle
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.semantics
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's popover content: `w-72 rounded-md border bg-popover p-4 text-popover-foreground`.
 *
 * **`w-72` is a fixed width, not a minimum.** A popover is 288px wide whatever is in it, which is
 * what keeps a row of them aligned; `widthIn(min = )` would let each one size to its content and
 * look ragged.
 *
 * This is the content only. Anchoring to a trigger and the open/close lifecycle belong to the
 * overlay layer, which owns them for every floating component rather than each one inventing its
 * own -- see `07-overlay-layering`.
 */
context(_: Composer)
fun shadcnPopover(
    modifier: Modifier = Modifier,
    /**
     * `w-72` by default, and a parameter because upstream treats it as one: the popover case in the
     * reference app is `className="w-[260px]"`. A fixed width with no way to state another forces
     * every caller that wants a different one to reach around the recipe.
     */
    width: Dp = PopoverWidth,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    val theme = shadcnTheme
    val style = remember(theme) { theme.popoverSurfaceStyle(Tw.Spacing.s4) }
    Box(modifier.width(width).styleable(StyleState.Default, style)) {
        // `text-popover-foreground` on the panel, which children inherit. `styleable` records a
        // style's text colour for `resolveTextColor` to read deliberately -- it does not propagate --
        // so without this the body fell through to the engine's default light grey, which is
        // near-invisible on a light popover and which only looking at a render would catch.
        val body = content
        if (body != null) {
            CompositionLocalProvider(
                LocalTextStyle provides
                    LocalTextStyle.current.copy(color = theme.palette.popoverForeground),
            ) {
                body()
            }
        }
    }
}

/**
 * A controlled popover anchored below [trigger].
 *
 * The panel is a layer, so it neither affects the trigger's layout nor is clipped by an ancestor.
 * The caller owns [visible]; pressing outside, pressing Escape, or pressing the trigger again asks
 * to close. Upstream builds this on the same primitive as the dropdown menu, and so does this --
 * the only difference is what goes inside.
 */
context(_: Composer)
fun shadcnPopover(
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    panelModifier: Modifier = Modifier,
    width: Dp = PopoverWidth,
    id: String? = null,
    trigger: context(Composer) (onClick: () -> Unit) -> Unit,
    content: (context(Composer) () -> Unit)? = null,
) {
    val anchor = remember { PopupAnchor() }
    val density = LocalDensity.current
    val alpha = rememberOverlayAlpha(visible)
    Box(modifier.popupAnchor(anchor)) {
        trigger { onVisibleChange(!visible) }
        if (isPresent(visible, alpha)) {
            Layer(
                kind = LayerKind.Popup,
                dismissOnOutsideClick = true,
                onDismissRequest = { onVisibleChange(false) },
                positionProvider = remember(density) {
                    AnchoredBelowPositionProvider(anchor, (PopoverGap.value * density).toInt())
                },
                measurePolicy = BoxMeasurePolicy(),
            ) {
                shadcnPopover(
                    modifier = panelModifier.alpha(alpha).semantics {
                        if (id != null) this[SemanticsProperties.TestTag] = id
                    },
                    width = width,
                    content = content,
                )
            }
        }
    }
}

/** shadcn's `w-72`. */
private val PopoverWidth: Dp = 288.dp

/** `sideOffset={4}`, the same gap the dropdown uses. */
private val PopoverGap: Dp = 4.dp
