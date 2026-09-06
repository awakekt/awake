/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.hoverable
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.style.StyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.layout.Layer
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * `ShadcnHoverCard`: A hover-triggered preview card popover.
 *
 * `w-80 rounded-md border bg-popover p-4 text-popover-foreground shadow-md`.
 */
context(_: Composer)
fun ShadcnHoverCardContent(
    modifier: Modifier = Modifier,
    width: Dp = HoverCardWidth,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    val theme = shadcnTheme
    val style = remember(theme) { theme.popoverSurfaceStyle(Tw.Spacing.s4) }
    Box(modifier.width(width).styleable(StyleState.Default, style)) {
        val body = content
        if (body != null) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(color = theme.palette.popoverForeground),
            ) {
                body()
            }
        }
    }
}

/**
 * Hover-triggered popover card anchored below [trigger].
 */
context(_: Composer)
fun ShadcnHoverCard(
    modifier: Modifier = Modifier,
    width: Dp = HoverCardWidth,
    id: String? = null,
    trigger: context(Composer) () -> Unit,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    val interaction = remember { InteractionSource() }
    val cardInteraction = remember { InteractionSource() }
    val anchor = remember { PopupAnchor() }
    val density = LocalDensity.current

    val isOpen = interaction.isHovered || cardInteraction.isHovered
    val alpha = rememberOverlayAlpha(isOpen)

    Box(
        modifier
            .popupAnchor(anchor)
            .hoverable(interaction),
    ) {
        trigger()
        if (isPresent(isOpen, alpha)) {
            Layer(
                kind = LayerKind.Popup,
                positionProvider = remember(density) {
                    AnchoredBelowPositionProvider(anchor, (HoverCardGap.value * density).toInt())
                },
                measurePolicy = BoxMeasurePolicy(),
            ) {
                Box(Modifier.hoverable(cardInteraction)) {
                    ShadcnHoverCardContent(
                        modifier = Modifier.alpha(alpha).semantics {
                            if (id != null) this[SemanticsProperties.TestTag] = id
                        },
                        width = width,
                        content = content,
                    )
                }
            }
        }
    }
}

private val HoverCardWidth: Dp = 320.dp
private val HoverCardGap: Dp = 4.dp
