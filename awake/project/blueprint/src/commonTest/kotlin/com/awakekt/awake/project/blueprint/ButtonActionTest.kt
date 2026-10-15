/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.blueprint

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.project.runtime.SceneSystemSet
import com.awakekt.awake.project.runtime.loadProject
import com.awakekt.awake.project.runtime.sceneSystems
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.blueprint.BlueprintComponent
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.input.inputActions
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.ui.canvas.CanvasElement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * One press of a Button reaches both its `action`, which the project runtime presses as an input
 * action, and a blueprint's On Button Pressed, whichever of the two systems runs first.
 */
class ButtonActionTest {
    private val input = Input()
    private val gameplay = { GameplayInput(input.currentSnapshot, InputOwnership()) }
    private val host = ComposeHost()

    @Test
    fun aPressTriggersTheButtonsActionAndTheBlueprint() = runTest {
        val project = loadProject(files(), capabilities = listOf(BlueprintCapability))
        // Drawn, as a game is: a headless host skips Set Text, which only changes what is shown.
        val systems = project.sceneSystems(gameplay, renderer = NoopRenderer())
        val world = World().also { SceneLoader.instantiate(project.scene, it) }
        val actions = assertNotNull(world.inputActions())
        systems.runFixed(world)
        systems.runFrame(world)

        tap(world)
        systems.runFixed(world)
        systems.runFrame(world)

        assertTrue(actions.wasPressed("fire"), "the Button's action was pressed")
        assertEquals("Fired", world.element("Status").text, "the blueprint heard the press")
        assertEquals(1f, world.presses(), "once")
        systems.runFixed(world)
        systems.runFrame(world)
        assertFalse(actions.wasPressed("fire"), "a frame with no tap presses nothing")
        assertEquals(1f, world.presses(), "a step with no tap fires nothing")

        tap(world)
        systems.runFrame(world)
        assertTrue(actions.wasPressed("fire"), "the action reads the press before the blueprint runs")
        systems.runFixed(world)
        assertEquals(2f, world.presses(), "and the blueprint still hears it")
        systems.close()
    }

    private fun SceneSystemSet.runFixed(world: World) = fixed.forEach { it.update(world, STEP) }

    private fun SceneSystemSet.runFrame(world: World) = frame.forEach { it.update(world, STEP) }

    /** Taps the Fire button through the scene's own canvas. */
    private fun tap(world: World) {
        val fire = world.element("Fire")
        val x = (fire.offsetX + fire.width / 2).toInt()
        val y = (fire.offsetY + fire.height / 2).toInt()
        for ((down, pressed, released) in listOf(Triple(false, false, false), Triple(true, true, false), Triple(false, false, true))) {
            host.frame(FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
                SceneCanvas(world)
            }
        }
    }

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return checkNotNull(found) { "no $name in the scene" }
    }

    private fun World.element(name: String): CanvasElement = assertNotNull(get<CanvasElement>(named(name)))

    private fun World.presses(): Any? = get<BlueprintComponent>(named("Logic"))?.instance?.variable("presses")

    private fun files() = AssetSource { path ->
        runCatching { mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to SCENE, GRAPH to MENU_GRAPH).getValue(path.value).encodeToByteArray() }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val WIDTH = 400
        const val HEIGHT = 300
        const val GRAPH = "logic/menu.graph.json"
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Controls", "components": [ { "component": "input_actions", "actions": [ { "type": "button", "name": "fire", "keys": ["F"] } ] } ] },
  { "name": "Fire", "components": [ { "component": "canvas_element", "kind": "Button", "offsetX": 10.0, "offsetY": 10.0,
    "width": 80.0, "height": 30.0, "action": "fire", "text": "Fire" } ] },
  { "name": "Status", "components": [ { "component": "canvas_element", "kind": "Text", "offsetX": 10.0, "offsetY": 60.0, "text": "Ready" } ] },
  { "name": "Logic", "components": [ { "component": "blueprint", "graph": "logic/menu.graph.json" } ] }
] }
"""

        /** Pressing Fire counts the press and shows Fired. */
        const val MENU_GRAPH = """
{ "kind": "awake.logic.event-graph",
  "nodes": [
    { "id": "pressed", "type": "event.button.pressed", "config": { "name": "Fire" } },
    { "id": "n", "type": "var.get.float", "config": { "name": "presses" } },
    { "id": "add", "type": "math.add", "config": { "b": 1.0 } },
    { "id": "count", "type": "var.set.float", "config": { "name": "presses" } },
    { "id": "status", "type": "ui.set-text", "config": { "name": "Status", "text": "Fired" } }
  ],
  "edges": [
    { "fromNode": "pressed", "fromPort": "then", "toNode": "count", "toPort": "exec" },
    { "fromNode": "n", "fromPort": "value", "toNode": "add", "toPort": "a" },
    { "fromNode": "add", "fromPort": "sum", "toNode": "count", "toPort": "value" },
    { "fromNode": "count", "fromPort": "then", "toNode": "status", "toPort": "exec" }
  ] }
"""
    }
}
