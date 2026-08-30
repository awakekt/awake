/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs

import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnRadioGroup

private class SelectedIndex {
    var value: Int = 0
}

internal val RadioGroupPage = ShowcasePage(
    id = "radio-group",
    title = "Radio Group",
    category = ShowcaseCategory.Inputs,
    description = "A set of checkable buttons where no more than one can be checked at a time.",
    usageCode = """ShadcnRadioGroup(options = listOf("A", "B"), selected = 0)""",
    referenceExample = "registry/new-york-v4/examples/radio-group-demo.tsx",
    previewHeight = 320,
    notes = listOf("Exclusive single-choice selection with keyboard navigation."),
    hero = {
        val selected = remember { SelectedIndex() }
        selected.value = ShadcnRadioGroup(
            options = listOf("System", "Light", "Dark"),
            selected = selected.value,
        )
    },
)
