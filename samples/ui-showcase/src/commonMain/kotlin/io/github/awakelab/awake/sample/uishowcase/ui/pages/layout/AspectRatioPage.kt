/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.state.UiShowcaseRuntimeState
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

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
