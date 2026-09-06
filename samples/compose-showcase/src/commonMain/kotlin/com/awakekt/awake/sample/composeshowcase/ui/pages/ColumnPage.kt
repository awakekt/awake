/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.composeshowcase.ui.pages

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.composeshowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.composeshowcase.ui.ShowcasePage

internal val ColumnPage = ShowcasePage(
    id = "column",
    title = "Column",
    category = ShowcaseCategory.RowColumn,
    description = "Lays children out top to bottom -- the same policy as Row, transposed.",
    notes = listOf(
        "verticalArrangement distributes leftover main-axis space.",
        "horizontalAlignment positions each child on the cross axis; children have different widths here on purpose.",
    ),
    demo = {
        Row(Modifier.height(220.dp)) {
            listOf(
                "Top" to Arrangement.Top,
                "Center" to Arrangement.Center,
                "SpaceBetween" to Arrangement.SpaceBetween,
                "spacedBy(16.dp)" to Arrangement.spacedBy(16.dp),
            ).forEach { (label, arrangement) ->
                DemoColumn(label, arrangement)
            }
        }
    },
)

context(composer: com.awakekt.awake.compose.runtime.Composer)
private fun DemoColumn(label: String, arrangement: Arrangement.Vertical) {
    Column(Modifier.width(160.dp)) {
        DemoSectionLabel(label)
        Column(
            modifier = Modifier.height(140.dp),
            verticalArrangement = arrangement,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            swatch("1", 40.dp, 24.dp, SwatchColors[0])
            swatch("2", 70.dp, 24.dp, SwatchColors[1])
            swatch("3", 50.dp, 24.dp, SwatchColors[2])
        }
    }
}
