/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.EntityNames
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.ui.canvas.CanvasButtonWidget
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasGradient
import com.awakekt.awake.ui.canvas.CanvasPointerState
import com.awakekt.awake.ui.canvas.CanvasRect
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.CanvasText
import com.awakekt.awake.ui.canvas.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A canvas element in a scene is its components: a file saved before the split reads as them, a
 * world saves them, the element drawn is made of the same instances, and what the player does to it
 * is a component of its own that is never saved.
 */
class CanvasComponentsSceneTest {
    @Test
    fun middleColoursSurviveTheLegacyAndFocusedSavedForms() {
        val style = CanvasStyle(gradient = CanvasGradient("#FF0000", "#0000FF", middle = "#00FF0080"))
        val forms = listOf(
            listOf(SceneCanvasElement(kind = CanvasElementKind.Panel, style = style)),
            listOf(SceneCanvasRect(), SceneCanvasAppearance(style = style)),
        )
        for (components in forms) {
            val saved = loadCanvas(listOf(SceneNode(name = "Gradient", components = components))).saveCanvas().single()
            val json = CanvasPrefabs.json.encodeToString(SceneNode.serializer(), saved)
            val decoded = CanvasPrefabs.json.decodeFromString(SceneNode.serializer(), json)
            assertEquals(style, loadCanvas(listOf(decoded)).canvasElements().getValue("Gradient").style)
            assertTrue("\"middle\"" in json, json)
        }
        val old = SceneNode(components = listOf(SceneCanvasAppearance(style = CanvasStyle(gradient = CanvasGradient("#FF0000", "#0000FF")))))
        assertFalse("\"middle\"" in CanvasPrefabs.json.encodeToString(SceneNode.serializer(), old), "old two-colour files gain no field")
    }

    @Test
    fun aPrefabWrittenBeforeTheSplitReadsAsItsComponentsAndSavesThem() {
        val older = CanvasPrefabs.fromJson(OLDER_PREFAB)
        val saved = loadCanvas(listOf(older.root)).saveCanvas().single()

        assertEquals(older.root.withCanvasComponents(), saved)
        assertEquals(
            listOf(SceneCanvasAppearance::class, SceneCanvasButton::class, SceneCanvasRect::class, SceneCanvasText::class),
            saved.children.single().components.map { it::class },
        )
        val current = older.copy(root = saved)
        assertEquals(current, CanvasPrefabs.fromJson(CanvasPrefabs.toJson(current)), "the current form reads back from its own JSON")
        assertEquals<SceneCanvasElement?>(older.root.children.single().components.single() as SceneCanvasElement, saved.children.single().canvasElementView())
    }

    @Test
    fun eachKindReadsAsItsWidget() {
        val widgets = CanvasElementKind.entries.associateWith { kind ->
            SceneCanvasElement(kind = kind).toCanvasComponents().filter { it.isCanvasWidget() }.map { it::class.simpleName }
        }

        assertEquals(emptyList(), widgets.getValue(CanvasElementKind.Text))
        assertEquals(emptyList(), widgets.getValue(CanvasElementKind.Panel))
        assertEquals(listOf("SceneCanvasStepper"), widgets.getValue(CanvasElementKind.Stepper))
        assertEquals(CanvasElementKind.entries.size - 2, widgets.values.count { it.size == 1 })
        for (kind in CanvasElementKind.entries) {
            val world = loadCanvas(listOf(SceneNode(name = "e", components = listOf(SceneCanvasElement(kind = kind)))))
            assertEquals(kind, world.canvasElements().getValue("e").kind, "a $kind loads as a $kind")
        }
    }

    @Test
    fun whatThePlayerDidIsNeverSaved() {
        val world = loadCanvas(listOf(SceneNode(name = "Root", components = SceneCanvasElement(kind = CanvasElementKind.Panel).toCanvasComponents(), children = CONTROLS)))
        val before = world.saveCanvas()
        val button = world.named("Use")
        val elements = world.canvasElements()

        tap(world, button)
        elements.getValue("Use").cooldown = 0.5f
        elements.getValue("Name").focused = true

        assertEquals(1, world.get<CanvasPointerState>(button)?.presses, "the tap reached the button")
        assertEquals(before, world.saveCanvas(), "presses, a cooldown, focus and where it was drawn are not saved")
        val json = CanvasPrefabs.json.encodeToString(SceneNode.serializer(), world.saveCanvas().single())
        for (live in listOf("presses", "focused", "cooldown", "screenBounds", "pointer")) assertFalse("\"$live\"" in json, "$live in $json")
    }

