/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.layout

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.rememberScrollState
import com.awakekt.awake.compose.foundation.verticalScroll
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.shadcnMuted
import com.awakekt.awake.ui.shadcn.components.shadcnSurface

internal val ScrollAreaPage = ShowcasePage(
    id = "scroll-area",
    title = "Scroll Area",
    category = ShowcaseCategory.Layout,
    description = "Augments native scroll functionality with a constrained, clipped content region.",
    usageCode = """Column(Modifier.height(180.dp).verticalScroll(rememberScrollState())) { ... }""",
    referenceExample = "registry/new-york-v4/examples/scroll-area-demo.tsx",
    previewWidth = 520,
    previewHeight = 360,
    hero = {
        shadcnMuted("Scroll inside the constrained viewport.")
        shadcnSurface(modifier = Modifier.width(420.dp).height(180.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ShadcnText("Scrollable content")
                repeat(10) { index ->
                    shadcnMuted("Row ${index + 1}: content remains inside the viewport.")
                }
            }
        }
    },
)
