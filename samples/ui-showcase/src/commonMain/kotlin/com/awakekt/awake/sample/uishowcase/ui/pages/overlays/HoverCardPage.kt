/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.overlays

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnAvatar
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnHoverCard
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

internal val HoverCardPage = ShowcasePage(
    id = "hover-card",
    title = "Hover Card",
    category = ShowcaseCategory.Overlays,
    description = "For sighted users to preview content available behind a link or trigger on hover.",
    usageCode = """
ShadcnHoverCard(
    trigger = { ShadcnButton("@nextjs", variant = ShadcnButtonVariant.Link) },
) {
    Column {
        shadcnAvatar(initials = "NX")
        ShadcnText("The React Framework – created and maintained by @vercel.")
    }
}
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/hover-card-demo.tsx",
    previewHeight = 350,
    hero = {
        ShadcnHoverCard(
            trigger = {
                ShadcnButton("@nextjs", variant = ShadcnButtonVariant.Link)
            },
        ) {
            Column {
                ShadcnAvatar(initials = "NX")
                ShadcnText("Next.js")
                shadcnMuted("The React Framework – created and maintained by @vercel.")
            }
        }
    },
)
