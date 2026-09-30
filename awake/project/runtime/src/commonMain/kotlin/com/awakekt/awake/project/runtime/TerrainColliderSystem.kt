/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent

/**
 * Gives each terrain that is `collider` a static heightfield body built from its own heightmap, laid
 * where its mesh lies, so characters and bodies stand on the ground they see. Register it before
 * `PhysicsSystem`, which builds the body.
 *
 * Built once per terrain: edits made to a heightmap afterwards do not reach its collider.
 */
class TerrainColliderSystem : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(TerrainComponent::class) { entity, terrain ->
            if (!terrain.collider || world.has(entity, PhysicsBody::class)) return@queryEach
            val heightmap = terrain.heightmap
            require(heightmap.width == heightmap.depth) {
                "A terrain collider needs a square heightmap, not ${heightmap.width} x ${heightmap.depth}"
            }
            val shape = HeightFieldShape(
                heights = heightmap.copySamples(),
                sampleCount = heightmap.width,
                scale = heightmap.scale,
                origin = heightmap.origin,
            )
            world.add(entity, PhysicsBody(shape = shape, motionType = MotionType.STATIC))
        }
    }
}
