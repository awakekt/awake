/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnResizableOrientation
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted
import io.github.awakelab.awake.ui.shadcn.components.shadcnResizablePanelGroup

internal val ResizablePage = ShowcasePage(
    id = "resizable",
    title = "Resizable",
    category = ShowcaseCategory.Layout,
    description = "Accessible resizable panel groups and layouts with keyboard support.",
    usageCode = """shadcnResizablePanelGroup { panel(size = 0.5f) { ... }; panel(size = 0.5f) { ... } }""",
    referenceExample = "registry/new-york-v4/examples/resizable-demo.tsx",
    previewHeight = 420,
    notes = listOf("Nested groups compose freely -- a vertical group inside a horizontal panel."),
    hero = {
        shadcnMuted("Drag the handles to redistribute space between the panels.")
        // Mirrors the reference resizable-demo: One | (Two over Three), the nested vertical
        // group living inside the horizontal group's second panel.
        shadcnResizablePanelGroup(modifier = Modifier.fillMaxWidth().height(240.dp)) {
            panel(size = 0.5f) {
                ShadcnText("One")
            }
            panel(size = 0.5f) {
                shadcnResizablePanelGroup(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    orientation = ShadcnResizableOrientation.Vertical,
                ) {
                    panel(size = 0.25f) {
                        ShadcnText("Two")
                    }
                    panel(size = 0.75f) {
                        ShadcnText("Three")
                    }
                }
            }
        }
    },
)
