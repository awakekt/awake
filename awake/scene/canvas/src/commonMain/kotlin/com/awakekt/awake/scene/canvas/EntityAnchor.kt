/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.foundation.layout.AnchorPoint
import com.awakekt.awake.compose.foundation.layout.AnchorProvider
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Anchors a Compose child of `WorldAnchorLayout` to [entity]'s world position, [offset] away in
 * world space, as [projector] reports the camera sees it: the Compose counterpart of a canvas
 * element that follows a node, for a nameplate or a bar over an entity.
 *
 * The child hides while the entity has no [Transform], the projector has no view, the point is behind
 * the camera, or it lands outside the layout. Fade, scale by distance and ranking are not applied:
 * a caller that wants them reads [com.awakekt.awake.ui.canvas.CanvasProjection.distance] through its
 * own provider.
 *
 * Its projection is the one [SceneCanvas] follows use, so both place a child on the same pixel. Like
 * them it allocates only what [CanvasProjector.projectFollow] does.
 */
fun World.entityAnchor(entity: Entity, projector: CanvasProjector, offset: Vec3f = Vec3f(0f, 0f, 0f)): AnchorProvider =
    AnchorProvider { into -> anchorEntity(entity, projector, offset, into) }

private fun World.anchorEntity(entity: Entity, projector: CanvasProjector, offset: Vec3f, into: AnchorPoint) {
    val matrix = get<Transform>(entity)?.worldMatrix
    val projection = matrix?.let { projector.projectFollow(it.m03 + offset.x, it.m13 + offset.y, it.m23 + offset.z) }
    if (projection == null || projection.behind) {
        into.visible = false
        return
    }
    into.x = projection.x
    into.y = projection.y
    into.visible = projection.x in 0f..into.surfaceWidth.toFloat() && projection.y in 0f..into.surfaceHeight.toFloat()
}
