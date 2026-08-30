/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.foundation.style.Style
import io.github.awakelab.awake.compose.foundation.style.StyleState
import io.github.awakelab.awake.compose.foundation.style.styleable
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.platform.LocalTextStyle
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.math2d.sp
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.ShadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's dialog panel: `bg-background rounded-lg border p-6 shadow-lg gap-4`.
 *
 * The panel only. Upstream's `Dialog` is the panel plus an overlay, a portal and focus capture, and
 * those belong to whatever owns the overlay layer -- a recipe that dimmed the screen from inside
 * itself would be un-composable with any other overlay. `07-overlay-layering.md` is where that lives.
 *
 * `DialogHeader` is `flex flex-col gap-2` and `DialogFooter` is `flex justify-end`, which is why the
 * title and description sit closer to each other than the footer does to either.
 */
context(_: Composer)
fun shadcnDialog(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    width: Dp = DialogWidth,
    actions: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    val theme = shadcnTheme
    val style = remember(theme) { theme.dialogStyle() }

    Box(modifier.width(width).styleable(StyleState.Default, style)) {
        // Children inherit `text-foreground` from the panel. `styleable` records a style's text
        // colour for `resolveTextColor` rather than propagating it, so without this the body takes
        // the engine's default grey -- the same gap the popover had.
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(color = theme.palette.foreground),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(DialogSectionGap)) {
                Column(verticalArrangement = Arrangement.spacedBy(DialogHeaderGap)) {
                    // `leading-none` on the title: its line box is the font size, not the `text-lg` pair's 28.
                    ShadcnText(title, variant = ShadcnTextVariant.Large, lineHeight = DialogTitleLeading)
                    if (description != null) {
                        ShadcnText(description, variant = ShadcnTextVariant.Muted)
                    }
                }
                val footer = actions
                if (footer != null) {
                    // `justify-end` -- the footer's buttons sit against the right edge.
                    Box(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) { footer() }
                }
            }
        }
    }
}

internal fun ShadcnThemeValues.dialogStyle(): Style = Style {
    background(palette.background)
    border(DialogBorderWidth, palette.border)
    cornerRadius(radii.lg)
    // `p-6`, with the border folded in -- shadcn is `border-box` and Awake's border reserves nothing.
    contentPadding(Tw.Spacing.s6 + DialogBorderWidth)
}

/** `leading-none` -- equal to `text-lg`'s own 18px size. */
private val DialogTitleLeading = 18f.sp

/** shadcn's `sm:max-w-lg`, narrowed to the width the parity capture pins. */
private val DialogWidth: Dp = 320.dp

/** `gap-4` between header and footer. */
private val DialogSectionGap: Dp = Tw.Spacing.s4

/** `gap-2` inside the header. */
private val DialogHeaderGap: Dp = Tw.Spacing.s2

/** Tailwind's bare `border` is 1px; the width scale is not generated -- see `ShadcnCard`. */
private val DialogBorderWidth: Dp = 1.dp
