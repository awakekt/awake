/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.heightIn
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ui.shadcn.components.ShadcnCheckbox
import com.awakekt.awake.ui.shadcn.components.ShadcnSlider
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import kotlin.math.roundToInt

private val ROW_GAP: Dp = 6.dp
private val DEBUG_TARGET_HEIGHT: Dp = 44.dp

internal fun Float.oneDecimal(): String = ((this * 10f).roundToInt() / 10f).toString()

context(_: Composer)
internal fun Toggle(label: String, tag: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = DEBUG_TARGET_HEIGHT)
            .testTag("$tag-row").clickable { onChange(!checked) }.padding(vertical = ROW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnCheckbox(
            checked = checked,
            modifier = Modifier.testTag(tag),
            onCheckedChange = onChange,
        )
        ShadcnText(
            label,
            modifier = Modifier.padding(start = ROW_GAP),
            variant = ShadcnTextVariant.Small,
        )
    }
}

/** Reusable float slider field with label, readout, and slider track. */
context(_: Composer)
internal fun FloatSliderField(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    tag: String? = null,
    enabled: Boolean = true,
    format: (Float) -> String = { it.oneDecimal() },
    onValueChange: (Float) -> Unit,
) {
    Column(modifier.padding(top = ROW_GAP)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnText(label, variant = ShadcnTextVariant.Small)
            Spacer(Modifier.weight(1f))
            ShadcnText(format(value), variant = ShadcnTextVariant.Muted)
        }
        val sliderMod = Modifier.padding(top = 4.dp).fillMaxWidth()
        ShadcnSlider(
            value = value,
            min = min,
            max = max,
            steps = steps,
            enabled = enabled,
            modifier = if (tag != null) sliderMod.testTag(tag) else sliderMod,
            onValueChange = onValueChange,
        )
    }
}

/** Reusable 2D vector field with X and Y sliders. */
context(_: Composer)
internal fun Vec2SliderField(
    label: String,
    x: Float,
    y: Float,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 1f,
    steps: Int = 0,
    tagPrefix: String? = null,
    enabled: Boolean = true,
    onValueChange: (Float, Float) -> Unit,
) {
    Column(modifier.padding(top = ROW_GAP)) {
        ShadcnText(label, variant = ShadcnTextVariant.Small)
        FloatSliderField(
            label = "X",
            value = x,
            min = min,
            max = max,
            steps = steps,
            tag = tagPrefix?.let { "$it-x" },
            enabled = enabled,
            onValueChange = { onValueChange(it, y) },
        )
        FloatSliderField(
            label = "Y",
            value = y,
            min = min,
            max = max,
            steps = steps,
            tag = tagPrefix?.let { "$it-y" },
            enabled = enabled,
            onValueChange = { onValueChange(x, it) },
        )
    }
}

/** Reusable 3D vector field with X, Y, and Z sliders. */
context(_: Composer)
internal fun Vec3SliderField(
    label: String,
    x: Float,
    y: Float,
    z: Float,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 1f,
    steps: Int = 0,
    labels: Triple<String, String, String> = Triple("X", "Y", "Z"),
    tagPrefix: String? = null,
    enabled: Boolean = true,
    onValueChange: (Float, Float, Float) -> Unit,
) {
    Column(modifier.padding(top = ROW_GAP)) {
        ShadcnText(label, variant = ShadcnTextVariant.Small)
        FloatSliderField(
            label = labels.first,
            value = x,
            min = min,
            max = max,
            steps = steps,
            tag = tagPrefix?.let { "$it-x" },
            enabled = enabled,
            onValueChange = { onValueChange(it, y, z) },
        )
        FloatSliderField(
            label = labels.second,
            value = y,
            min = min,
            max = max,
            steps = steps,
            tag = tagPrefix?.let { "$it-y" },
            enabled = enabled,
            onValueChange = { onValueChange(x, it, z) },
        )
        FloatSliderField(
            label = labels.third,
            value = z,
            min = min,
            max = max,
            steps = steps,
            tag = tagPrefix?.let { "$it-z" },
            enabled = enabled,
            onValueChange = { onValueChange(x, y, it) },
        )
    }
}

/** Reusable RGB Color field with live color preview swatch and component sliders. */
context(_: Composer)
internal fun ColorRgbField(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    tagPrefix: String? = null,
    enabled: Boolean = true,
    onColorChange: (Color) -> Unit,
) {
    Column(modifier.padding(top = ROW_GAP)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnText(label, variant = ShadcnTextVariant.Small)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(16.dp)
                    .background(color, RoundedCornerShape(3.dp))
                    .border(1.dp, ShowcaseTheme.palette.border, RoundedCornerShape(3.dp)),
            )
        }
        FloatSliderField(
            label = "R",
            value = color.r,
            min = 0f,
            max = 1f,
            steps = 100,
            tag = tagPrefix?.let { "$it-r" },
            enabled = enabled,
            format = { (it * 100f).roundToInt().toString() + "%" },
            onValueChange = { onColorChange(Color(it, color.g, color.b, color.a)) },
        )
        FloatSliderField(
            label = "G",
            value = color.g,
            min = 0f,
            max = 1f,
            steps = 100,
            tag = tagPrefix?.let { "$it-g" },
            enabled = enabled,
            format = { (it * 100f).roundToInt().toString() + "%" },
            onValueChange = { onColorChange(Color(color.r, it, color.b, color.a)) },
        )
        FloatSliderField(
            label = "B",
            value = color.b,
            min = 0f,
            max = 1f,
            steps = 100,
            tag = tagPrefix?.let { "$it-b" },
            enabled = enabled,
            format = { (it * 100f).roundToInt().toString() + "%" },
            onValueChange = { onColorChange(Color(color.r, color.g, it, color.a)) },
        )
    }
}
