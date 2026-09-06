/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.ColumnScope
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.RowScope
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.StyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.ShadcnThemeValues
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

@DslMarker
annotation class ShadcnCardDsl

/**
 * DSL Scope for composing card sections inside [ShadcnCard].
 */
@ShadcnCardDsl
class ShadcnCardScope internal constructor(
    private val columnScope: ColumnScope,
) : ColumnScope by columnScope {

    context(_: Composer)
    fun header(
        modifier: Modifier = Modifier,
        content: context(Composer) RowScope.() -> Unit,
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.Top,
        ) {
            content()
        }
    }

    context(_: Composer)
    fun content(
        modifier: Modifier = Modifier,
        content: context(Composer) ColumnScope.() -> Unit,
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
            content()
        }
    }

    context(_: Composer)
    fun footer(
        modifier: Modifier = Modifier,
        content: context(Composer) RowScope.() -> Unit,
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

/**
 * `ShadcnCard`: Bordered, filled container panel for grouping related content and actions.
 *
 * **Tailwind Reference**: `flex flex-col gap-6 rounded-xl border bg-card py-6 text-card-foreground shadow-sm`.
 *
 * Use cases:
 * - Login forms, settings cards, summary panels, item feature cards.
 *
 * **Example Usage**:
 * ```kotlin
 * ShadcnCard(Modifier.width(360.dp)) {
 *     header {
 *         ShadcnText("Security Overview", variant = ShadcnTextVariant.H3)
 *     }
 *     content {
 *         ShadcnText("Manage 2FA and active tokens.")
 *     }
 *     footer {
 *         ShadcnButton("Save Changes")
 *     }
 * }
 * ```
 *
 * @param modifier Custom layout modifier applied to the card surface.
 * @param contentPadding Inner vertical padding (`Tw.Spacing.s6` by default).
 * @param content Card body content slot with [ShadcnCardScope] access.
 *
 * Keywords: card, panel, surface, card container, card header, card content, card footer.
 */
context(_: Composer)
fun ShadcnCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = Tw.Spacing.s6,
    content: (
        context(Composer)
        ShadcnCardScope.() -> Unit
    )? = null,
) {
    val theme = shadcnTheme
    val style = remember(theme, contentPadding) { theme.cardStyle(contentPadding) }
    Box(modifier.styleable(StyleState.Default, style)) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s6),
        ) {
            if (content != null) {
                val scope = remember(this) { ShadcnCardScope(this) }
                scope.content()
            }
        }
    }
}

/**
 * The card's visual contract as a [Style] rather than a modifier chain.
 */
private fun ShadcnThemeValues.cardStyle(contentPadding: Dp): Style = Style {
    background(palette.card)
    border(CardBorderWidth, palette.border)
    cornerRadius(radii.xl)
    textColor(palette.cardForeground)
    val horizontal = if (contentPadding > 0.dp) contentPadding + CardBorderWidth else CardBorderWidth
    val vertical = if (contentPadding > 0.dp) contentPadding + CardBorderWidth else CardBorderWidth
    contentPadding(
        horizontal = horizontal,
        vertical = vertical,
    )
}

private val CardBorderWidth: Dp = 1.dp
