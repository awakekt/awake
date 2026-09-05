/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.overlays

import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCommand
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCommandItem

internal val CommandPage = ShowcasePage(
    id = "command",
    title = "Command",
    category = ShowcaseCategory.Overlays,
    description = "Fast, composable command menu palette for search and action shortcuts.",
    usageCode = """
ShadcnCommand(
    items = listOf(
        ShadcnCommandItem("Calendar", shortcut = "⌘C", category = "Suggestions"),
        ShadcnCommandItem("Search Emoji", shortcut = "⌘E", category = "Suggestions"),
        ShadcnCommandItem("Profile", shortcut = "⌘P", category = "Settings"),
    ),
    onItemSelected = { ... }
)
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/command-demo.tsx",
    previewHeight = 400,
    hero = {
        val commandItems = remember {
            listOf(
                ShadcnCommandItem("Calendar", shortcut = "Ctrl+C", category = "Suggestions"),
                ShadcnCommandItem("Search Emoji", shortcut = "Ctrl+E", category = "Suggestions"),
                ShadcnCommandItem("Calculator", shortcut = "Ctrl+K", category = "Suggestions"),
                ShadcnCommandItem("Profile Settings", shortcut = "Ctrl+P", category = "Settings"),
                ShadcnCommandItem("Billing & Invoices", shortcut = "Ctrl+B", category = "Settings"),
            )
        }

        ShadcnCommand(
            items = commandItems,
            onItemSelected = { },
        )
    },
)
