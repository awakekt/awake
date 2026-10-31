/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.lod.LodQuality
import com.awakekt.awake.ecs.Entity

/**
 * Makes an [LodGroup] switch with another entity's group instead of by its own thresholds: it
 * draws the [leader]'s level plus [levelOffset], within its own [LodGroup.policy]. The parts of a
 * composed or skinned character follow its body this way, so they switch on the same frame; an
 * offset of `-1` keeps a head one level finer than the body it follows. A leader with no
 * [LodGroup] leaves the follower on its own thresholds.
 */
data class LodFollow(val leader: Entity, val levelOffset: Int = 0)

/** The host's level-of-detail graphics setting for every [LodGroup], read every frame. */
object SceneLod {
    /** The global bias and coverage scale; see [LodQuality]. */
    var quality: LodQuality = LodQuality.Default
}
