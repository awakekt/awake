/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.overlays

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTooltipped
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

internal val TooltipPage = ShowcasePage(
    id = "tooltip",
    title = "Tooltip",
    category = ShowcaseCategory.Overlays,
    description = "A popup that displays information related to an element when it receives focus or hover.",
    usageCode = """shadcnTooltipped("Hint") { ShadcnButton("Hover target") }""",
    referenceExample = "registry/new-york-v4/examples/tooltip-demo.tsx",
    previewHeight = 200,
    notes = listOf(
        "shadcnTooltipped wraps its trigger content directly and shows the bubble on hover -- " +
            "no manual open/close state is needed at the call site.",
    ),
    hero = {
        shadcnMuted("Hover the button to reveal the tooltip.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedByHorizontal(12.dp)) {
            ShadcnTooltipped("Scene stats stay live here when the cursor rests on the trigger.") {
                ShadcnButton("Hover target", modifier = Modifier.height(36.dp))
            }
            ShadcnButton(
                "Reference",
                variant = ShadcnButtonVariant.Secondary,
                modifier = Modifier.height(36.dp),
            )
        }
    },
)
