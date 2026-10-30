/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldtext

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.worldtext.WorldText
import com.awakekt.awake.worldtext.WorldTextStyle

// Where a scene file's `world_text` becomes the live WorldText and back. It is the only place the two meet,
// so an option added to either side is wrong until it is handled here: WorldTextMappingTest fails when a
// style option is neither mapped from the scene file nor listed there as set from code only.

/** [this] scene file as a live [WorldText]. */
internal fun SceneWorldText.toWorldText(): WorldText = WorldText(text, toStyle(), billboard)

/** The look options of [this] scene file as a [WorldTextStyle], in the built-in face. */
internal fun SceneWorldText.toStyle(): WorldTextStyle = WorldTextStyle(
    size = size,
    weight = FontWeight(weight),
    color = Color(color.r, color.g, color.b, color.a),
    align = align,
    lineSpacing = lineSpacing,
)

/** [this] live text as the scene file it came from, as an editor saves it. */
internal fun WorldText.toScene(): SceneWorldText = SceneWorldText(
    text = text,
    size = style.size,
    color = style.color.let { SceneColor(it.r, it.g, it.b, it.a) },
    weight = style.weight.value,
    align = style.align,
    lineSpacing = style.lineSpacing,
    billboard = billboard,
)
