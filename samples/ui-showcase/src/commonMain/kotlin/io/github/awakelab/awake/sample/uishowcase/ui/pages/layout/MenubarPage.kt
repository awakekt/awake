/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuEntry
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenubar
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenubarMenu

internal val MenubarPage = ShowcasePage(
    id = "menubar",
    title = "Menubar",
    category = ShowcaseCategory.Layout,
    description = "A visually persistent menu common in desktop applications that provides access to a consistent set of commands.",
    usageCode = """
ShadcnMenubar {
    ShadcnMenubarMenu(
        title = "File",
        isOpen = openFile,
        onOpenChange = { openFile = it },
        entries = fileEntries,
        onItemSelected = { },
    )
}
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/menubar-demo.tsx",
    previewHeight = 350,
    hero = {
        val fileMenuOpen = remember { BooleanState(false) }
        val editMenuOpen = remember { BooleanState(false) }

        val fileEntries = remember {
            listOf<ShadcnMenuEntry>(
                ShadcnMenuItem("New Tab  Ctrl+T"),
                ShadcnMenuItem("New Window  Ctrl+N"),
                ShadcnMenuSeparator,
                ShadcnMenuItem("Share"),
                ShadcnMenuSeparator,
                ShadcnMenuItem("Print  Ctrl+P"),
            )
        }

        val editEntries = remember {
            listOf<ShadcnMenuEntry>(
                ShadcnMenuItem("Undo  Ctrl+Z"),
                ShadcnMenuItem("Redo  Ctrl+Shift+Z"),
                ShadcnMenuSeparator,
                ShadcnMenuItem("Cut  Ctrl+X"),
                ShadcnMenuItem("Copy  Ctrl+C"),
                ShadcnMenuItem("Paste  Ctrl+V"),
            )
        }

        ShadcnMenubar {
            ShadcnMenubarMenu(
                title = "File",
                isOpen = fileMenuOpen.value,
                onOpenChange = { fileMenuOpen.value = it },
                entries = fileEntries,
                onItemSelected = { },
            )
            ShadcnMenubarMenu(
                title = "Edit",
                isOpen = editMenuOpen.value,
                onOpenChange = { editMenuOpen.value = it },
                entries = editEntries,
                onItemSelected = { },
            )
        }
    },
)

private class BooleanState(var value: Boolean)
