/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.sample.composeshowcase.ui.pages

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.composeshowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.composeshowcase.ui.ShowcasePage

internal val RowPage = ShowcasePage(
    id = "row",
    title = "Row",
    category = ShowcaseCategory.RowColumn,
    description = "Lays children out left to right, one measure pass each, no wrapping.",
    notes = listOf(
        "horizontalArrangement distributes leftover main-axis space.",
        "verticalAlignment positions each child on the cross axis; children have different heights here on purpose.",
    ),
    demo = {
        Column(Modifier.fillMaxWidth()) {
            listOf(
                "Start" to Arrangement.Start,
                "CenterHorizontally" to Arrangement.CenterHorizontally,
                "SpaceBetweenHorizontal" to Arrangement.SpaceBetweenHorizontal,
                "spacedByHorizontal(16.dp)" to Arrangement.spacedByHorizontal(16.dp),
            ).forEach { (label, arrangement) ->
                DemoSectionLabel(label)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = arrangement,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    swatch("1", 60.dp, 40.dp, SwatchColors[0])
                    swatch("2", 60.dp, 70.dp, SwatchColors[1])
                    swatch("3", 60.dp, 50.dp, SwatchColors[2])
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    },
)
