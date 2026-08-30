/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs

import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.sample.uishowcase.ui.showcaseMatrix
import io.github.awakelab.awake.ui.shadcn.components.shadcnToggle

private class ToggleCheckedState(initial: Boolean) {
    var checked: Boolean = initial
}

internal val TogglePage = ShowcasePage(
    id = "toggle",
    title = "Toggle",
    category = ShowcaseCategory.Inputs,
    description = "A two-state button that can be either on or off.",
    usageCode = """shadcnToggle("Bold", checked = bold)""",
    referenceExample = "registry/new-york-v4/examples/toggle-demo.tsx",
    previewHeight = 280,
    hero = {
        val state = remember { ToggleCheckedState(false) }
        state.checked = shadcnToggle("Bold", checked = state.checked)
    },
    states = {
        showcaseMatrix(listOf("On" to true, "Off" to false)) { (label, checked) ->
            shadcnToggle(label, checked = checked)
        }
    },
)
