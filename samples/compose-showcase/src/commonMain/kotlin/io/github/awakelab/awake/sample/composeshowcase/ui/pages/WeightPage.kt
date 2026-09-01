/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.sample.composeshowcase.ui.pages

import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.composeshowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.composeshowcase.ui.ShowcasePage

internal val WeightPage = ShowcasePage(
    id = "weight",
    title = "Modifier.weight",
    category = ShowcaseCategory.RowColumn,
    description = "Claims a share of the main-axis space its unweighted siblings did not take.",
    notes = listOf(
        "Two equal weights split the leftover space evenly.",
        "A 2:1 ratio gives the heavier child twice the leftover space, not twice its total size.",
        "weight is a RowScope/ColumnScope extension -- only callable inside that Row/Column's own content lambda.",
    ),
    demo = {
        Column(Modifier.fillMaxWidth()) {
            DemoSectionLabel("weight(1f) x2 -- even split of what's left")
            Row(Modifier.fillMaxWidth().height(48.dp)) {
                swatch("fixed 80dp", Modifier.width(80.dp).height(48.dp), SwatchColors[0])
                swatch("weight(1f)", Modifier.weight(1f).height(48.dp), SwatchColors[1])
                swatch("weight(1f)", Modifier.weight(1f).height(48.dp), SwatchColors[2])
            }
            Spacer(Modifier.height(20.dp))
            DemoSectionLabel("weight(2f) vs weight(1f) -- 2:1 split of what's left")
            Row(Modifier.fillMaxWidth().height(48.dp)) {
                swatch("fixed 80dp", Modifier.width(80.dp).height(48.dp), SwatchColors[0])
                swatch("weight(2f)", Modifier.weight(2f).height(48.dp), SwatchColors[1])
                swatch("weight(1f)", Modifier.weight(1f).height(48.dp), SwatchColors[2])
            }
        }
    },
)
