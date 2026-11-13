/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.asset.shaderpack.CelestialLook
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.firstOrNull
import com.awakekt.awake.scene.rendering.light.DayCycle

/**
 * Copies what the scene says about the sun and moon into this look, once a frame.
 *
 * With a `day_cycle` the bodies follow its sun path and its visibility windows, not the `light`'s
 * direction: a rotated light node can hold the ground light still while the sky's sun and moon move
 * with the time of day. Without one the pass's light direction places the sun. Allocates nothing.
 */
internal fun CelestialLook.sync(world: World, sky: Skybox?) {
    moonPhase = sky?.moonPhase ?: CelestialLook.FULL_MOON
    val cycle = world.firstOrNull<DayCycle>()
    followsDirection = cycle != null
    if (cycle == null) {
        sunVisibility = 1f
        moonVisibility = 1f
        return
    }
    sunDirection.set(cycle.sunDirection.x, cycle.sunDirection.y, cycle.sunDirection.z)
    sunVisibility = cycle.sunVisibility
    moonVisibility = cycle.moonVisibility
}
