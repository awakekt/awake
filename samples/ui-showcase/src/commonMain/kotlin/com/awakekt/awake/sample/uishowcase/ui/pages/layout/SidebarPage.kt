/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.layout

import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnSidebar
import com.awakekt.awake.ui.shadcn.components.ShadcnSidebarMenu
import com.awakekt.awake.ui.shadcn.components.ShadcnSidebarMenuItem
import com.awakekt.awake.ui.shadcn.components.ShadcnText

private val SidebarSampleItems = listOf("Overview", "Analytics", "Projects", "Settings")

private class SidebarActiveItem {
    var index: Int = 0
}

internal val SidebarPage = ShowcasePage(
    id = "sidebar",
    title = "Sidebar",
    category = ShowcaseCategory.Layout,
    description = "A composable, themeable and customizable sidebar with groups, menus, and pinned chrome.",
    usageCode = """shadcnSidebar(header = { ... }, footer = { ... }) { shadcnSidebarMenu { ... } }""",
    referenceExample = "registry/new-york-v4/blocks/sidebar-07",
    previewHeight = 520,
    notes = listOf("Header and footer stay pinned while the menu region scrolls."),
    hero = {
        val active = remember { SidebarActiveItem() }
        ShadcnSidebar(
            modifier = Modifier.width(264.dp).height(360.dp),
            header = { ShadcnText("Acme Inc") },
            footer = { ShadcnText("shadcn") },
        ) {
            ShadcnSidebarMenu {
                SidebarSampleItems.forEachIndexed { index, label ->
                    ShadcnSidebarMenuItem(
                        label = label,
                        active = index == active.index,
                        onClick = { active.index = index },
                    )
                }
            }
        }
    },
)
