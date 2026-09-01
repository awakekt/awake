/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.sample.composeshowcase.ui.pages

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.FlowColumn
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.composeshowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.composeshowcase.ui.ShowcasePage

internal val FlowColumnPage = ShowcasePage(
    id = "flow-column",
    title = "FlowColumn",
    category = ShowcaseCategory.Flow,
    description = "The vertical transpose of FlowRow -- wraps onto a new column when children don't fit.",
    notes = listOf(
        "maxItemsInEachColumn caps how many children each column holds before starting the next.",
    ),
    demo = {
        FlowColumn(
            modifier = Modifier.height(220.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedByHorizontal(8.dp),
            maxItemsInEachColumn = 3,
        ) {
            for (index in 1..9) {
                swatch("$index", 70.dp, 44.dp, SwatchColors[index % SwatchColors.size])
            }
        }
    },
)
