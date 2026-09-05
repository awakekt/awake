/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.status

import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSpinner

internal val SpinnerPage = ShowcasePage(
    id = "spinner",
    title = "Spinner",
    category = ShowcaseCategory.Status,
    description = "An indeterminate loading indicator.",
    usageCode = """shadcnSpinner(size = 24.dp)""",
    referenceExample = "registry/new-york-v4/examples/spinner-demo.tsx",
    previewHeight = 260,
    notes = listOf("Continuous rotation loading indicator."),
    hero = {
        ShadcnSpinner(size = 24.dp)
    },
)
