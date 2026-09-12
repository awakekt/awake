/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.overlays

import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.shadcnDrawer
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

private class DrawerState {
    var visible: Boolean = false
}

internal val DrawerPage = ShowcasePage(
    id = "drawer",
    title = "Drawer",
    category = ShowcaseCategory.Overlays,
    description = "A slide-over panel anchored to the bottom edge of the viewport.",
    usageCode = """shadcnDrawer(visible = isOpen, onDismissRequest = { isOpen = false }) { ... }""",
    previewHeight = 420,
    hero = {
        val state = remember { DrawerState() }
        ShadcnButton("Open drawer", onClick = { state.visible = true })
        shadcnDrawer(
            visible = state.visible,
            title = "Account details",
            description = "Review the profile currently signed in.",
            id = "showcase-drawer",
            onDismissRequest = { state.visible = false },
        ) {
            ShadcnText("ron@example.com")
            shadcnMuted("Member since 2024")
            ShadcnButton("Close", onClick = { state.visible = false })
        }
    },
)
