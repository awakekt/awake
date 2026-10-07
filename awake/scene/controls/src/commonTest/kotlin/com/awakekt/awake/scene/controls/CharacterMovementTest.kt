/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.movement.MatrixRelativeMovementSystem
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.SceneMovementControl
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

class CharacterMovementTest {
    @Test
    fun theMarkerRoundTripsWithItsSpeedOnceControlsAreRegistered() {
        val registry = SceneComponentRegistry().registerControls()
        val json = """{"version":1,"name":"x","nodes":[{"name":"player","components":[{"component":"movement_control","speed":6.0}]}]}"""
        val world = World()

        SceneLoader.decode(json).instantiate(world = world, componentRegistry = registry)

        val loaded = mutableListOf<MovementControl>().also { list -> world.family<MovementControl>().forEach { _, c -> list += c } }
        assertEquals(6f, loaded.single().speed)
        assertEquals(
            SceneMovementControl(speed = 6f),
            SceneLoader.fromWorld(world, name = "x", componentRegistry = registry)
                .nodes.single().components.filterIsInstance<SceneMovementControl>().single(),
        )
    }

    @Test
    fun anEntitysOwnSpeedOverridesTheSystemSpeed() {
        val world = World()
        val player = world.create()
        val transform = Transform()
        world.add(player, transform)
        world.add(
            player,
            MovementControl().apply {
                moveZ = 1f
                speed = 6f
            },
        )

        MatrixRelativeMovementSystem(speed = 2f).update(world, 1f)

        assertEquals(6f, abs(transform.position.z), 1e-4f)
    }
}
