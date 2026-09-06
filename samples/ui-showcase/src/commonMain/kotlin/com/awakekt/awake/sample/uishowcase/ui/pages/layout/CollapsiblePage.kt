/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.uishowcase.ui.pages.layout

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnCollapsible
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparator
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

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
        ShadcnCollapsible(
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
        ShadcnCollapsible(
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
