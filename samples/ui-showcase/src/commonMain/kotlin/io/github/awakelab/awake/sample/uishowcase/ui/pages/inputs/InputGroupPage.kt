/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs

import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.text.rememberTextFieldState
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnInputGroup
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant

internal val InputGroupPage = ShowcasePage(
    id = "input-group",
    title = "Input Group",
    category = ShowcaseCategory.Inputs,
    description = "An input paired with inline prefix or suffix affixes inside one bordered control.",
    usageCode = """ShadcnInputGroup(state, prefix = { ShadcnText("https://") }, suffix = { ShadcnText("/profile") })""",
    referenceExample = "registry/new-york-v4/examples/input-group-demo.tsx",
    previewHeight = 320,
    notes = listOf("Prefix, editable field, and suffix are composed in one compact group."),
    hero = {
        val state = rememberTextFieldState("example.com")
        ShadcnInputGroup(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            prefix = { ShadcnText("https://", variant = ShadcnTextVariant.Small) },
            suffix = { ShadcnText("/profile", variant = ShadcnTextVariant.Small) },
        )
    },
)
