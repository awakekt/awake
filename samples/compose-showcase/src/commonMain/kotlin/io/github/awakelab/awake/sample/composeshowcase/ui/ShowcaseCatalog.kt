/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.composeshowcase.ui

import io.github.awakelab.awake.sample.composeshowcase.ui.pages.BoxPage
import io.github.awakelab.awake.sample.composeshowcase.ui.pages.ColumnPage
import io.github.awakelab.awake.sample.composeshowcase.ui.pages.FlexBoxPage
import io.github.awakelab.awake.sample.composeshowcase.ui.pages.FlowColumnPage
import io.github.awakelab.awake.sample.composeshowcase.ui.pages.FlowRowPage
import io.github.awakelab.awake.sample.composeshowcase.ui.pages.RowPage
import io.github.awakelab.awake.sample.composeshowcase.ui.pages.WeightPage

/**
 * The single showcase catalog. Adding a page here is the only way to publish one -- same rule as
 * `samples/ui-showcase`'s `ShowcaseCatalog.kt`, for the same reason: no second, test-only list.
 */
internal val ShowcasePages: List<ShowcasePage> = listOf(
    RowPage,
    ColumnPage,
    WeightPage,
    FlowRowPage,
    FlowColumnPage,
    FlexBoxPage,
    BoxPage,
)

internal val ShowcasePagesByCategory: Map<ShowcaseCategory, List<ShowcasePage>> =
    ShowcasePages.groupBy { it.category }

internal fun showcasePageOrNull(pageId: String): ShowcasePage? =
    ShowcasePages.firstOrNull { it.id == pageId }

internal fun showcasePageById(pageId: String): ShowcasePage =
    requireNotNull(showcasePageOrNull(pageId)) { "Unknown showcase page id: $pageId" }
