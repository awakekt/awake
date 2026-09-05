/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.BoxMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.draw.alpha
import io.github.awakelab.awake.compose.ui.layout.Layer
import io.github.awakelab.awake.compose.ui.layout.LayerKind
import io.github.awakelab.awake.compose.ui.platform.LocalDensity
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.semantics
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.tailwind.Tw

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
