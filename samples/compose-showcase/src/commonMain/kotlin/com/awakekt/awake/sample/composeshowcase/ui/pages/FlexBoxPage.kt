/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.composeshowcase.ui.pages

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.FlexBox
import com.awakekt.awake.compose.foundation.layout.FlexDirection
import com.awakekt.awake.compose.foundation.layout.FlexJustifyContent
import com.awakekt.awake.compose.foundation.layout.FlexWrap
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.flex
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.composeshowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.composeshowcase.ui.ShowcasePage

internal val FlexBoxPage = ShowcasePage(
    id = "flexbox",
    title = "FlexBox",
    category = ShowcaseCategory.FlexBox,
    description = "CSS-Flexbox-shaped layout: direction, wrap, grow/shrink, order and alignment, all in one container.",
    notes = listOf(
        "grow(2f) vs grow(1f) below claims twice the leftover main-axis space, same as Row's weight.",
        "order() reorders visual placement independent of declaration order; unset items keep declaration order among themselves.",
        "direction = Column swaps which axis is \"main\" without changing any child code.",
    ),
    demo = {
        Column(Modifier.fillMaxWidth()) {
            DemoSectionLabel("grow -- 2:1 split of what's left, same math as Row's weight")
            FlexBox(Modifier.fillMaxWidth().height(48.dp)) {
                swatch("fixed 80dp", Modifier.width(80.dp).height(48.dp), SwatchColors[0])
                swatch("grow(2f)", Modifier.flex { grow(2f) }.height(48.dp), SwatchColors[1])
                swatch("grow(1f)", Modifier.flex { grow(1f) }.height(48.dp), SwatchColors[2])
            }

            Spacer(Modifier.height(20.dp))
            DemoSectionLabel("order -- item 2 renders visually first")
            FlexBox(Modifier.fillMaxWidth().height(48.dp)) {
                swatch("1", Modifier.width(70.dp).height(48.dp), SwatchColors[0])
                swatch("2 (order=-1)", Modifier.flex { order(-1) }.width(90.dp).height(48.dp), SwatchColors[1])
                swatch("3", Modifier.width(70.dp).height(48.dp), SwatchColors[2])
            }

            Spacer(Modifier.height(20.dp))
            DemoSectionLabel("wrap -- narrower than 4 fixed children, so a 4th line starts")
            FlexBox(
                modifier = Modifier.width(240.dp),
                config = {
                    wrap = FlexWrap.Wrap
                    gap(8.dp)
                },
            ) {
                for (index in 1..4) {
                    swatch("$index", 70.dp, 44.dp, SwatchColors[index % SwatchColors.size])
                }
            }

            Spacer(Modifier.height(20.dp))
            DemoSectionLabel("direction = Column, justifyContent = SpaceBetween")
            FlexBox(
                modifier = Modifier.width(120.dp).height(180.dp),
                config = {
                    direction = FlexDirection.Column
                    justifyContent = FlexJustifyContent.SpaceBetween
                },
            ) {
                swatch("1", 100.dp, 32.dp, SwatchColors[0])
                swatch("2", 100.dp, 32.dp, SwatchColors[1])
                swatch("3", 100.dp, 32.dp, SwatchColors[2])
            }
        }
    },
)
