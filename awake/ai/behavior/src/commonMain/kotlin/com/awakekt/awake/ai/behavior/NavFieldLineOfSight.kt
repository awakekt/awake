/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.ai.perception.LineOfSight
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.navigation.grid.NavField
import com.awakekt.awake.navigation.grid.hasLineOfSight

/**
 * Line of sight over a navigation grid: a line is clear when every cell it crosses on the ground
 * plane is walkable, so the grid's blocked cells are the walls perception cannot see through.
 * Heights are ignored, so nothing is seen over a low wall.
 *
 * @property field The grid.
 */
class NavFieldLineOfSight(val field: NavField) : LineOfSight {
    override fun isClear(from: Vec3f, to: Vec3f, viewer: Entity, target: Entity?): Boolean = field.hasLineOfSight(from, to)
}