    @Test
    fun aTapIsReadFromThePointerStateComponentOfTheButtonsEntity() {
        val world = loadCanvas(CONTROLS)
        val button = world.named("Use")

        tap(world, button)

        val pointer = assertNotNull(world.get<CanvasPointerState>(button))
        assertSame(world.get<CanvasElement>(button)?.pointer, pointer)
        val pressable = ArrayList<Entity>()
        world.family<CanvasButtonWidget, CanvasPointerState>().forEach { entity, _, _ -> pressable += entity }
        assertEquals(listOf(button), pressable, "a query by component finds the one button")
        assertTrue(pointer.consumePress())
        assertFalse(pointer.consumePress())
    }

    @Test
    fun aComponentChangedOnTheEntityReachesItsElementAndBack() {
        val world = loadCanvas(CONTROLS)
        val entity = world.named("Name")
        val element = assertNotNull(world.get<CanvasElement>(entity))

        world.add(entity, CanvasText().apply { text = "Grace" })
        world.syncCanvasElements()
        assertEquals("Grace", element.text)

        element.set(CanvasRect().apply { width = 64f })
        world.syncCanvasElements()
        assertSame(element.rect, world.get<CanvasRect>(entity))
        assertEquals(64f, world.get<CanvasRect>(entity)?.width)
    }

    @Test
    fun anElementAddedWholeGetsItsComponentsAndOneWhoseRectIsRemovedIsNoMore() {
        val world = World()
        val added = world.create().also { world.add(it, CanvasElement().apply { kind = CanvasElementKind.Button; text = "Go" }) }

        world.syncCanvasElements()
        assertEquals("Go", world.get<CanvasText>(added)?.text)
        assertTrue(world.has<CanvasButtonWidget>(added))

        world.remove<CanvasRect>(added)
        world.syncCanvasElements()
        assertNull(world.get<CanvasElement>(added))
        assertNull(world.get<CanvasButtonWidget>(added))
    }

    @Test
    fun anEntityGivenOnlyComponentsBecomesAnElement() {
        val world = World()
        val entity = world.create()
        world.add(entity, CanvasRect())
        world.add(entity, CanvasButtonWidget().apply { action = "jump" })

        world.syncCanvasElements()

        val element = assertNotNull(world.get<CanvasElement>(entity))
        assertEquals(CanvasElementKind.Button, element.kind)
        assertEquals("jump", element.get<CanvasButtonWidget>()?.action)
    }

    private fun World.named(name: String): Entity = assertNotNull(EntityNames(this).find(name), "no $name")

    private fun tap(world: World, entity: Entity) {
        val host = ComposeHost()
        fun frame(pressed: Boolean = false, down: Boolean = false, released: Boolean = false, x: Int = 0, y: Int = 0): FrameOutput =
            host.frame(FrameInput(400, 300, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) { SceneCanvas(world) }
        val box = assertNotNull(frame().semantics.tagged("canvas-element-${entity.id}"))
        val x = box.x + box.width / 2
        val y = box.y + box.height / 2
        frame(x = x, y = y)
        frame(pressed = true, down = true, x = x, y = y)
        frame(released = true, x = x, y = y)
    }

    internal companion object {
        val CONTROLS = listOf(
            SceneNode(name = "Use", components = listOf(SceneCanvasElement(kind = CanvasElementKind.Button, offsetX = 10f, offsetY = 10f, width = 80f, height = 30f, text = "Use", action = "use"))),
            SceneNode(name = "Name", components = listOf(SceneCanvasElement(kind = CanvasElementKind.TextField, offsetX = 10f, offsetY = 60f, text = "Ada"))),
            SceneNode(name = "Count", components = listOf(SceneCanvasElement(kind = CanvasElementKind.Stepper, offsetX = 10f, offsetY = 110f, stepper = CanvasStepper(value = 2f)))),
        )

        /** A prefab as the builder wrote it before the split. */
        const val OLDER_PREFAB = """{
  "guid": "c0ffee00-0000-4000-8000-0000000000aa",
  "name": "Bar",
  "root": {
    "name": "Bar",
    "components": [ { "component": "canvas_element", "kind": "Panel", "width": 120.0, "height": 40.0, "background": "#202020FF", "elementId": "bar" } ],
    "children": [
      { "name": "bar.slot-0", "components": [ { "component": "canvas_element", "kind": "Button", "width": 32.0, "height": 32.0,
        "text": "Q", "color": "#FFD700", "action": "skill.1", "elementId": "bar.slot-0" } ] }
    ]
  }
}"""
    }
}

private fun List<SemanticsNode>.tagged(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.tagged(tag) }
