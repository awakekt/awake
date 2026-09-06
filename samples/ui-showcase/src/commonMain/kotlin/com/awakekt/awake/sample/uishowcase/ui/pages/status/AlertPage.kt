/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.status

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnAlert
import com.awakekt.awake.ui.shadcn.components.ShadcnAlertVariant

internal val AlertPage = ShowcasePage(
    id = "alert",
    title = "Alert",
    category = ShowcaseCategory.Status,
    description = "Displays a callout for user attention.",
    usageCode = """shadcnAlert(title = "Heads up!", description = "...")""",
    referenceExample = "registry/new-york-v4/examples/alert-demo.tsx",
    previewHeight = 380,
    notes = listOf("Default and Destructive alert callout boxes."),
    hero = {
        ShadcnAlert(
            title = "Heads up!",
            description = "You can add components to your app using the CLI.",
        )
    },
    variants = {
        ShadcnAlertVariant.entries.forEach { variant ->
            ShadcnAlert(
                title = variant.name,
                description = "Alert variant ${variant.name}.",
                variant = variant,
            )
            Spacer(Modifier.height(12.dp))
        }
    },
)
