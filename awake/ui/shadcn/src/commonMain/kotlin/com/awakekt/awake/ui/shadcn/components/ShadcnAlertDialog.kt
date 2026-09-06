/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.widthIn
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
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's `AlertDialog`: a modal that interrupts and requires an explicit answer.
 *
 * The difference from `Dialog` is not styling, it is dismissal. Upstream's alert dialog does not
 * close when its backdrop is clicked -- the whole point is that a destructive question cannot be
 * answered by clicking away from it -- while Escape still closes. That distinction is why
 * [Layer] takes `dismissOnEscape` separately from `dismissOnOutsideClick`.
 *
 * Nothing here is shown unless [visible]. The layer is the overlay seam: its content is measured
 * against the viewport rather than the caller's constraints, so an alert dialog declared deep in a
 * page still covers the page.
 */
context(_: Composer)
fun ShadcnAlertDialog(
    visible: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    confirmLabel: String = "Continue",
    cancelLabel: String = "Cancel",
    destructive: Boolean = false,
    onConfirm: () -> Unit = {},
    id: String? = null,
) {
    val theme = shadcnTheme
    val style = remember(theme) { theme.dialogStyle() }

    // Upstream's alert dialog answers Escape and deliberately ignores its backdrop: a destructive
    // question must not be answerable by clicking away from it. That is the whole difference from
    // an ordinary dialog, and the reason the shared layer takes the choice as a parameter.
    shadcnModalLayer(
        visible = visible,
        alignment = Alignment.Center,
        onDismissRequest = onDismissRequest,
        scrimModifier = scrimModifier(id, onScrimClick = null),
    ) {
        Box(
            modifier
                .fillMaxWidth()
                .widthIn(max = AlertDialogMaxWidth)
                .styleable(StyleState.Default, style)
                .semantics {
                    this[SemanticsProperties.Role] = SemanticsRole.Dialog
                    this[SemanticsProperties.Label] = title
                    if (id != null) this[SemanticsProperties.TestTag] = id
                },
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(color = theme.palette.foreground),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AlertDialogSectionGap)) {
                    Column(verticalArrangement = Arrangement.spacedBy(AlertDialogHeaderGap)) {
                        ShadcnText(
                            title,
                            variant = ShadcnTextVariant.Large,
                            lineHeight = AlertDialogTitleLeading,
                        )
                        if (description != null) {
                            ShadcnText(description, variant = ShadcnTextVariant.Muted)
                        }
                    }
                    // `sm:justify-end`, cancel before action -- upstream stacks them reversed on a
                    // narrow viewport, which this does not model yet.
                    Box(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Row(
                            horizontalArrangement = Arrangement.spacedByHorizontal(
                                AlertDialogActionGap,
                            ),
                        ) {
                            ShadcnButton(
                                cancelLabel,
                                variant = ShadcnButtonVariant.Outline,
                                onClick = onDismissRequest,
                                modifier = Modifier.taggedAction(id, "cancel"),
                            )
                            ShadcnButton(
                                confirmLabel,
                                variant = if (destructive) ShadcnButtonVariant.Destructive else ShadcnButtonVariant.Default,
                                onClick = onConfirm,
                                modifier = Modifier.taggedAction(id, "confirm"),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.taggedAction(id: String?, name: String): Modifier =
    if (id == null) this else semantics { this[SemanticsProperties.TestTag] = "$id.$name" }

/** `sm:max-w-lg`. */
private val AlertDialogMaxWidth: Dp = 512.dp
private val AlertDialogSectionGap: Dp = Tw.Spacing.s4
private val AlertDialogHeaderGap: Dp = Tw.Spacing.s2
private val AlertDialogActionGap: Dp = Tw.Spacing.s2

/** `text-lg` with `leading-none`: the title's line box is its font size. */
private val AlertDialogTitleLeading = 18f.sp
