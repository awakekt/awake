/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.overlays

import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnContextMenu
import com.awakekt.awake.ui.shadcn.components.ShadcnMenuEntry
import com.awakekt.awake.ui.shadcn.components.ShadcnMenuItem
import com.awakekt.awake.ui.shadcn.components.ShadcnMenuSeparator
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.shadcnMuted
import com.awakekt.awake.ui.shadcn.components.shadcnSurface

private val ContextMenuEntries: List<ShadcnMenuEntry> = listOf(
    ShadcnMenuItem(label = "Open item"),
    ShadcnMenuItem(label = "Rename item"),
    ShadcnMenuSeparator,
    ShadcnMenuItem(label = "Delete item", destructive = true),
)

private class ContextMenuState {
    var selected: String = "Nothing selected"
}

internal val ContextMenuPage = ShowcasePage(
    id = "context-menu",
    title = "Context Menu",
    category = ShowcaseCategory.Overlays,
    description = "Displays a menu located at the pointer, triggered by a right click.",
    usageCode = """ShadcnContextMenu(entries = actions, onItemSelected = ::handleAction) { ... }""",
    referenceExample = "registry/new-york-v4/examples/context-menu-demo.tsx",
    previewHeight = 420,
    hero = {
        val state = remember { ContextMenuState() }
        ShadcnContextMenu(
            entries = ContextMenuEntries,
            onItemSelected = { index -> state.selected = "Action ${index + 1}" },
            modifier = Modifier.width(320.dp).height(96.dp),
            id = "showcase-context-menu",
        ) {
            shadcnSurface(contentPadding = 16.dp) {
                ShadcnText("Right-click this item")
                shadcnMuted("Open actions at the pointer")
            }
        }
        shadcnMuted("Interaction proof: ${state.selected}")
    },
)
