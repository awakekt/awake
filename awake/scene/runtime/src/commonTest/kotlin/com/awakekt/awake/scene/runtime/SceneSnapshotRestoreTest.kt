/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A snapshot taken before simulation must restore what was authored.
 */
class SceneSnapshotRestoreTest {

    @Test
    fun restoringTheSnapshotDiscardsWhateverSimulationDid() {
        val world = World()
        val entity = world.create()
        world.add(entity, Name("cube"))
        val transform = Transform(position = Vec3f(1f, 2f, 3f))
        world.add(entity, transform)

        // Start Play: capture the scene as authored.
        val authored = SceneLoader.fromWorld(world, name = "play")

        // Simulation runs, and moves things. A spin system does exactly this every frame.
        transform.position.set(99f, 99f, 99f)
        assertEquals(99f, transform.position.x)

        // Stop: rebuild from the snapshot into a fresh world, as SceneManager.switchTo does.
        val restored = SceneLoader.instantiate(SceneLoader.decode(SceneLoader.encode(authored))).world

        var found: Vec3f? = null
        restored.queryEach<Name> { e, name ->
            if (name.value == "cube") found = restored.get<Transform>(e)?.position
        }
        assertEquals(1f, found?.x, "Stop must put back the authored position, not the simulated one")
        assertEquals(2f, found?.y)
        assertEquals(3f, found?.z)
    }

    @Test
    fun theSnapshotIncludesEditsMadeBeforePlay() {
        val world = World()
        val entity = world.create()
        world.add(entity, Name("cube"))
        val transform = Transform(position = Vec3f(0f, 0f, 0f))
        world.add(entity, transform)

        transform.position.set(5f, 6f, 7f)
        val authored = SceneLoader.fromWorld(world, name = "play")
        transform.position.set(99f, 99f, 99f)

        val node = authored.nodes.single { it.name == "cube" }
        assertEquals(5f, node.transform.position.x, "the edit made before Play must be in the snapshot")
        assertEquals(6f, node.transform.position.y)
        assertEquals(7f, node.transform.position.z)
    }
}
