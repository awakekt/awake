/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeGraphJson
import com.awakekt.awake.scene.canvas.CanvasElement
import com.awakekt.awake.scene.canvas.CanvasElementKind
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.scene.core.Name
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A menu made with blueprints: a Button's press changes a Text and hides a Panel, through a real canvas. */
class CanvasNodesTest {
    private val world = World()
    private val graphs = HashMap<String, NodeGraph>()
    private val system = BlueprintSystem(graphs = { graphs.getValue(it) })
    private val host = ComposeHost()

    private val window = element("Window", CanvasElementKind.Panel, x = 200f, y = 10f)
    private val title = element("Title", CanvasElementKind.Text, x = 200f, y = 150f) { text = "Main Menu" }
    private val play = element("Play", CanvasElementKind.Button, x = 10f, y = 10f) { text = "Play" }
    private val quit = element("Quit", CanvasElementKind.Button, x = 10f, y = 60f) { text = "Quit" }

    private fun element(name: String, kind: CanvasElementKind, x: Float, y: Float, configure: CanvasElement.() -> Unit = {}): Entity =
        world.create().also { entity ->
            world.add(entity, Name(name))
            world.add(
                entity,
                CanvasElement().apply {
                    this.kind = kind
                    offsetX = x
                    offsetY = y
                    width = ELEMENT_WIDTH
                    height = ELEMENT_HEIGHT
                    configure()
                },
            )
        }

    private fun blueprint(path: String, on: Entity = world.create()): Entity = on.also { world.add(it, BlueprintComponent(path)) }

    private fun canvas(entity: Entity): CanvasElement = assertNotNull(world.get<CanvasElement>(entity))

    private fun step(times: Int = 1) = repeat(times) { system.update(world, STEP) }

