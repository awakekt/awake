/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.status

import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAlertVariant
import io.github.awakelab.awake.ui.shadcn.components.shadcnAlert

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
        shadcnAlert(
            title = "Heads up!",
            description = "You can add components to your app using the CLI.",
        )
    },
    variants = {
        ShadcnAlertVariant.entries.forEach { variant ->
            shadcnAlert(
                title = variant.name,
                description = "Alert variant ${variant.name}.",
                variant = variant,
            )
            Spacer(Modifier.height(12.dp))
        }
    },
)
