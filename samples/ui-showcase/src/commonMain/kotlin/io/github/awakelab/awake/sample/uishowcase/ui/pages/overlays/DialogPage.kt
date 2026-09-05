/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.overlays

import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnDialog

/** Whether the dialog modal is showing. */
private class OpenState {
    var open: Boolean = false
}

internal val DialogPage = ShowcasePage(
    id = "dialog",
    title = "Dialog",
    category = ShowcaseCategory.Overlays,
    description = "A window overlaid on either the primary window or another dialog window.",
    usageCode = """
        ShadcnDialog(visible = isOpen, onDismissRequest = { isOpen = false }) {
            header {
                title("Edit profile")
                description("Make changes to your profile here. Click save when you're done.")
            }
            footer {
                ShadcnButton("Save changes", onClick = { isOpen = false })
            }
        }
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/dialog-demo.tsx",
    previewHeight = 420,
    notes = listOf(
        "ShadcnDialog renders in the viewport overlay layer with an animated scrim backdrop.",
    ),
    hero = {
        val state = remember { OpenState() }
        ShadcnButton(
            "Open dialog",
            variant = ShadcnButtonVariant.Outline,
            onClick = { state.open = true },
        )
        ShadcnDialog(
            visible = state.open,
            onDismissRequest = { state.open = false },
        ) {
            header {
                title("Edit profile")
                description(
                    "Make changes to your profile here. This dialog is interactive and dismisses on Escape or clicking outside.",
                )
            }
            footer {
                ShadcnButton("Close", onClick = { state.open = false })
            }
        }
    },
)
