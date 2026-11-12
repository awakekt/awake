/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.trail

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.trail.TrailRenderer

// Where a scene file's `trail_renderer` becomes the live TrailRenderer and back. It is the only place the
// two meet, so an option added to either side is wrong until it is handled here: TrailExposureTest fails
// when an option is neither mapped from the scene file nor listed there as set from code only.

/** [this] scene file as a live [TrailRenderer], with no pairs visible yet. */
internal fun SceneTrailRenderer.toTrailRenderer(): TrailRenderer = TrailRenderer(
    capacity = capacity,
    blend = blend,
    tint = Color(tint.r, tint.g, tint.b, tint.a),
    depthWrite = depthWrite,
    doubleSided = doubleSided,
)

/** Keeps the authored reference on the entity while the asset is loading. */
internal data class TrailTextureReference(val name: String)

/** [this] live trail as the scene file it came from, as an editor saves it. */
internal fun TrailRenderer.toScene(texture: TrailTextureReference?): SceneTrailRenderer = SceneTrailRenderer(
    capacity = capacity,
    blend = blend,
    tint = tint.let { SceneColor(it.r, it.g, it.b, it.a) },
    depthWrite = depthWrite,
    doubleSided = doubleSided,
    texture = texture?.name,
)
