/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCollapsible
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

/** No `shadcnCollapsibleCard` recipe in compose yet -- compose it from card + collapsible. */
private class CollapsibleCardExpanded {
    var open: Boolean = true
}

internal val CollapsibleCardPage = ShowcasePage(
    id = "collapsible-card",
    title = "Collapsible Card",
    category = ShowcaseCategory.Layout,
    description = "A card whose body collapses behind its own header row.",
    usageCode = """ShadcnCard { shadcnCollapsible("Deployment settings", expanded = open, onExpandedChange = { ... }) { ... } }""",
    previewHeight = 400,
    notes = listOf("Composes the behavior primitive with the card visual instead of baking one into the other."),
    hero = {
        val state = remember { CollapsibleCardExpanded() }
        ShadcnCard {
            ShadcnCollapsible(
                title = "Deployment settings",
                expanded = state.open,
                onExpandedChange = { state.open = it },
            ) {
                shadcnMuted("Body content is mounted only while the card is expanded.")
            }
        }
    },
)
