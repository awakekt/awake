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
import com.awakekt.awake.compose.foundation.layout.padding
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
    // Upstream's alert dialog answers Escape and deliberately ignores its backdrop: a destructive
    // question must not be answerable by clicking away from it. That is the whole difference from
    // an ordinary dialog, and the reason the shared layer takes the choice as a parameter.
    shadcnModalLayer(
        visible = visible,
        alignment = Alignment.Center,
        onDismissRequest = onDismissRequest,
        scrimModifier = scrimModifier(id, onScrimClick = null),
    ) {
        alertDialogPanel(
            AlertDialogSpec(
                title = title,
                modifier = modifier,
                description = description,
                actions = AlertDialogActionsSpec(
                    onDismissRequest = onDismissRequest,
                    confirmLabel = confirmLabel,
                    cancelLabel = cancelLabel,
                    destructive = destructive,
                    onConfirm = onConfirm,
                ),
                id = id,
            ),
        )
    }
}

private class AlertDialogSpec(
    val title: String,
    val modifier: Modifier,
    val description: String?,
    val actions: AlertDialogActionsSpec,
    val id: String?,
)

private class AlertDialogActionsSpec(
    val onDismissRequest: () -> Unit,
    val confirmLabel: String,
    val cancelLabel: String,
    val destructive: Boolean,
    val onConfirm: () -> Unit,
)

context(_: Composer)
private fun alertDialogPanel(spec: AlertDialogSpec) {
    val theme = shadcnTheme
    val style = remember(theme) { theme.dialogStyle() }
    // The upstream `w-full max-w-[calc(100%-2rem)]` leaves a 16dp gutter on narrow windows.
    // Keep that gutter in a wrapper so the tagged, painted panel reports its real bounds.
    Box(Modifier.padding(horizontal = AlertDialogViewportMargin)) {
        Box(
            spec.modifier
                .widthIn(max = AlertDialogMaxWidth)
                .fillMaxWidth()
                .styleable(StyleState.Default, style)
                .semantics {
                    this[SemanticsProperties.Role] = SemanticsRole.Dialog
                    this[SemanticsProperties.Label] = spec.title
                    if (spec.id != null) this[SemanticsProperties.TestTag] = spec.id
                },
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(color = theme.palette.foreground),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AlertDialogSectionGap)) {
                    alertDialogHeader(spec.title, spec.description)
                    alertDialogActions(spec)
                }
            }
        }
    }
}

context(_: Composer)
private fun alertDialogHeader(title: String, description: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(AlertDialogHeaderGap)) {
        ShadcnText(title, variant = ShadcnTextVariant.Large, lineHeight = AlertDialogTitleLeading)
        if (description != null) ShadcnText(description, variant = ShadcnTextVariant.Muted)
    }
}

context(_: Composer)
private fun alertDialogActions(spec: AlertDialogSpec) {
    // `sm:justify-end`, cancel before action -- upstream stacks them reversed on a narrow viewport.
    Box(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Row(horizontalArrangement = Arrangement.spacedByHorizontal(AlertDialogActionGap)) {
            ShadcnButton(
                spec.actions.cancelLabel,
                variant = ShadcnButtonVariant.Outline,
                onClick = spec.actions.onDismissRequest,
                modifier = Modifier.taggedAction(spec.id, "cancel"),
            )
            ShadcnButton(
                spec.actions.confirmLabel,
                variant = if (spec.actions.destructive) ShadcnButtonVariant.Destructive else ShadcnButtonVariant.Default,
                onClick = spec.actions.onConfirm,
                modifier = Modifier.taggedAction(spec.id, "confirm"),
            )
        }
    }
}

private fun Modifier.taggedAction(id: String?, name: String): Modifier =
    if (id == null) this else semantics { this[SemanticsProperties.TestTag] = "$id.$name" }

/** `sm:max-w-lg`. */
private val AlertDialogMaxWidth: Dp = 512.dp
private val AlertDialogViewportMargin: Dp = Tw.Spacing.s4
private val AlertDialogSectionGap: Dp = Tw.Spacing.s4
private val AlertDialogHeaderGap: Dp = Tw.Spacing.s2
private val AlertDialogActionGap: Dp = Tw.Spacing.s2

/** `text-lg` with `leading-none`: the title's line box is its font size. */
private val AlertDialogTitleLeading = 18f.sp
