/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.trail

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.trail.TrailBlend
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// This file is the scene-document schema of `trail_renderer` and nothing else. TrailBinding attaches it
// as the node's live TrailRenderer through TrailMapping, and TrailRenderFeature draws it.

/**
 * A trail drawn at its node, such as a swing's ribbon, a projectile's streak or a tyre mark: a
 * [com.awakekt.awake.trail.TrailRenderer] authored as data. The document says how it is drawn; its
 * point pairs, how many show and their colours are a game's systems' to write every frame.
 *
 * @property capacity The most point pairs it holds; its GPU buffer is sized for this once.
 * @property blend Whether it adds to what is behind it or covers it by its alpha; the names of
 *   [TrailBlend]'s constants, which are part of the file format.
 * @property tint A straight-alpha colour multiplied into every vertex's own.
 */
@Serializable
@SerialName("trail_renderer")
data class SceneTrailRenderer(
    @PropertyRange(min = 2.0, max = 65536.0) val capacity: Int = 64,
    val blend: TrailBlend = TrailBlend.Additive,
    val tint: SceneColor = SceneColor(),
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> =
        if (capacity in MIN_CAPACITY..MAX_CAPACITY) {
            emptyList()
        } else {
            listOf(SceneValidationIssue(path, "trail_renderer.capacity must be within $MIN_CAPACITY..$MAX_CAPACITY"))
        }
}

private const val MIN_CAPACITY = 2
private const val MAX_CAPACITY = 65536
