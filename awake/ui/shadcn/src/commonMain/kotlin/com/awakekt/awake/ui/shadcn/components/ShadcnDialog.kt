/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "MatchingDeclarationName", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.ColumnScope
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.RowScope
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.StyleState
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsRole
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.ShadcnThemeValues
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

@DslMarker
annotation class ShadcnDialogDsl

/**
 * DSL Scope for composing dialog header contents inside [ShadcnDialogScope.header].
 */
@ShadcnDialogDsl
class ShadcnDialogHeaderScope internal constructor(
    private val columnScope: ColumnScope,
) : ColumnScope by columnScope {

    context(_: Composer)
    fun title(
        text: String,
        modifier: Modifier = Modifier,
    ) {
        ShadcnText(
            text = text,
            modifier = modifier,
            variant = ShadcnTextVariant.Large,
            lineHeight = DialogTitleLeading,
        )
    }

    context(_: Composer)
    fun description(
        text: String,
        modifier: Modifier = Modifier,
    ) {
        ShadcnText(
            text = text,
            modifier = modifier,
            variant = ShadcnTextVariant.Muted,
        )
    }
}

/**
 * DSL Scope for composing dialog sections inside [ShadcnDialog].
 */
@ShadcnDialogDsl
class ShadcnDialogScope internal constructor(
    private val columnScope: ColumnScope,
) : ColumnScope by columnScope {

    context(_: Composer)
    fun header(
        modifier: Modifier = Modifier,
        content: context(Composer) ShadcnDialogHeaderScope.() -> Unit,
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DialogHeaderGap),
        ) {
            val scope = remember(this) { ShadcnDialogHeaderScope(this) }
            scope.content()
        }
    }

    context(_: Composer)
    fun title(
        text: String,
        modifier: Modifier = Modifier,
    ) {
        ShadcnText(
            text = text,
            modifier = modifier,
            variant = ShadcnTextVariant.Large,
            lineHeight = DialogTitleLeading,
        )
    }

    context(_: Composer)
    fun description(
        text: String,
        modifier: Modifier = Modifier,
    ) {
        ShadcnText(
            text = text,
            modifier = modifier,
            variant = ShadcnTextVariant.Muted,
        )
    }

    context(_: Composer)
    fun content(
        modifier: Modifier = Modifier,
        content: context(Composer) ColumnScope.() -> Unit,
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DialogSectionGap),
        ) {
            content()
        }
    }

    context(_: Composer)
    fun footer(
        modifier: Modifier = Modifier,
        content: context(Composer) RowScope.() -> Unit,
    ) {
        Box(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedByHorizontal(DialogActionGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        }
    }
}

/**
 * `ShadcnDialog`: Modal dialog overlay with full-viewport animated scrim backdrop.
 *
 * **Tailwind Reference**: `fixed inset-0 z-50 bg-black/80 data-[state=open]:animate-in data-[state=closed]:animate-out`.
 *
 * Use cases:
 * - Studio marketplace extensions browser, pro licensing dialog, modal settings dialogs.
 *
 * **Example Usage**:
 * ```kotlin
 * ShadcnDialog(visible = isDialogOpen, onDismissRequest = { isDialogOpen = false }) {
 *     header {
 *         title("Edit profile")
 *         description("Make changes to your profile here.")
 *     }
 *     content {
 *         // Dialog body content
 *     }
 *     footer {
 *         ShadcnButton("Save Changes", onClick = { isDialogOpen = false })
 *     }
 * }
 * ```
 *
 * @param visible Controls whether the dialog is displayed.
 * @param onDismissRequest Callback invoked when clicking the scrim backdrop or pressing Escape.
 * @param modifier Custom layout modifier for the panel container.
 * @param width Width of the dialog container (defaults to `sm:max-w-lg` 512.dp).
 * @param id Optional test tag or identifier.
 * @param content The composable dialog body slot with [ShadcnDialogScope] receiver.
 *
 * Keywords: dialog, modal, popup, scrim, overlay, window.
 */
context(_: Composer)
fun ShadcnDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = DialogWidth,
    id: String? = null,
    content: (
        context(Composer)
        ShadcnDialogScope.() -> Unit
    )? = null,
) {
    val theme = shadcnTheme
    val style = remember(theme) { theme.dialogStyle() }

    shadcnModalLayer(
        visible = visible,
        alignment = Alignment.Center,
        onDismissRequest = onDismissRequest,
        scrimModifier = scrimModifier(id, onScrimClick = onDismissRequest),
    ) {
        Box(
            modifier = modifier
                .width(width)
                .styleable(StyleState.Default, style)
                .semantics {
                    this[SemanticsProperties.Role] = SemanticsRole.Dialog
                    if (id != null) this[SemanticsProperties.TestTag] = id
                },
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(color = theme.palette.foreground),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DialogSectionGap),
                ) {
                    if (content != null) {
                        val scope = remember(this) { ShadcnDialogScope(this) }
                        scope.content()
                    }
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

/** shadcn's `sm:max-w-lg`. */
private val DialogWidth: Dp = 512.dp

/** `gap-4` between header and footer. */
private val DialogSectionGap: Dp = Tw.Spacing.s4

/** `gap-2` inside the header. */
private val DialogHeaderGap: Dp = Tw.Spacing.s2

/** `gap-2` between footer actions. */
private val DialogActionGap: Dp = Tw.Spacing.s2

/** Tailwind's bare `border` is 1px; the width scale is not generated -- see `ShadcnCard`. */
private val DialogBorderWidth: Dp = 1.dp
