/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.transform

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/**
 * Updates or creates a [Transform] component on [entity] in a single safe call.
 *
 * @return The updated or created [Transform] instance.
 */
fun World.setTransform(
    entity: Entity,
    position: Vec3f? = null,
    rotation: Vec3f? = null,
    scale: Vec3f? = null,
): Transform {
    val current = get<Transform>(entity)
    return if (current != null) {
        position?.let { current.position.set(it) }
        rotation?.let { current.rotation.set(it) }
        scale?.let { current.scale.set(it) }
        current
    } else {
        val newTransform = Transform(
            position = position ?: Vec3f.ZERO,
            rotation = rotation ?: Vec3f.ZERO,
            scale = scale ?: Vec3f.ONE,
        )
        add(entity, newTransform)
        newTransform
    }
}
