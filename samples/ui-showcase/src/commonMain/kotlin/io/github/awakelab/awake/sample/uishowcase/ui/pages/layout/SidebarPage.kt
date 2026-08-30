/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.shadcnSidebar
import io.github.awakelab.awake.ui.shadcn.components.shadcnSidebarMenu
import io.github.awakelab.awake.ui.shadcn.components.shadcnSidebarMenuItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText

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
        shadcnSidebar(
            modifier = Modifier.width(264.dp).height(360.dp),
            header = { ShadcnText("Acme Inc") },
            footer = { ShadcnText("shadcn") },
        ) {
            shadcnSidebarMenu {
                SidebarSampleItems.forEachIndexed { index, label ->
                    shadcnSidebarMenuItem(
                        label = label,
                        active = index == active.index,
                        onClick = { active.index = index },
                    )
                }
            }
        }
    },
)
