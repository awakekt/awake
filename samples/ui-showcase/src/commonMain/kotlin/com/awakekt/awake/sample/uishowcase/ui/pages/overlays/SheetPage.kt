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
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnSheet
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

private class SheetState {
    var visible: Boolean = false
}

internal val SheetPage = ShowcasePage(
    id = "sheet",
    title = "Sheet",
    category = ShowcaseCategory.Overlays,
    description = "Extends the Dialog component to display content that complements the main content of the screen.",
    usageCode = """ShadcnSheet(visible = isOpen, onDismissRequest = { isOpen = false }) { ... }""",
    previewHeight = 420,
    hero = {
        val state = remember { SheetState() }
        ShadcnButton("Open settings", variant = ShadcnButtonVariant.Outline, onClick = { state.visible = true })
        ShadcnSheet(
            visible = state.visible,
            title = "Settings",
            description = "Update preferences without leaving the current page.",
            id = "showcase-sheet",
            onDismissRequest = { state.visible = false },
        ) {
            ShadcnText("Notifications")
            shadcnMuted("Email and desktop alerts are enabled.")
            ShadcnButton("Done", onClick = { state.visible = false })
        }
    },
)
