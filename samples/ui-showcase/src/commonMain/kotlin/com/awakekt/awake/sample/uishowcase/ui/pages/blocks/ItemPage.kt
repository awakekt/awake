/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.blocks

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.ShadcnAvatar
import com.awakekt.awake.ui.shadcn.components.ShadcnBadge
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnItem

internal val ItemPage = ShowcasePage(
    id = "item",
    title = "Item",
    category = ShowcaseCategory.Blocks,
    description = "Media object row with leading slot, title/description stack, and trailing actions.",
    usageCode = """
ShadcnItem(
    title = "Two-Factor Authentication",
    description = "Add an extra layer of security to your account.",
    leading = { shadcnAvatar("2F") },
    trailing = { ShadcnButton("Enable", size = ShadcnButtonSizeVariant.Sm) }
)
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/item-demo.tsx",
    previewHeight = 350,
    hero = {
        Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3)) {
            ShadcnItem(
                title = "Two-Factor Authentication",
                description = "Add an extra layer of security to your account with 2FA.",
                leading = { ShadcnAvatar("2F") },
                trailing = { ShadcnButton("Enable", size = ShadcnButtonSizeVariant.Sm) },
            )

            ShadcnItem(
                title = "API Access Tokens",
                description = "Manage active secret keys for external integrations.",
                leading = { ShadcnAvatar("AK") },
                trailing = { ShadcnBadge("Active") },
            )

            ShadcnItem(
                title = "Notification Preferences",
                description = "Customize email and push notification alerts.",
                leading = { ShadcnAvatar("NP") },
                trailing = {
                    ShadcnButton(
                        "Configure",
                        variant = ShadcnButtonVariant.Outline,
                        size = ShadcnButtonSizeVariant.Sm,
                    )
                },
            )
        }
    },
)