    private fun frame(x: Int, y: Int, pressed: Boolean = false, released: Boolean = false) {
        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, pointerDown = pressed, pointerPressed = pressed, pointerReleased = released)) {
            SceneCanvas(world)
        }
    }

    /** Taps the middle of [entity]'s element as a mouse does: the pointer arrives, goes down and comes up. */
    private fun tap(entity: Entity) {
        val element = canvas(entity)
        val x = (element.offsetX + element.width / 2).toInt()
        val y = (element.offsetY + element.height / 2).toInt()
        frame(x, y)
        frame(x, y, pressed = true)
        frame(x, y, released = true)
    }

    @Test
    fun pressingAButtonSetsATextsTextAndHidesAPanel() {
        graphs["menu"] = NodeGraphJson.decode(MENU_GRAPH)
        blueprint("menu")
        step()

        tap(quit)
        step()
        assertEquals("Main Menu", canvas(title).text, "a press of another Button fires nothing")
        assertTrue(canvas(window).visible)

        tap(play)
        step()
        assertEquals("Loading", canvas(title).text)
        assertFalse(canvas(window).visible)
        assertTrue(canvas(play).consumePress(), "the blueprint took no press from the Button's other readers")
    }

    @Test
    fun aBlueprintOnTheButtonItselfHearsOnlyItsOwnPresses() {
        graphs["self"] = graph {
            node("pressed", CanvasNodes.OnButtonPressed.TYPE)
            node("label", "ui.set-text", "text" to "Done")
            wire("pressed.then", "label.exec")
        }
        blueprint("self", on = play)
        step()

        tap(quit)
        step()
        assertEquals("Play", canvas(play).text, "another Button's press")

        tap(play)
        step()
        assertEquals("Done", canvas(play).text, "no name and no target is the blueprint's own element")
    }

    @Test
    fun eachPressFiresOnceHoweverManyStepsRun() {
        graphs["count"] = graph {
            node("pressed", CanvasNodes.OnButtonPressed.TYPE, "name" to "Play")
            node("n", "var.get.float", "name" to "presses")
            node("add", "math.add", "b" to 1f)
            node("set", "var.set.float", "name" to "presses")
            wire("pressed.then", "set.exec")
            wire("n.value", "add.a")
            wire("add.sum", "set.value")
        }
        val counter = blueprint("count")
        step()

        tap(play)
        step(3)
        assertEquals(1f, variable(counter, "presses"))

        tap(play)
        tap(play)
        step()
        assertEquals(3f, variable(counter, "presses"), "two presses between steps fire twice")
    }

    @Test
    fun thePressedElementAndAFoundEntityNameTheElementsToChange() {
        graphs["wired"] = graph {
            node("pressed", CanvasNodes.OnButtonPressed.TYPE, "name" to "Play")
            node("label", "ui.set-text", "text" to "Clicked")
            node("find", "entity.find-by-name", "name" to "Window")
            node("hide", "ui.set-visible", "visible" to false)
            wire("pressed.then", "label.exec")
            wire("label.then", "hide.exec")
            wire("pressed.element", "label.target")
            wire("find.entity", "hide.target")
        }
        blueprint("wired")
        step()

        tap(play)
        step()

        assertEquals("Clicked", canvas(play).text)
        assertFalse(canvas(window).visible)
    }

    @Test
    fun setVisibleShowsAHiddenElementAgainAndSkipsOneItCannotFind() {
        canvas(window).visible = false
        graphs["show"] = graph {
            node("pressed", CanvasNodes.OnButtonPressed.TYPE, "name" to "Play")
            node("missing", "ui.set-visible", "name" to "Nowhere", "visible" to true)
            node("show", "ui.set-visible", "name" to "Window", "visible" to true)
            node("text", "ui.set-text", "name" to "Nowhere", "text" to "lost")
            wire("pressed.then", "missing.exec")
            wire("missing.then", "show.exec")
            wire("show.then", "text.exec")
        }
        blueprint("show")
        step()

        tap(play)
        step()

        assertTrue(canvas(window).visible)
        assertEquals("Main Menu", canvas(title).text)
    }

    @Test
    fun setTextWithAFindByNameThatFindsNoOneChangesNoTextAndWarnsOnce() {
        graphs["typo"] = everyStep("ui.set-text", find = "Titel", "text" to "lost")
        blueprint("typo", on = play)

        val warnings = blueprintWarnings { assertEquals(RUNS, runs(system, world, play, "act", RUNS), "Set Text ran every step") }

        assertEquals("Play", canvas(play).text, "a wire that found no one is not the blueprint's own element")
        assertEquals(1, warnings.size, "one warning, not one a step: $warnings")
        assertContains(warnings.single(), "'act' (Set Text)")
    }

    @Test
    fun setVisibleWithAFindByNameThatFindsNoOneHidesNothingAndWarnsOnce() {
        graphs["typo"] = everyStep("ui.set-visible", find = "Windw", "visible" to false)
        blueprint("typo", on = play)

        val warnings = blueprintWarnings { assertEquals(RUNS, runs(system, world, play, "act", RUNS), "Set Visible ran every step") }

        assertTrue(canvas(play).visible, "a wire that found no one is not the blueprint's own element")
        assertEquals(1, warnings.size, "one warning, not one a step: $warnings")
        assertContains(warnings.single(), "'act' (Set Visible)")
    }

    @Test
    fun aWiredTargetThatFindsNoOneIsNotTheElementNamedOnTheNode() {
        graphs["typo"] = everyStep("ui.set-visible", find = "Windw", "name" to "Window", "visible" to false)
        blueprint("typo")

        val warnings = blueprintWarnings { step() }

        assertTrue(canvas(window).visible)
        assertEquals(1, warnings.size)
    }

    @Test
    fun setTextAndSetVisibleWithNothingWiredChangeTheBlueprintsOwnElement() {
        graphs["self"] = graph {
            node("start", "event.start")
            node("label", "ui.set-text", "text" to "Done")
            node("hide", "ui.set-visible", "visible" to false)
            wire("start.then", "label.exec")
            wire("label.then", "hide.exec")
        }
        blueprint("self", on = play)

        val warnings = blueprintWarnings { step() }

        assertEquals("Done", canvas(play).text)
        assertFalse(canvas(play).visible)
        assertEquals(emptyList(), warnings)
    }

    @Test
    fun aServerSkipsTheNodesThatOnlyChangeWhatIsShown() {
        val server = BlueprintSystem(graphs = { NodeGraphJson.decode(MENU_GRAPH) }, runPresentation = false)
        blueprint("menu")
        server.update(world, STEP)

        tap(play)
        server.update(world, STEP)

        assertEquals("Main Menu", canvas(title).text)
        assertTrue(canvas(window).visible)
    }

    @Test
    fun eachCanvasNodeSavesAndLoadsWithWhatIsSetOnIt() {
        val settable = mapOf(
            CanvasNodes.OnButtonPressed.TYPE to listOf("name"),
            "ui.set-text" to listOf("target", "name", "text"),
            "ui.set-visible" to listOf("target", "name", "visible"),
        )
        for ((type, fields) in settable) {
            assertEquals(fields, assertNotNull(system.nodes.registry[type], "$type is registered").config.map { it.name }, "$type's settable fields")
        }

        val saved = NodeGraphJson.decode(MENU_GRAPH)
        val reloaded = NodeGraphJson.decode(NodeGraphJson.encode(saved))

        assertEquals(saved, reloaded)
        assertEquals(emptyList(), system.nodes.registry.validate(reloaded))
        assertEquals(
            listOf("Play", "Title", "Loading", "Window", "false"),
            reloaded.nodes.flatMap { node -> node.config.values.map { it.toString().trim('"') } },
            "every setting kept its value",
        )
    }

    private fun variable(entity: Entity, name: String): Any? = world.get<BlueprintComponent>(entity)?.instance?.variable(name)

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300
        const val ELEMENT_WIDTH = 80f
        const val ELEMENT_HEIGHT = 30f
        const val RUNS = 4

        /** What Studio saves: pressing Play shows Loading and hides the window. */
        val MENU_GRAPH = """
            {
              "kind": "awake.logic.event-graph",
              "nodes": [
                { "id": "pressed", "type": "event.button.pressed", "config": { "name": "Play" } },
                { "id": "title", "type": "ui.set-text", "config": { "name": "Title", "text": "Loading" } },
                { "id": "hide", "type": "ui.set-visible", "config": { "name": "Window", "visible": false } }
              ],
              "edges": [
                { "fromNode": "pressed", "fromPort": "then", "toNode": "title", "toPort": "exec" },
                { "fromNode": "title", "fromPort": "then", "toNode": "hide", "toPort": "exec" }
              ]
            }
        """.trimIndent()
    }
}
