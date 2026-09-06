/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.composeshowcase.ui.pages

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.sample.composeshowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.composeshowcase.ui.ShowcasePage

internal val BoxPage = ShowcasePage(
    id = "box",
    title = "Box",
    category = ShowcaseCategory.Box,
    description = "Stacks children on top of each other; contentAlignment positions every child that doesn't set its own align().",
    notes = listOf(
        "With no size modifier, a bare Box shrink-wraps to its largest child.",
    ),
    demo = {
        Row(horizontalArrangement = Arrangement.spacedByHorizontal(16.dp)) {
            listOf(
                "TopStart" to Alignment.TopStart,
                "Center" to Alignment.Center,
                "BottomEnd" to Alignment.BottomEnd,
            ).forEach { (label, alignment) ->
                Box(
                    modifier = Modifier.width(120.dp).height(120.dp).background(Color(0.92f, 0.92f, 0.94f, 1f)),
                    contentAlignment = alignment,
                ) {
                    swatch(label, 60.dp, 40.dp, SwatchColors[0])
                }
            }
        }
    },
)
