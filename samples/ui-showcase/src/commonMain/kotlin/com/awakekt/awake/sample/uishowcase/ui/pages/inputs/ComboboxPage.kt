/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.inputs

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnCombobox
import com.awakekt.awake.ui.shadcn.components.ShadcnComboboxItem
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

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
