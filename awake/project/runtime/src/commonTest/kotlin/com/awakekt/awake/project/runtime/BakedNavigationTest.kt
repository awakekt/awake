/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.navigation.SceneNavigation
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A scene's navigation grid is baked once, as the project loads, and every system set planned from
 * that content routes over the same grid, so instances of one scene share it instead of each baking
 * its own from the document.
 */
class BakedNavigationTest {
    private val idle = { GameplayInput(Input().currentSnapshot, InputOwnership()) }

    @Test
    fun loadingBakesTheScenesGridIntoItsContent() = runTest {
        val project = loadProject(files(OPEN_FIELD))

        val grid = assertNotNull(project.content[AiNavigation], "the grid is content, baked at load")

        assertEquals(ROWS * ROWS, grid.tile.walkableCount, "every cell of the open field is walkable")
        assertSame(grid, project.content[AiNavigation], "content holds the one grid; nothing bakes it again")
        project.close()
    }

    @Test
    fun theSystemsRouteOverTheGridTheContentHolds() {
        installProjectComponents()
        val scene = SceneLoader.decode(OPEN_FIELD)
        val walled = SceneNavigation(rows = List(ROWS) { "#".repeat(ROWS) }).toGrid()

        val fromContent = walkedBy(scene, SceneContent.build { this[AiNavigation] = walled })
        val fromDocument = walkedBy(scene, SceneContent.Empty)

        assertTrue(fromContent < STILL, "the content's grid is all walls, so the guard has no route; it walked $fromContent")
        assertTrue(fromDocument > WALKED, "the positive control: with no baked grid the document's open field routes it; it walked $fromDocument")
    }

    /** How far the guard walks along z in [FRAMES] frames of [scene]'s systems, planned over [content]. */
    private fun walkedBy(scene: SceneDocument, content: SceneContent): Float {
        val world = World()
        scene.instantiate(world = world)
        val start = world.guardZ()
        sceneSystemsFor(scene, SceneHostServices.headless(idle, content = content)).use { systems ->
            repeat(FRAMES) { systems.frame.forEach { it.update(world, STEP) } }
        }
        return abs(world.guardZ() - start)
    }

    private fun World.guardZ(): Float = buildList {
        queryEach(Name::class) { entity, name -> if (name.value == "Guard") add(get<Transform>(entity)!!.position.z) }
    }.single()

    private fun files(scene: String): AssetSource {
        val sources = mapOf(PROJECT_MANIFEST to MANIFEST, "scenes/main.scene.json" to scene)
        return AssetSource { path -> runCatching { sources.getValue(path.value).encodeToByteArray() } }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val FRAMES = 120
        const val ROWS = 12
        const val STILL = 0.01f
        const val WALKED = 2f
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val OPEN_FIELD = """
{ "version": 1, "name": "field", "nodes": [
  { "name": "Map", "components": [ { "component": "navigation", "cellSize": 1.0, "rows": [
    "............", "............", "............", "............", "............", "............",
    "............", "............", "............", "............", "............", "............" ] } ] },
  { "name": "Guard", "transform": { "position": { "x": 1.0, "y": 0.0, "z": 1.0 } }, "components": [
    { "component": "patrol", "style": "loop", "dwellSeconds": 0.0, "speed": 5.0,
      "stops": [ { "x": 1.0, "y": 0.0, "z": 1.0 }, { "x": 1.0, "y": 0.0, "z": 10.0 } ] } ] }
] }
"""
    }
}
