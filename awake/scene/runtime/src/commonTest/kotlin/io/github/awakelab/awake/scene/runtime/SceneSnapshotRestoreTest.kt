/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.components.Name
import io.github.awakelab.awake.scene.core.components.Transform
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A snapshot taken before simulation must restore what was authored.
 *
 * The guarantee is what matters, not the mechanism: an editor host runs Play on the same world its systems and
 * renderer are bound to, and Stop rebuilds that world from a document captured beforehand. This
 * covers the capture/restore pair, which is what makes the guarantee true; the host wiring that
 * calls it is one `when` branch either side and belongs to the host's own tests.
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

    /**
     * The snapshot is taken from the *edited* world, not from the file.
     *
     * Editing then playing then stopping has to return to what was edited. Snapshotting the file
     * on disk instead would silently revert the session's work every time Play was pressed.
     */
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
