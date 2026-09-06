/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.layout.Layer
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

/**
 * `ShadcnMenubar`: A desktop window top menu bar with cascading dropdown menus.
 *
 * `rounded-md border bg-background p-1 shadow-sm`.
 */
context(_: Composer)
fun ShadcnMenubar(
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    val theme = shadcnTheme
    val baseModifier = if (bordered) {
        modifier
            .background(theme.palette.background, theme.radii.md)
            .border(1f.dp, theme.palette.border, theme.radii.md)
            .padding(Tw.Spacing.s1)
    } else {
        modifier
    }
    Row(
        baseModifier,
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content?.let { it() }
    }
}

/**
 * Individual menu trigger inside a [ShadcnMenubar].
 */
context(_: Composer)
fun ShadcnMenubarMenu(
    title: String,
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    entries: List<ShadcnMenuEntry>,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    id: String? = null,
) {
    val anchor = remember { PopupAnchor() }
    val density = LocalDensity.current
    val alpha = rememberOverlayAlpha(isOpen)

    Box(modifier.popupAnchor(anchor)) {
        ShadcnButton(
            label = title,
            variant = if (isOpen) ShadcnButtonVariant.Secondary else ShadcnButtonVariant.Ghost,
            size = ShadcnButtonSizeVariant.Sm,
            onClick = { onOpenChange(!isOpen) },
        )
        if (isPresent(isOpen, alpha)) {
            Layer(
                kind = LayerKind.Popup,
                dismissOnOutsideClick = true,
                onDismissRequest = { onOpenChange(false) },
                positionProvider = remember(density) {
                    AnchoredBelowPositionProvider(anchor, (MenubarGap.value * density).toInt())
                },
                measurePolicy = BoxMeasurePolicy(),
            ) {
                ShadcnDropdownMenu(
                    entries = entries,
                    modifier = Modifier.alpha(alpha).semantics {
                        if (id != null) this[SemanticsProperties.TestTag] = id
                    },
                )?.let { selectedIndex ->
                    onItemSelected(selectedIndex)
                    onOpenChange(false)
                }
            }
        }
    }
}

private val MenubarGap: Dp = 4.dp
