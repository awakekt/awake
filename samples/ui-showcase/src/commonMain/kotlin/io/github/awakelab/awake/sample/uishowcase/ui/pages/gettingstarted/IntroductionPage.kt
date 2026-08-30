/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.gettingstarted

import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage

internal val IntroductionPage = ShowcasePage(
    id = "introduction",
    title = "Introduction",
    category = ShowcaseCategory.GettingStarted,
    description = "Re-usable components built with shadcn/ui, ported to the Awake compose engine.",
    usageCode = "// This page has no single usage snippet -- it is the catalog's own front page.",
    previewWidth = 920,
    previewHeight = 320,
    hero = { ShowcaseOverviewPreview() },
    variants = { ShowcaseReferenceComparisonPreview() },
)
