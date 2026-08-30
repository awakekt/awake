/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.overlays

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuEntry
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.shadcnDropdownMenu
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

private val MenuItems: List<ShadcnMenuEntry> = listOf(
    ShadcnMenuItem(label = "Pinned action", enabled = false),
    ShadcnMenuSeparator,
    ShadcnMenuItem(label = "Duplicate panel"),
    ShadcnMenuItem(label = "Delete scene", destructive = true),
)

/** The index last clicked in the menu -- the interaction proof under it. */
private class SelectedState {
    var index: Int? = null
    var expanded: Boolean = false
}

internal val DropdownMenuPage = ShowcasePage(
    id = "dropdown-menu",
    title = "Dropdown Menu",
    category = ShowcaseCategory.Overlays,
    description = "Displays a menu to the user -- such as a set of actions or functions -- triggered by a button.",
    usageCode = """shadcnDropdownMenu(entries = items)""",
    referenceExample = "registry/new-york-v4/examples/dropdown-menu-demo.tsx",
    previewHeight = 420,
    notes = listOf(
        "The Actions button controls the anchored menu; selecting an item records its entry index.",
    ),
    hero = {
        val selected = remember { SelectedState() }
        shadcnMuted("Rendered inline so row spacing, grouping, and disabled/destructive rows stay reviewable.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedByHorizontal(12.dp)) {
            shadcnDropdownMenu(
                entries = MenuItems,
                expanded = selected.expanded,
                onExpandedChange = { selected.expanded = it },
                onItemSelected = { selected.index = it },
                id = "showcase-dropdown",
            ) { onClick ->
                ShadcnButton("Actions", modifier = Modifier.height(36.dp), onClick = onClick)
            }
            ShadcnButton(
                "Secondary",
                variant = ShadcnButtonVariant.Outline,
                modifier = Modifier.height(36.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        shadcnMuted("Interaction proof: last clicked index = ${selected.index}")
    },
)
