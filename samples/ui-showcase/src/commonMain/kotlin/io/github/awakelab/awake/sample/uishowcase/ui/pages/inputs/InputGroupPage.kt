/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.text.rememberTextFieldState
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnInput
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.components.shadcnSurface

internal val InputGroupPage = ShowcasePage(
    id = "input-group",
    title = "Input Group",
    category = ShowcaseCategory.Inputs,
    description = "An input paired with inline prefix or suffix affixes inside one bordered control.",
    usageCode = """Row { ShadcnText(\"https://\"); ShadcnInput(state, Modifier.weight(1f)) }""",
    referenceExample = "registry/new-york-v4/examples/input-group-demo.tsx",
    previewHeight = 320,
    notes = listOf("Prefix, editable field, and suffix are composed in one compact group."),
    hero = {
        val state = rememberTextFieldState("example.com")
        shadcnSurface(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedByHorizontal(4.dp),
            ) {
                ShadcnText("https://", variant = ShadcnTextVariant.Small)
                ShadcnInput(state, Modifier.weight(1f))
                ShadcnText("/profile", variant = ShadcnTextVariant.Small)
            }
        }
    },
)
