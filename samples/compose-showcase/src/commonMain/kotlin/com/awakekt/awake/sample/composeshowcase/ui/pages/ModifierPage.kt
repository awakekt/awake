/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.composeshowcase.ui.pages

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.aspectRatio
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.sizeIn
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.layout.widthIn
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.sample.composeshowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.composeshowcase.ui.ShowcasePage

private val ModifierBlue = Color.fromHex(0x2563EB)
private val ModifierGreen = Color.fromHex(0x16A34A)
private val ModifierOrange = Color.fromHex(0xEA580C)
private val ModifierPurple = Color.fromHex(0x7C3AED)

internal val ModifierPage = ShowcasePage(
    id = "modifiers",
    title = "Modifiers",
    category = ShowcaseCategory.Modifiers,
    description = "Layout, drawing, transform, and sizing modifiers composed in the order they are written.",
    notes = listOf(
        "Modifiers are a chain: order changes measurement, painting, and hit-target behavior.",
        "offset changes placement without changing the space claimed by siblings.",
        "sizeIn and widthIn constrain content while aspectRatio derives the missing dimension.",
    ),
    demo = {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            DemoSectionLabel("Sizing and spacing")
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(12.dp)) {
                modifierSwatch("padding", Modifier.width(110.dp).height(58.dp).background(ModifierBlue).padding(12.dp))
                modifierSwatch("sizeIn", Modifier.sizeIn(minWidth = 84.dp, minHeight = 44.dp).background(ModifierGreen))
                modifierSwatch("aspectRatio", Modifier.width(100.dp).aspectRatio(2f).background(ModifierOrange))
            }

            DemoSectionLabel("Offset, alpha, scale, and clip")
            Row(
                modifier = Modifier.height(100.dp),
                horizontalArrangement = Arrangement.spacedByHorizontal(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                modifierSwatch("offset", Modifier.size(76.dp).offset(x = 10.dp, y = (-8).dp).background(ModifierPurple))
                modifierSwatch("alpha", Modifier.size(76.dp).alpha(0.45f).background(ModifierBlue))
                modifierSwatch("scale", Modifier.size(76.dp).graphicsLayer(scaleX = 1.18f, scaleY = 0.72f).background(ModifierGreen))
                modifierSwatch(
                    "clip",
                    Modifier.size(76.dp).background(ModifierOrange).clip(RoundedCornerShape(18.dp)),
                )
            }

            DemoSectionLabel("Border and modifier composition")
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(12.dp)) {
                modifierSwatch(
                    "border then padding",
                    Modifier.width(150.dp).border(2.dp, ModifierBlue, 8.dp).padding(10.dp),
                )
                modifierSwatch(
                    "background then border",
                    Modifier.width(150.dp).background(ModifierGreen).border(2.dp, Color.White, 8.dp),
                )
            }
        }
    },
)

context(composer: com.awakekt.awake.compose.runtime.Composer)
private fun modifierSwatch(label: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(label, style = TextStyle.Default.copy(color = Color.White))
    }
}
