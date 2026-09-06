/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.BorderSides
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.rememberStyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.Shape
import com.awakekt.awake.compose.ui.graphics.vector.ImageVector
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * `ShadcnButton`: Primary action button component.
 *
 * **Tailwind Reference**: `inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors`.
 *
 * Use cases:
 * - Form submit triggers, primary/secondary actions, icon-only buttons.
 *
 * **Example Usage**:
 * ```kotlin
 * ShadcnButton("Submit", variant = ShadcnButtonVariant.Default) { handleSubmit() }
 * ```
 *
 * @param label Button text label.
 * @param modifier Custom layout modifier.
 * @param variant Visual style variant (`Default`, `Destructive`, `Outline`, `Secondary`, `Ghost`, `Link`).
 * @param size Button dimensions (`Default`, `Sm`, `Lg`, `Icon`).
 * @param enabled Whether the button is interactive.
 * @param onClick Click action handler.
 * @param shape Custom corner clip shape.
 * @param leadingIcon Optional leading vector icon.
 *
 * Keywords: button, action, cta, primary button, outline button, ghost button, icon button.
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
) = shadcnButtonContent(
    modifier,
    variant,
    size,
    enabled,
    onClick,
    shape,
    BorderSides.All,
    hasIcon = leadingIcon != null,
) {
    if (leadingIcon != null) ShadcnIcon(leadingIcon)
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
) = shadcnButtonContent(
    modifier,
    variant,
    size,
    enabled,
    onClick,
    shape,
    BorderSides.All,
    hasIcon = false,
    content,
)

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
            LocalTextStyle provides LocalTextStyle.current.copy(
                color = theme.buttonForeground(
                    variant,
                ),
            ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedByHorizontal(ShadcnButtonIconLabelGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        }
    }
}
