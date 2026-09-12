/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.layout

import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnNavigationMenu
import com.awakekt.awake.ui.shadcn.components.ShadcnNavigationMenuTrigger
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

internal val NavigationMenuPage = ShowcasePage(
    id = "navigation-menu",
    title = "Navigation Menu",
    category = ShowcaseCategory.Layout,
    description = "A collection of links for navigating websites with animated popover megamenus.",
    usageCode = """
ShadcnNavigationMenu {
    ShadcnNavigationMenuTrigger(
        title = "Getting Started",
        isOpen = isOpen,
        onOpenChange = { isOpen = it },
    ) {
        ShadcnText("Introduction & Installation Guides")
    }
}
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/navigation-menu-demo.tsx",
    previewHeight = 350,
    hero = {
        val gettingStartedOpen = remember { NavMenuState(false) }
        val componentsOpen = remember { NavMenuState(false) }

        ShadcnNavigationMenu {
            ShadcnNavigationMenuTrigger(
                title = "Getting Started",
                isOpen = gettingStartedOpen.value,
                onOpenChange = { gettingStartedOpen.value = it },
            ) {
                ShadcnText("Introduction")
                shadcnMuted("Reusable components built for the Awake Engine.")
            }

            ShadcnNavigationMenuTrigger(
                title = "Components",
                isOpen = componentsOpen.value,
                onOpenChange = { componentsOpen.value = it },
            ) {
                ShadcnText("Component Catalog")
                shadcnMuted("Over 40+ accessible UI components styled with OKLCH tokens.")
            }

            ShadcnButton("Documentation", variant = ShadcnButtonVariant.Ghost)
        }
    },
)

private class NavMenuState(var value: Boolean)
