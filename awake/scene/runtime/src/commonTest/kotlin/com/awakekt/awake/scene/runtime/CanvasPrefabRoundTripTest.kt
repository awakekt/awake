/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.dp
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.canvas.CanvasPrefabs
import com.awakekt.awake.scene.canvas.canvasPrefab
import com.awakekt.awake.scene.canvas.findCanvasElement
import com.awakekt.awake.scene.core.EntityNames
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.scene.document.ScenePrefabLink
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.scene.document.withPrefabs
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasBar
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasButton
import com.awakekt.awake.ui.canvas.CanvasDirection
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasSweep
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A canvas prefab built in code loads into a world and saves back as the same prefab, as an editor
 * does when it opens the file and saves it, and code finds each placed copy's elements by id.
 */
class CanvasPrefabRoundTripTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    // --8<-- [start:canvas-prefab]
    private val actionBar = canvasPrefab(guid = "c0ffee00-0000-4000-8000-000000000002", name = "Action Bar") {
        anchor = CanvasAnchor.BottomCenter
        size(320.dp, 64.dp)
        background = Color(0.1f, 0.1f, 0.1f, 0.75f)
        layout = CanvasLayout(direction = CanvasDirection.Row, gap = 4f, padding = 4f)
        button("Slot 1", id = "slot-1") {
            size(56.dp, 56.dp)
            action = "slot.1"
            textKey = "hud.slot.1"
            fontSize = 12.sp
            button = CanvasButton(cooldownSeconds = 1.5f)
            sweep("Cooldown", id = "slot-1-cooldown") { sweep = CanvasSweep(track = "#00000060") }
        }
        bar("Energy", id = "energy") {
            size(120.dp, 8.dp)
            bind = CanvasBinding(node = "Player", value = "energy.current", max = "energy.max")
            bar = CanvasBar(ticks = 4)
        }
    }
    // --8<-- [end:canvas-prefab]

    @Test
    fun aBuiltPrefabLoadsAndSavesBackUnchanged() {
        val world = World()
        val document = SceneDocument(nodes = listOf(actionBar.root))
        assertEquals(emptyList(), SceneValidator.validate(document))

        document.instantiate(world = world)
        val saved = ScenePrefab(guid = actionBar.guid, name = actionBar.name, root = SceneLoader.fromWorld(world).nodes.single())

        assertEquals(actionBar, saved)
        assertEquals(CanvasPrefabs.toJson(actionBar), CanvasPrefabs.toJson(saved), "the file an editor saves is the file code wrote")
    }

    @Test
    fun eachPlacedCopyFindsItsOwnElementsById() = runTest {
        val path = "ui/action-bar.prefab.json"
        val scene = SceneDocument(
            nodes = listOf(
                SceneNode(name = "Left Bar", components = listOf(ScenePrefabLink(path))),
                SceneNode(name = "Right Bar", components = listOf(ScenePrefabLink(path))),
            ),
        )
        val world = World()

        scene.withPrefabs(CanvasPrefabs.json) { CanvasPrefabs.toJson(actionBar) }.instantiate(world = world)
        val names = EntityNames(world)
        val left = assertNotNull(names.find("Left Bar"))
        val right = assertNotNull(names.find("Right Bar"))

        // --8<-- [start:find-by-id]
        val leftSlot = assertNotNull(world.findCanvasElement(left, "slot-1"))
        val rightSlot = assertNotNull(world.findCanvasElement(right, "slot-1"))
        // --8<-- [end:find-by-id]
        assertNotEquals(leftSlot, rightSlot, "two copies share the name Slot 1 but not the entity")
        assertTrue(world.get<Name>(leftSlot)?.value == "Slot 1" && world.get<Name>(rightSlot)?.value == "Slot 1")
        world.get<Name>(rightSlot)!!.value = "Renamed"
        assertEquals(rightSlot, world.findCanvasElement(right, "slot-1"), "a rename leaves the id")
    }
}
