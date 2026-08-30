/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.overlays

import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.shadcnDialog

/** Whether the dialog panel is showing -- shadcnDialog is content-only, so the page owns this. */
private class OpenState {
    var open: Boolean = false
}

internal val DialogPage = ShowcasePage(
    id = "dialog",
    title = "Dialog",
    category = ShowcaseCategory.Overlays,
    description = "A window overlaid on either the primary window or another dialog window.",
    usageCode = """shadcnDialog(title = "Edit profile", description = "...", actions = { ShadcnButton("Close", onClick = { }) })""",
    referenceExample = "registry/new-york-v4/examples/dialog-demo.tsx",
    previewHeight = 420,
    notes = listOf(
        "shadcnDialog renders the panel only; anchoring/overlay lifecycle is not wired yet, " +
            "so the trigger toggles the panel inline instead of over a scrim.",
    ),
    hero = {
        val state = remember { OpenState() }
        ShadcnButton(
            "Open dialog",
            variant = ShadcnButtonVariant.Outline,
            onClick = { state.open = !state.open },
        )
        if (state.open) {
            Spacer(Modifier.height(8.dp))
            shadcnDialog(
                title = "Edit profile",
                description = "Make changes to your profile here. This dialog is interactive.",
                actions = {
                    ShadcnButton("Close", onClick = { state.open = false })
                },
            )
        }
    },
)
