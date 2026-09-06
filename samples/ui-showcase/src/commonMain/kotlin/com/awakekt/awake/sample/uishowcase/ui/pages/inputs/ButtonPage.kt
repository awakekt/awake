/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.inputs

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.sample.uishowcase.ui.showcaseMatrix
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

/** How many times the hero button was clicked -- the interaction proof under it. */
private class ClickCount {
    var value: Int = 0
}

internal val ButtonPage = ShowcasePage(
    id = "button",
    title = "Button",
    category = ShowcaseCategory.Inputs,
    description = "Displays a button or a component that looks like a button.",
    usageCode = """ShadcnButton("Button", variant = ShadcnButtonVariant.Default, onClick = { })""",
    referenceExample = "registry/new-york-v4/examples/button-demo.tsx",
    notes = listOf("Supports text labels, icons, and custom slot API blocks."),
    hero = {
        val clicks = remember { ClickCount() }
        ShadcnButton(
            "Button",
            variant = ShadcnButtonVariant.Default,
            onClick = { clicks.value += 1 },
        )
        Spacer(Modifier.height(8.dp))
        ShadcnText("Interaction proof: ${clicks.value} clicks", variant = ShadcnTextVariant.Muted)
    },
    variants = {
        showcaseMatrix(ShadcnButtonVariant.entries) { variant ->
            ShadcnButton(variant.name, variant = variant)
        }
    },
    states = {
        showcaseMatrix(ShadcnButtonSizeVariant.entries) { size ->
            ShadcnButton(size.name, size = size)
        }
        Spacer(Modifier.height(12.dp))
        showcaseMatrix(listOf(true, false)) { enabled ->
            ShadcnButton(if (enabled) "Enabled" else "Disabled", enabled = enabled)
        }
    },
)
