/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.inputs

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnFieldLabel
import com.awakekt.awake.ui.shadcn.components.ShadcnSwitch

private class SwitchCheckedState(initial: Boolean) {
    var checked: Boolean = initial
}

internal val SwitchPage = ShowcasePage(
    id = "switch",
    title = "Switch",
    category = ShowcaseCategory.Inputs,
    description = "A control that allows the user to toggle between checked and not checked.",
    usageCode = """shadcnSwitch(checked = enabled, onCheckedChange = { })""",
    referenceExample = "registry/new-york-v4/examples/switch-demo.tsx",
    previewHeight = 280,
    notes = listOf("Smooth thumb sliding animation across track."),
    hero = {
        val state = remember { SwitchCheckedState(true) }
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnSwitch(state.checked, onCheckedChange = { state.checked = it })
            ShadcnFieldLabel("Airplane mode")
        }
    },
)
