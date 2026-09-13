/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.status

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnSpinner

internal val SpinnerPage = ShowcasePage(
    id = "spinner",
    title = "Spinner",
    category = ShowcaseCategory.Status,
    description = "An indeterminate loading indicator.",
    usageCode = """ShadcnSpinner()""",
    referenceExample = "registry/new-york-v4/examples/spinner-demo.tsx",
    previewHeight = 260,
    notes = listOf("Continuous rotation loading indicator."),
    hero = {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            ShadcnSpinner()
        }
    },
)
