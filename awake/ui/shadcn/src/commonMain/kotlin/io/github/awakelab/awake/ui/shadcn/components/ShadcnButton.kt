/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.hoverable
import io.github.awakelab.awake.compose.foundation.BorderSides
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.foundation.style.Style
import io.github.awakelab.awake.compose.foundation.style.rememberStyleState
import io.github.awakelab.awake.compose.foundation.style.styleable
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.graphics.Shape
import io.github.awakelab.awake.compose.ui.graphics.vector.ImageVector
import io.github.awakelab.awake.compose.ui.platform.LocalTextStyle
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's button.
 *
 * The first ported recipe with real interaction, so it is where `Style`'s state rules earn
 * themselves: rest, hover, press and disabled are four branches inside one style rather than four
 * things a caller assembles.
 *
 * **Destructive's label is `text-white`, literally** -- the fourth place upstream hardcodes white
 * where a semantic token would look right, after the slider thumb and the badge. Treat any
 * `-white` in a shadcn class string as deliberate.
 *
 * Press is Awake's, not upstream's: `button.tsx` has no `active:` class, because a browser gives a
 * pressed affordance for free and a canvas does not. Darkening on press is the smallest thing that
 * restores it, and it is marked here so nobody hunts for the class it came from.
 */
context(_: Composer)
fun ShadcnButton(
    label: String,
    modifier: Modifier = Modifier,
    variant: ShadcnButtonVariant = ShadcnButtonVariant.Default,
    size: ShadcnButtonSizeVariant = ShadcnButtonSizeVariant.Default,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    shape: Shape? = null,
    leadingIcon: ImageVector? = null,
) = shadcnButtonContent(modifier, variant, size, enabled, onClick, shape, BorderSides.All, hasIcon = leadingIcon != null) {
    if (leadingIcon != null) shadcnIcon(leadingIcon)
    ShadcnText(label, variant = size.text, color = shadcnTheme.buttonForeground(variant))
}

/**
 * A button holding arbitrary content.
 *
 * Upstream's `Button` renders its children, and an icon-only button is `size="icon"` with an svg
 * inside. The label overload above is the common case spelled shorter; without this one an icon
 * button was not expressible at all, which is what Studio's display rail needed.
 */
context(_: Composer)
fun ShadcnButton(
    modifier: Modifier = Modifier,
    variant: ShadcnButtonVariant = ShadcnButtonVariant.Default,
    size: ShadcnButtonSizeVariant = ShadcnButtonSizeVariant.Default,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    shape: Shape? = null,
    content: context(Composer) () -> Unit,
) = shadcnButtonContent(modifier, variant, size, enabled, onClick, shape, BorderSides.All, hasIcon = false, content)

context(_: Composer)
internal fun shadcnButtonContent(
    modifier: Modifier,
    variant: ShadcnButtonVariant,
    size: ShadcnButtonSizeVariant,
    enabled: Boolean,
    onClick: () -> Unit,
    shape: Shape?,
    borderSides: BorderSides,
    hasIcon: Boolean,
    content: context(Composer) () -> Unit,
) {
    val theme = shadcnTheme
    val interaction = remember { InteractionSource() }
    val state = rememberStyleState(interaction, enabled = enabled)
    val style = remember(theme, variant) { theme.shadcnButtonStyle(variant) }

    val sized = if (size.square) {
        modifier.width(size.height).height(size.height)
    } else {
        modifier.height(size.height)
    }
    val boxModel = Style {
        val inset = if (variant.bordered) ShadcnButtonBorderWidth else Tw.Spacing.s0
        val horizontal = if (hasIcon && !size.square) ShadcnButtonIconPadding else size.paddingX
        contentPadding(horizontal + inset, size.paddingY + inset)
    }
    val interactive = sized
            // The border is folded into the inset, not added beside it. shadcn is `border-box`, so
            // an outline button's 1px border consumes layout space and the button ends up 2px wider
            // than the same label unbordered; Awake's border paints inside the bounds and reserves
            // nothing, which left outline 2.53px narrow against the reference while every unbordered
            // variant matched. Same fix as `popoverSurfaceStyle`.
            .hoverable(interaction, enabled = enabled)
            .clickable(interaction) { if (enabled) onClick() }
    val surface = if (shape == null) {
        interactive.styleable(state, borderSides, style, boxModel)
    } else {
        interactive.styleable(state, borderSides, style, boxModel, Style { shape(shape) })
    }
    Box(
        surface,
        // `inline-flex items-center justify-center` on upstream's shared class. Without it a label
        // sits top-left, which only shows on the fixed-square icon sizes -- the text sizes hug their
        // content and hide the omission.
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Upstream's `text-primary-foreground` cascades to both text and SVG `currentColor`.
        // LocalTextStyle is Awake's existing composition-scoped content-colour mechanism; icons
        // consume it unless an explicit tint asks to opt out.
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(color = theme.buttonForeground(variant)),
        ) {
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(ShadcnButtonIconLabelGap)) {
                content()
            }
        }
    }
}
