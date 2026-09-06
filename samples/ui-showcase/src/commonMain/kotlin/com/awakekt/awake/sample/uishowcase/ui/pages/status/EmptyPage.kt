/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.status

import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnEmpty

internal val EmptyPage = ShowcasePage(
    id = "empty",
    title = "Empty",
    category = ShowcaseCategory.Status,
    description = "Use to display an empty state with a title, description, and optional action.",
    usageCode = """shadcnEmpty(title = "No projects", description = "...") { ... }""",
    referenceExample = "registry/new-york-v4/examples/empty-demo.tsx",
    previewHeight = 380,
    hero = {
        ShadcnEmpty(
            title = "No projects yet",
            description = "Create your first project to see it listed here.",
        ) {
            ShadcnButton(
                "New project",
                variant = ShadcnButtonVariant.Default,
                onClick = { },
            )
        }
    },
)
