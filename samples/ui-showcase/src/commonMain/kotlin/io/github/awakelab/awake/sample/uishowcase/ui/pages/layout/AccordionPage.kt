/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.compose.runtime.key
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCollapsible
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

private data class AccordionItem(val id: String, val title: String, val body: String)

private val SampleAccordionItems = listOf(
    AccordionItem(
        "item-1",
        "Is it accessible?",
        "Yes. It adheres to the WAI-ARIA design pattern for accordion components.",
    ),
    AccordionItem(
        "item-2",
        "Is it headless?",
        "Yes. It's headless by default, giving you full control over the visual presentation.",
    ),
    AccordionItem(
        "item-3",
        "Can it be animated?",
        "Yes. Height transitions animate smoothly between collapsed and expanded states.",
    ),
)

/** Which single item is expanded -- an accordion is a set of collapsibles sharing one selection. */
private class AccordionSelection {
    var selectedId: String? = "item-1"
}

internal val AccordionPage = ShowcasePage(
    id = "accordion",
    title = "Accordion",
    category = ShowcaseCategory.Layout,
    description = "A vertically stacked set of interactive headings that each reveal a section of content.",
    usageCode = """shadcnCollapsible(item.title, expanded = selected == item.id, onExpandedChange = { ... })""",
    referenceExample = "registry/new-york-v4/examples/accordion-demo.tsx",
    previewHeight = 420,
    notes = listOf("Collapsible group supporting WAI-ARIA single selection."),
    hero = {
        val selection = remember { AccordionSelection() }
        SampleAccordionItems.forEach { item ->
            key(item.id) {
                ShadcnCollapsible(
                    title = item.title,
                    expanded = selection.selectedId == item.id,
                    onExpandedChange = {
                        selection.selectedId = if (it) item.id else null
                    },
                ) {
                    shadcnMuted(item.body)
                }
            }
        }
    },
)
