/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.sample.composeshowcase.ui.pages

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.FlowRow
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.composeshowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.composeshowcase.ui.ShowcasePage

internal val FlowRowPage = ShowcasePage(
    id = "flow-row",
    title = "FlowRow",
    category = ShowcaseCategory.Flow,
    description = "A Row that wraps onto a new line instead of overflowing when children don't fit.",
    notes = listOf(
        "Resize the window narrower to watch items reflow onto more lines.",
        "maxItemsInEachRow and maxLines cap wrapping; both default to unbounded.",
    ),
    demo = {
        FlowRow(
            modifier = Modifier.width(360.dp),
            horizontalArrangement = Arrangement.spacedByHorizontal(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (index in 1..9) {
                swatch("$index", 70.dp, 44.dp, SwatchColors[index % SwatchColors.size])
            }
        }
    },
)
