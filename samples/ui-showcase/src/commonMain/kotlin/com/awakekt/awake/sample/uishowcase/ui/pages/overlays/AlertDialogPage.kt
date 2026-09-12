/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.overlays

import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnAlertDialog
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

private class AlertDialogState {
    var visible: Boolean = false
    var result: String = "No decision yet"
}

internal val AlertDialogPage = ShowcasePage(
    id = "alert-dialog",
    title = "Alert Dialog",
    category = ShowcaseCategory.Overlays,
    description = "A modal dialog that interrupts the user with important content and expects a response.",
    usageCode = """ShadcnAlertDialog(visible = isOpen, title = \"Delete project?\", onDismissRequest = { isOpen = false })""",
    previewHeight = 420,
    hero = {
        val state = remember { AlertDialogState() }
        ShadcnButton("Delete project", variant = ShadcnButtonVariant.Destructive, onClick = { state.visible = true })
        ShadcnAlertDialog(
            visible = state.visible,
            title = "Delete project?",
            description = "This permanently removes the project and its data.",
            confirmLabel = "Delete",
            cancelLabel = "Keep project",
            destructive = true,
            id = "showcase-alert-dialog",
            onDismissRequest = {
                state.result = "Kept project"
                state.visible = false
            },
            onConfirm = {
                state.result = "Deleted project"
                state.visible = false
            },
        )
        shadcnMuted("Interaction proof: ${state.result}")
    },
)
