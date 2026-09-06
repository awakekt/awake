/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.status

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnProgress

internal val ProgressPage = ShowcasePage(
    id = "progress",
    title = "Progress",
    category = ShowcaseCategory.Status,
    description = "Displays an indicator showing the completion progress of a task.",
    usageCode = """ShadcnProgress(progress = 0.65f)""",
    referenceExample = "registry/new-york-v4/examples/progress-demo.tsx",
    previewHeight = 320,
    notes = listOf("Animated progress fill bar."),
    hero = {
        ShadcnProgress(progress = 0.65f, modifier = Modifier.width(260.dp))
    },
    states = {
        listOf(0.25f, 0.65f, 1f).forEach { value ->
            ShadcnProgress(progress = value, modifier = Modifier.width(260.dp))
            Spacer(Modifier.height(12.dp))
        }
    },
)
