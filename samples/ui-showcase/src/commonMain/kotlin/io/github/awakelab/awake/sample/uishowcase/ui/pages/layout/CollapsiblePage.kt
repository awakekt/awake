/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.shadcnCollapsible
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText

private class CollapsibleExpanded {
    var open: Boolean = true
}

internal val CollapsiblePage = ShowcasePage(
    id = "collapsible",
    title = "Collapsible",
    category = ShowcaseCategory.Layout,
    description = "An interactive component which expands and collapses a panel.",
    usageCode = """shadcnCollapsible("Header", expanded = expanded, onExpandedChange = { expanded = it }) { ... }""",
    referenceExample = "registry/new-york-v4/examples/collapsible-demo.tsx",
    previewHeight = 440,
    notes = listOf("Smooth expand/collapse animation for hidden content."),
    hero = {
        val state = remember { CollapsibleExpanded() }
        shadcnCollapsible(
            title = "@radix-ui/primitives",
            expanded = state.open,
            onExpandedChange = { state.open = it },
        ) {
            CollapsibleRows()
        }
    },
    states = {
        // Rendered open on purpose so revealed-content spacing and separators stay reviewable
        // without live interaction.
        shadcnCollapsible(
            title = "@radix-ui/primitives",
            expanded = true,
            onExpandedChange = {},
        ) {
            CollapsibleRows()
        }
    },
)

context(_: Composer)
private fun CollapsibleRows() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shadcnMuted("Starred repositories in this workspace.")
        ShadcnSeparator(modifier = Modifier.padding(vertical = 4.dp))
        Row(Modifier.fillMaxWidth().height(32.dp)) {
            ShadcnText("@radix-ui/colors", modifier = Modifier.padding(horizontal = 12.dp))
        }
        Row(Modifier.fillMaxWidth().height(32.dp)) {
            ShadcnText("@stitches/react", modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}
