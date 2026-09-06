/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.spatial

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.scene.rendering.RenderSystem
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial

/** Opt-in occluder for [RenderSystem]'s occlusion culling -- [localBounds] is that entity's
 * bounds in its own local (pre-transform) space, same
 * "build from `MeshGeometry.vertices` via `Aabb.fromPositions`" convention
 * [MeshBounds] uses. A scene author decides which entities occlude by attaching this component;
 * it is independent of [MeshBounds] -- an occluder does not need to be cullable itself (it can
 * carry both), and a cullable entity does not need to be an occluder. An entity with no
 * `Occluder` in the world never occludes anything (occlusion culling never runs at all when
 * zero entities carry this component), same "system reads whatever's currently set, entity
 * opts in" shape [MeshBounds]/[SkinnedPose]/[PbrMaterial] already use. */
data class Occluder(
    val localBounds: Aabb,
)
