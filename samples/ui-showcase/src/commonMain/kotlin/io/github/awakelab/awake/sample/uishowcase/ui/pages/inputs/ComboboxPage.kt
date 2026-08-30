/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCombobox
import io.github.awakelab.awake.ui.shadcn.components.ShadcnComboboxItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant

private class ComboboxState {
    var expanded = false
    var selected: Int? = null
}

internal val ComboboxPage = ShowcasePage(
    id = "combobox",
    title = "Combobox",
    category = ShowcaseCategory.Inputs,
    description = "Autocomplete input and command palette with a searchable list of suggestions.",
    usageCode = """val frameworks = listOf("Next.js", "SvelteKit", "Nuxt.js", "Remix", "Astro").map(::ShadcnComboboxItem)
ShadcnCombobox(
    items = frameworks,
    selectedIndex = selected,
    expanded = expanded,
    onExpandedChange = { expanded = it },
    onItemSelected = { selected = it },
    placeholder = "Select framework...",
    searchPlaceholder = "Search framework...",
)""",
    referenceExample = "registry/new-york-v4/examples/combobox-demo.tsx",
    previewHeight = 360,
    notes = listOf("Command-style framework search with auto-filtered anchored option popup."),
    hero = {
        val state = remember { ComboboxState() }
        val options = listOf(
            "Next.js",
            "SvelteKit",
            "Nuxt.js",
            "Remix",
            "Astro",
        ).map(::ShadcnComboboxItem)

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShadcnText("Framework", variant = ShadcnTextVariant.Small)
            ShadcnCombobox(
                items = options,
                selectedIndex = state.selected,
                expanded = state.expanded,
                onExpandedChange = { state.expanded = it },
                onItemSelected = { state.selected = it },
                id = "showcase-combobox",
                placeholder = "Select framework...",
                searchPlaceholder = "Search framework...",
            )
        }
    },
)
