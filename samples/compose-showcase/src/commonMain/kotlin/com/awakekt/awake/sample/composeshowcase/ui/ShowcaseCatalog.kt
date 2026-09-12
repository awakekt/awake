/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.composeshowcase.ui

import com.awakekt.awake.sample.composeshowcase.ui.pages.BoxPage
import com.awakekt.awake.sample.composeshowcase.ui.pages.ColumnPage
import com.awakekt.awake.sample.composeshowcase.ui.pages.FlexBoxPage
import com.awakekt.awake.sample.composeshowcase.ui.pages.FlowColumnPage
import com.awakekt.awake.sample.composeshowcase.ui.pages.FlowRowPage
import com.awakekt.awake.sample.composeshowcase.ui.pages.ModifierPage
import com.awakekt.awake.sample.composeshowcase.ui.pages.RowPage
import com.awakekt.awake.sample.composeshowcase.ui.pages.StateDiPage
import com.awakekt.awake.sample.composeshowcase.ui.pages.StylePage
import com.awakekt.awake.sample.composeshowcase.ui.pages.WeightPage

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
    ModifierPage,
    StylePage,
    StateDiPage,
)

internal val ShowcasePagesByCategory: Map<ShowcaseCategory, List<ShowcasePage>> =
    ShowcasePages.groupBy { it.category }

internal fun showcasePageOrNull(pageId: String): ShowcasePage? =
    ShowcasePages.firstOrNull { it.id == pageId }

internal fun showcasePageById(pageId: String): ShowcasePage =
    requireNotNull(showcasePageOrNull(pageId)) { "Unknown showcase page id: $pageId" }
