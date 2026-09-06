/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.uishowcase.ui.pages.layout

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.state.UiShowcaseRuntimeState
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

internal val AspectRatioPage = ShowcasePage(
    id = "aspect-ratio",
    title = "AspectRatio",
    category = ShowcaseCategory.Layout,
    description = "Displays content within a fixed aspect ratio container.",
    usageCode = "// TODO: Add usage snippet",
    hero = { AspectRatioHero() },
)

context(_: Composer)
private fun AspectRatioHero() {
    Column {
        shadcnMuted("AspectRatio component interactive preview.")
        Spacer(Modifier.height(12.dp))
        // TODO: Render component here
    }
}
