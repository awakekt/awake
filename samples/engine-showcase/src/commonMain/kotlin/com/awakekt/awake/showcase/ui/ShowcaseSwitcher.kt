/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.showcase.EngineShowcase
import com.awakekt.awake.showcase.ShowcaseSelection
import com.awakekt.awake.ui.shadcn.components.ShadcnSidebar
import com.awakekt.awake.ui.shadcn.components.ShadcnSidebarMenu
import com.awakekt.awake.ui.shadcn.components.ShadcnSidebarMenuItem
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

/** The shared desktop and mobile navigation. Only its menu scrolls; header and footer stay pinned. */
context(_: Composer)
internal fun ShowcaseSwitcher(
    selection: ShowcaseSelection,
    showcases: List<EngineShowcase>,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onSelect: () -> Unit = {},
) {
    ShadcnSidebar(
        modifier = modifier.testTag(ShowcaseSwitcherTags.PANEL),
        header = {
            Row(Modifier.fillMaxWidth()) {
                ShadcnText("Awake", Modifier.weight(1f), variant = ShadcnTextVariant.H4)
                ShowcaseCloseButton("Close navigation", ShowcaseChromeTags.CLOSE_NAV, onClose)
            }
            ShadcnText("Engine showcases", variant = ShadcnTextVariant.Muted)
        },
        footer = { ShadcnText("Select a scene to explore", variant = ShadcnTextVariant.Muted) },
    ) {
        ShadcnSidebarMenu {
            showcases.forEach { showcase ->
                key(showcase.id) {
                    ShadcnSidebarMenuItem(
                        label = showcase.title,
                        active = showcase.id == selection.current,
                        modifier = Modifier.height(44.dp).testTag(ShowcaseSwitcherTags.entry(showcase.id)),
                        onClick = {
                            selection.request(showcase.id)
                            onSelect()
                        },
                    )
                }
            }
        }
    }
}
