/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.layout

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnKbd
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparator
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparatorOrientation
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

internal val SeparatorPage = ShowcasePage(
    id = "separator",
    title = "Separator",
    category = ShowcaseCategory.Layout,
    description = "Visually or semantically separates content.",
    usageCode = """ShadcnSeparator(orientation = ShadcnSeparatorOrientation.Vertical)""",
    referenceExample = "registry/new-york-v4/examples/separator-demo.tsx",
    previewHeight = 300,
    hero = {
        ShadcnText("Awake UI")
        shadcnMuted("An immediate-mode component library.")
        Spacer(Modifier.height(12.dp))
        ShadcnSeparator()
        Spacer(Modifier.height(12.dp))
        Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedByHorizontal(8.dp)) {
            ShadcnKbd("⌘K")
            ShadcnSeparator(
                modifier = Modifier.width(1.dp),
                orientation = ShadcnSeparatorOrientation.Vertical,
            )
            shadcnMuted("Open command menu")
        }
    },
)
