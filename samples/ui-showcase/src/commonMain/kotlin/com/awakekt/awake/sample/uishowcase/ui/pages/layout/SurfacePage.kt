/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.layout

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.shadcnMuted
import com.awakekt.awake.ui.shadcn.components.shadcnSurface

internal val SurfacePage = ShowcasePage(
    id = "surface",
    title = "Surface",
    category = ShowcaseCategory.Layout,
    description = "The neutral themed container every other shadcn recipe is built on.",
    usageCode = """shadcnSurface { ShadcnText("Content") }""",
    previewHeight = 380,
    notes = listOf("Card, dialog, popover, and sidebar all resolve their chrome through this primitive."),
    hero = {
        shadcnSurface(modifier = Modifier.fillMaxWidth()) {
            ShadcnText("Default surface")
            shadcnMuted("Background, border, and radius come from the active theme.")
        }
        Spacer(Modifier.height(12.dp))
        shadcnSurface(modifier = Modifier.fillMaxWidth(), bordered = false) {
            ShadcnText("Unbordered surface")
            shadcnMuted("Used for inset panels and preview wells.")
        }
    },
)
