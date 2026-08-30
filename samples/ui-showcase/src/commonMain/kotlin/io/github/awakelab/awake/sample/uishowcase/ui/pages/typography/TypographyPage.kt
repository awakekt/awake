/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.typography

import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant

internal val TypographyPage = ShowcasePage(
    id = "typography",
    title = "Typography",
    category = ShowcaseCategory.Typography,
    description = "Styles for headings, paragraphs, lists, quotes, and inline code.",
    usageCode = """ShadcnText("The Joke Tax Chronicles", variant = ShadcnTextVariant.H1)""",
    referenceExample = "registry/new-york-v4/examples/typography-demo.tsx",
    previewWidth = 820,
    previewHeight = 760,
    notes = listOf("Every specimen here is a real recipe, not a restyled text() call."),
    hero = {
        ShadcnText("The Joke Tax Chronicles", variant = ShadcnTextVariant.H1)
        ShadcnText("A modern guide to the taxation of humour in the kingdom.", variant = ShadcnTextVariant.Lead)
        Spacer(Modifier.height(12.dp))
        ShadcnText("The King's Plan", variant = ShadcnTextVariant.H2)
        ShadcnText(
            "Body text is the default reading size for paragraphs and descriptions.",
            variant = ShadcnTextVariant.P,
        )
        ShadcnText("The Joke Tax", variant = ShadcnTextVariant.H3)
        ShadcnText(
            "Headings step down in size and weight without changing the reading rhythm.",
            variant = ShadcnTextVariant.P,
        )
        ShadcnText("Jokester's Revolt", variant = ShadcnTextVariant.H4)
        ShadcnText(
            "After the king's edict, jokes were smuggled in wagons of hay.",
            variant = ShadcnTextVariant.P,
        )
        Spacer(Modifier.height(12.dp))
        ShadcnText("Large text", variant = ShadcnTextVariant.Large)
        ShadcnText("Small text", variant = ShadcnTextVariant.Small)
        ShadcnText("Muted supporting copy.", variant = ShadcnTextVariant.Muted)
        ShadcnText("ShadcnText(\"inline\", variant = ShadcnTextVariant.Xs)", variant = ShadcnTextVariant.Xs)
    },
)
