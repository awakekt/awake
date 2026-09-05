/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.navigation.grid

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity

/**
 * One agent's current path, for [navGridDebugLines] to draw.
 *
 * A value the caller supplies rather than something this module queries. Reading `ChaseBehavior`
 * out of the world -- which is what this did -- made `scene:navigation` depend on `scene:ai`, and
 * `scene:ai` cannot help depending on navigation to request a path, so the two could never be
 * separate modules. It was also wrong on its own terms: `path` belongs to `RouteFollower`, so
 * patrol and flee have one too, and drawing only chasers silently omitted them.
 */
data class AgentRoute(val entity: Entity, val path: List<Vec3f>)
