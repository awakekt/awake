/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnKbd
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSeparatorOrientation
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

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
