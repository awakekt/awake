/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
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

/**
 * `ShadcnNavigationMenu`: Header navigation bar with trigger popovers and megamenus.
 */
context(_: Composer)
fun ShadcnNavigationMenu(
    modifier: Modifier = Modifier,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content?.let { it() }
    }
}

/**
 * Trigger button that opens a navigation content panel.
 */
context(_: Composer)
fun ShadcnNavigationMenuTrigger(
    title: String,
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    id: String? = null,
    panelContent: (
        context(Composer)
        () -> Unit
    )? = null,
) {
    val anchor = remember { PopupAnchor() }
    val density = LocalDensity.current
    val alpha = rememberOverlayAlpha(isOpen)

    Box(modifier.popupAnchor(anchor)) {
        ShadcnButton(
            label = title,
            variant = ShadcnButtonVariant.Ghost,
            onClick = { onOpenChange(!isOpen) },
        )
        if (isPresent(isOpen, alpha)) {
            Layer(
                kind = LayerKind.Popup,
                dismissOnOutsideClick = true,
                onDismissRequest = { onOpenChange(false) },
                positionProvider = remember(density) {
                    AnchoredBelowPositionProvider(anchor, (NavMenuGap.value * density).toInt())
                },
                measurePolicy = BoxMeasurePolicy(),
            ) {
                ShadcnPopover(
                    modifier = Modifier.alpha(alpha).semantics {
                        if (id != null) this[SemanticsProperties.TestTag] = id
                    },
                ) {
                    val body = panelContent
                    if (body != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s2)) {
                            body()
                        }
                    }
                }
            }
        }
    }
}

private val NavMenuGap: Dp = 4.dp
