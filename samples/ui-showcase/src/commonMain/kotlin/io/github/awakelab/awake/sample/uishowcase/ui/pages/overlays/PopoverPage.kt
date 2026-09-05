/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.overlays

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnPopover
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

internal val PopoverPage = ShowcasePage(
    id = "popover",
    title = "Popover",
    category = ShowcaseCategory.Overlays,
    description = "Displays rich content in a portal, triggered by a button.",
    usageCode = """shadcnPopover { ShadcnText("Share scene") }""",
    referenceExample = "registry/new-york-v4/examples/popover-demo.tsx",
    previewHeight = 400,
    notes = listOf(
        "shadcnPopover renders the panel content only (no anchor/open-state wiring yet), " +
            "so it is rendered inline below the trigger rather than anchored over it.",
    ),
    hero = {
        shadcnMuted("Rendered inline so panel chrome and freeform content stay reviewable.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedByHorizontal(12.dp)) {
            ShadcnButton("Share", modifier = Modifier.height(36.dp))
            ShadcnButton(
                "Reference",
                variant = ShadcnButtonVariant.Secondary,
                modifier = Modifier.height(36.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        ShadcnPopover {
            Column {
                ShadcnText("Share scene")
                shadcnMuted("Anyone with the link can view this scene until you revoke it.")
            }
        }
    },
)
