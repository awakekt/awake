/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.inputs

import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.sample.uishowcase.ui.showcaseMatrix
import com.awakekt.awake.ui.shadcn.components.ShadcnCheckbox

private class CheckedState(initial: Boolean) {
    var checked: Boolean = initial
}

internal val CheckboxPage = ShowcasePage(
    id = "checkbox",
    title = "Checkbox",
    category = ShowcaseCategory.Inputs,
    description = "A control that allows the user to toggle between checked and not checked.",
    usageCode = """ShadcnCheckbox(checked = true, onCheckedChange = { })""",
    referenceExample = "registry/new-york-v4/examples/checkbox-demo.tsx",
    previewHeight = 280,
    notes = listOf("Supports checked, unchecked, and disabled state tokens."),
    hero = {
        val state = remember { CheckedState(true) }
        ShadcnCheckbox(state.checked, onCheckedChange = { state.checked = it })
    },
    states = {
        // Checked, then unchecked -- ShadcnCheckbox has no label slot, so the state is the
        // only variable across the matrix.
        showcaseMatrix(listOf(true, false)) { checked ->
            ShadcnCheckbox(checked)
        }
    },
)
