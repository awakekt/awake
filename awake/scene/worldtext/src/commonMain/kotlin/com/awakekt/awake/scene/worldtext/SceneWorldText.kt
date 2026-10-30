/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldtext

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.worldtext.WorldText
import com.awakekt.awake.worldtext.WorldTextAlign
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// This file is the scene-document schema of `world_text` and nothing else. WorldTextBinding attaches it
// as the node's live WorldText through WorldTextMapping, and WorldTextRenderFeature draws it.

/**
 * Text placed in the scene at its node, such as a sign or a label over an object: a [WorldText]
 * authored as data. It is drawn from the built-in face's distance field, sharp at any distance and
 * angle, and sorts with the scene's other transparent draws.
 *
 * @property text What it says; each `\n` starts a line.
 * @property size The em size, in world units, before the node's scale.
 * @property color Its colour, straight alpha.
 * @property weight The face's weight, 100 to 900: 400 regular, 700 bold.
 * @property align How each line lines up across the text's width; the names of [WorldTextAlign]'s
 *   constants, which are part of the file format.
 * @property lineSpacing One line's top to the next, as a multiple of the face's line height.
 * @property billboard Whether it turns to face the camera every frame, keeping the node's place and
 *   scale; otherwise it lies in the node's XY plane, facing the node's +Z.
 */
@Serializable
@SerialName("world_text")
data class SceneWorldText(
    val text: String = "",
    @PropertyRange(min = 0.0, exclusiveMin = true) val size: Float = 0.25f,
    val color: SceneColor = SceneColor(),
    @PropertyRange(min = 100.0, max = 900.0) val weight: Int = 400,
    val align: WorldTextAlign = WorldTextAlign.Center,
    @PropertyRange(min = 0.0, exclusiveMin = true) val lineSpacing: Float = 1f,
    val billboard: Boolean = false,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> {
        val problems = ArrayList<String>()
        if (!(size > 0f && size.isFinite())) problems += "size must be a positive number of world units"
        if (weight !in MIN_WEIGHT..MAX_WEIGHT) problems += "weight must be within $MIN_WEIGHT..$MAX_WEIGHT"
        if (!(lineSpacing > 0f && lineSpacing.isFinite())) problems += "lineSpacing must be positive"
        return problems.map { SceneValidationIssue(path, "world_text.$it") }
    }
}

private const val MIN_WEIGHT = 100
private const val MAX_WEIGHT = 900
