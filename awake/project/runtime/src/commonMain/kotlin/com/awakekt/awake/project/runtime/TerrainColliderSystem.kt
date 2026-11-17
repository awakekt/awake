/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.terrain.clipmap.clipmapColliderFactor
import com.awakekt.awake.asset.terrain.clipmap.upsampledSamples
import com.awakekt.awake.core.math.Vec3f
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
 * The heightfield has the triangles the clipmap draws near the camera, not the raw samples (#1768).
 * Level 0 of a terrain's clipmap puts a vertex every `baseSpacing`, displaced by the heightmap sampled
 * bilinearly; a heightmap twice as coarse drawn this way has four cells a quad, with a centre of
 * `(a+b+c+d)/4`. A heightfield of the raw samples splits the quad once, with a centre of `(a+d)/2`, and
 * a character stood up to `|a+d-b-c|/4` above or under the ground it was drawn on. So the samples are
 * upsampled bilinearly to level 0's spacing, by [clipmapColliderFactor]: the collider then has the drawn
 * vertices, and Jolt's diagonal is the core mesh's. A host that draws and one that doesn't, a game
 * server, build the same collider, since both read the terrain's own clipmap config.
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
            val factor = heightmap.clipmapColliderFactor(terrain.clipmapConfig)
            val scale = heightmap.scale
            val shape = HeightFieldShape(
                heights = heightmap.upsampledSamples(factor),
                sampleCount = (heightmap.width - 1) * factor + 1,
                scale = Vec3f(scale.x / factor, scale.y, scale.z / factor),
                origin = heightmap.origin,
            )
            world.add(entity, PhysicsBody(shape = shape, motionType = MotionType.STATIC))
        }
    }
}
