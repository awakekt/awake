/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.composeshowcase.ui.pages

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextStyle

/**
 * A colored rectangle with a centered label -- the one visual unit every page in this catalog is
 * built from. Deliberately not a themed component: the point of this showcase is to make measure
 * and placement obvious, not to demonstrate design-system styling (that is `samples/ui-showcase`'s
 * job).
 */
context(composer: Composer)
fun swatch(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = SwatchColors[0],
) {
    Box(modifier.background(color), contentAlignment = Alignment.Center) {
        Text(label, style = TextStyle.Default.copy(color = Color.White))
    }
}

/** Fixed-size convenience over [swatch], for demos that only care about main/cross-axis size. */
context(composer: Composer)
fun swatch(
    label: String,
    width: Dp,
    height: Dp,
    color: Color = SwatchColors[0],
) {
    swatch(label, Modifier.width(width).height(height), color)
}

context(composer: Composer)
fun DemoSectionLabel(text: String) {
    Box(Modifier.padding(bottom = 4.dp)) {
        Text(text, style = TextStyle.Default.copy(color = Color.fromHex(0x71717A)))
    }
}

/** A rotating palette so a row of swatches reads as distinct children at a glance. */
val SwatchColors = listOf(
    Color.fromHex(0x3B82F6),
    Color.fromHex(0xEF4444),
    Color.fromHex(0x22C55E),
    Color.fromHex(0xF59E0B),
    Color.fromHex(0xA855F7),
    Color.fromHex(0x06B6D4),
)
