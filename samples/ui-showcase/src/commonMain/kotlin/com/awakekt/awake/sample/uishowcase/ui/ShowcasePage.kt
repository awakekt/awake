/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.uishowcase.ui

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.RowScope
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.state.UiShowcaseRuntimeState
import com.awakekt.awake.ui.shadcn.components.ShadcnEmpty
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

internal typealias ShowcaseRenderer = context(Composer)
(UiShowcaseRuntimeState) -> Unit

internal enum class ShowcaseCategory(val title: String) {
    GettingStarted("Getting Started"),
    Inputs("Inputs"),
    Layout("Layout"),
    Overlays("Overlays"),
    Status("Status"),
    Typography("Typography"),
    Blocks("Blocks"),
}

/**
 * [Placeholder] pages are registered on purpose: a component shadcn ships that Awake has not
 * built yet stays visible in the catalog instead of being silently absent. Removing a page is
 * therefore always a deliberate act, never the side effect of forgetting to add one.
 */
internal enum class ShowcaseStatus { Ready, Placeholder }

/**
 * One catalog entry. [hero] is the page's primary sample -- a port of [referenceExample] where
 * one exists. [variants] and [states] are static matrices; interaction coverage belongs in
 * `:awake:ui:shadcn` tests, not in extra showcase pages.
 *
 * [previewWidth]/[previewHeight] are the raster size the preview and layout-signature tests
 * render this page at. They live here rather than in a JVM annotation so the tests run on
 * every target, not only the reflective ones.
 */
internal class ShowcasePage(
    val id: String,
    val title: String,
    val category: ShowcaseCategory,
    val description: String,
    val usageCode: String,
    /** Path under `third_party/shadcn-ui-ref/apps/v4/` this hero is ported from, or "". */
    val referenceExample: String = "",
    val status: ShowcaseStatus = ShowcaseStatus.Ready,
    val previewWidth: Int = 720,
    val previewHeight: Int = 420,
    val notes: List<String> = emptyList(),
    val hero: ShowcaseRenderer,
    val variants: ShowcaseRenderer? = null,
    val states: ShowcaseRenderer? = null,
)

/** A component shadcn ships that Awake has not built yet. Renders as an explicit gap. */
internal fun showcasePlaceholder(
    id: String,
    title: String,
    category: ShowcaseCategory,
    description: String,
    missing: String,
    referenceExample: String = "",
): ShowcasePage = ShowcasePage(
    id = id,
    title = title,
    category = category,
    description = description,
    usageCode = "// not implemented -- $missing",
    referenceExample = referenceExample,
    status = ShowcaseStatus.Placeholder,
    previewHeight = 260,
    notes = listOf("Missing: $missing"),
    hero = { drawShowcasePlaceholder(title, missing, referenceExample) },
)

context(_: Composer)
private fun drawShowcasePlaceholder(
    title: String,
    missing: String,
    referenceExample: String,
) {
    ShadcnEmpty(title = "$title is not implemented", description = missing)
    if (referenceExample.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        shadcnMuted("Reference: $referenceExample")
    }
}

/** Lays [items] out in wrapped rows -- the shared shape of every variant/state matrix. */
context(_: Composer)
internal fun <T> showcaseMatrix(
    items: List<T>,
    perRow: Int = 4,
    gap: Dp = 12.dp,
    cell: context(Composer) RowScope.(T) -> Unit,
) {
    items.chunked(perRow).forEachIndexed { index, chunk ->
        if (index > 0) Spacer(Modifier.height(gap))
        Row(horizontalArrangement = Arrangement.spacedByHorizontal(gap)) {
            chunk.forEach { cell(it) }
        }
    }
}
