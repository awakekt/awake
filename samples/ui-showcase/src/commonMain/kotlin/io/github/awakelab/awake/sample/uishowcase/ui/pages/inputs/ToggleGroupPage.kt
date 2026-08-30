/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs

import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.shadcnToggleGroup

private class SelectedValue(initial: String?) {
    var value: String? = initial
}

private class SelectedValues(initial: Set<String>) {
    var value: Set<String> = initial
}

internal val ToggleGroupPage = ShowcasePage(
    id = "toggle-group",
    title = "Toggle Group",
    category = ShowcaseCategory.Inputs,
    description = "A set of two-state buttons that can be toggled on or off.",
    usageCode = """
shadcnToggleGroup(selected = "left", onSelectedChange = { }) {
    item("left", "Left")
    item("center", "Center")
}
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/toggle-group-demo.tsx",
    previewHeight = 280,
    notes = listOf("Single-select and multi-select forms share one recipe."),
    hero = {
        val selected = remember { SelectedValue("left") }
        shadcnToggleGroup(
            selected = selected.value,
            onSelectedChange = { selected.value = it },
        ) {
            item("left", "Left")
            item("center", "Center")
            item("right", "Right")
        }
    },
    states = {
        val picked = remember { SelectedValues(setOf("bold", "underline")) }
        shadcnToggleGroup(
            selected = picked.value,
            onSelectedChange = { picked.value = it },
        ) {
            item("bold", "Bold")
            item("italic", "Italic")
            item("underline", "Underline")
        }
    },
)
