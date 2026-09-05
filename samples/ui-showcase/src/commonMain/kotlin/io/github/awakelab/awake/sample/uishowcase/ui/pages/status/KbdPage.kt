/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.status

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnKbd

internal val KbdPage = ShowcasePage(
    id = "kbd",
    title = "Kbd",
    category = ShowcaseCategory.Status,
    description = "Displays a keyboard key or shortcut.",
    usageCode = """shadcnKbd("⌘K")""",
    referenceExample = "registry/new-york-v4/examples/kbd-demo.tsx",
    previewHeight = 260,
    notes = listOf("Subtle border box representing physical keys."),
    hero = {
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(8.dp),
            modifier = Modifier.height(24.dp),
        ) {
            ShadcnKbd("⌘")
            ShadcnKbd("K")
            ShadcnKbd("Shift")
        }
    },
)
