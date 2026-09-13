/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.uishowcase.ui.pages.layout

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.aspectRatio
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.shadcnMuted
import com.awakekt.awake.ui.shadcn.components.shadcnSurface
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme

internal val AspectRatioPage = ShowcasePage(
    id = "aspect-ratio",
    title = "AspectRatio",
    category = ShowcaseCategory.Layout,
    description = "Displays content within a fixed aspect ratio container.",
    usageCode = """shadcnSurface(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
    ShadcnText(\"Media preview\")
}""",
    referenceExample = "registry/new-york-v4/examples/aspect-ratio-demo.tsx",
    previewWidth = 560,
    previewHeight = 420,
    notes = listOf(
        "The width is constrained by the preview rail and the height is derived from the 16:9 ratio.",
        "The same modifier can preserve square, portrait, and other media ratios.",
    ),
    hero = { AspectRatioHero() },
)

context(_: Composer)
private fun AspectRatioHero() {
    val theme = shadcnTheme

    Column {
        shadcnMuted("A media frame that keeps its 16:9 shape as the available width changes.")
        Spacer(Modifier.height(12.dp))
        shadcnSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            contentPadding = 0.dp,
            cornerRadius = theme.radii.lg,
        ) {
            Column {
                ShadcnText("16:9", color = theme.palette.mutedForeground)
                shadcnMuted("Aspect-ratio constrained content")
            }
        }
    }
}
