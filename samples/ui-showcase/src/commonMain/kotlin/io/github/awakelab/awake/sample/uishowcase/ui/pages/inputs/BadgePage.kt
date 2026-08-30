/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs

import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.sample.uishowcase.ui.showcaseMatrix
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadge
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadgeVariant

internal val BadgePage = ShowcasePage(
    id = "badge",
    title = "Badge",
    category = ShowcaseCategory.Inputs,
    description = "Displays a badge or a component that looks like a badge.",
    usageCode = """ShadcnBadge("Badge", variant = ShadcnBadgeVariant.Default)""",
    referenceExample = "registry/new-york-v4/examples/badge-demo.tsx",
    previewHeight = 260,
    notes = listOf("Pill shape with primary, secondary, outline, ghost, and destructive tokens."),
    hero = {
        ShadcnBadge("Badge", variant = ShadcnBadgeVariant.Default)
    },
    variants = {
        showcaseMatrix(ShadcnBadgeVariant.entries) { variant ->
            ShadcnBadge(variant.name, variant = variant)
        }
    },
)
