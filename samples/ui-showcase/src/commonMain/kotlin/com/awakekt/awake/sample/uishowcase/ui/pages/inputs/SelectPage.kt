/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.inputs

import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnSelect
import com.awakekt.awake.ui.shadcn.components.ShadcnSelectItem

private class SelectState {
    var expanded = false
    var selected: Int? = 1
}

internal val SelectPage = ShowcasePage(
    id = "select",
    title = "Select",
    category = ShowcaseCategory.Inputs,
    description = "Displays a list of options for the user to pick from, triggered by a button.",
    usageCode = """ShadcnSelect(items = items, selectedIndex = selected, expanded = expanded, ...)""",
    referenceExample = "registry/new-york-v4/examples/select-demo.tsx",
    previewHeight = 360,
    notes = listOf("Open the trigger to choose an item; the popup is anchored to the field."),
    hero = {
        val state = remember { SelectState() }
        val items = listOf("Apple", "Banana", "Blueberry", "Grapes", "Pineapple").map(::ShadcnSelectItem)
        ShadcnSelect(
            items = items,
            selectedIndex = state.selected,
            expanded = state.expanded,
            onExpandedChange = { state.expanded = it },
            onItemSelected = { state.selected = it },
            id = "showcase-select",
            placeholder = "Choose a fruit",
        )
    },
)
