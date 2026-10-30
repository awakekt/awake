/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.dp
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.SceneTag
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.scene.document.SceneSerializers
import com.awakekt.awake.scene.document.SceneValidationException
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasBar
import com.awakekt.awake.ui.canvas.CanvasBarDirection
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasButton
import com.awakekt.awake.ui.canvas.CanvasDirection
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasFillImageMode
import com.awakekt.awake.ui.canvas.CanvasImage
import com.awakekt.awake.ui.canvas.CanvasJoystick
import com.awakekt.awake.ui.canvas.CanvasJoystickMode
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasMask
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.CanvasSweep
import com.awakekt.awake.ui.canvas.CanvasView
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A prefab of canvas elements built in code is the prefab an editor saves: its JSON reads back to the
 * same prefab and writes out the same text, and code finds its elements by their stable ids.
 */
class CanvasPrefabBuilderTest {
    @Test
    fun theViewBuilderSavesFocusedSettingsAndRejectsThemOnOtherWidgets() {
        val settings = CanvasView(transparent = true, mask = CanvasMask.Circle)
        val prefab = canvasPrefab(guid = "g", name = "Portrait") {
            view("Camera") { camera = "Portrait Camera"; view = settings }
        }
        val read = CanvasPrefabs.fromJson(CanvasPrefabs.toJson(prefab))
        assertEquals(settings, read.root.children.single().components.filterIsInstance<SceneCanvasView>().single().settings)
        assertFailsWith<SceneValidationException> {
            canvasPrefab(guid = "g", name = "Wrong") { panel("Panel") { view = settings } }
        }
    }

    private fun unitFrame(): ScenePrefab = canvasPrefab(guid = "c0ffee00-0000-4000-8000-000000000001", name = "Unit Frame", id = "frame") {
        anchor = CanvasAnchor.TopLeft
        offset(16.dp, 12.dp)
        size(240.dp, 72.dp)
        background = Color(0f, 0f, 0f, 0.5f)
        layout = CanvasLayout(direction = CanvasDirection.Column, gap = 4f, padding = 8f)
        style = CanvasStyle(image = CanvasImage("ui/frame.png", sliceLeft = 6, sliceTop = 6, sliceRight = 6, sliceBottom = 6, pixelsPerDp = 2f, hollow = true))
        text("Name", id = "name") {
            textKey = "hud.unit.name"
            textArgs = mapOf("level" to "1")
            fontSize = 14.sp
            color = Color.fromHex("#F0E6D2")
        }
        bar("Health", id = "health") {
            size(224.dp, 12.dp)
            grow = 1f
            color = Color.fromHex("#C0392B")
            bind = CanvasBinding(node = "Player", value = "health.current", max = "health.max")
            bar = CanvasBar(direction = CanvasBarDirection.LeftToRight, track = "#00000080", segments = 10, label = true)
            style = CanvasStyle(fillImage = CanvasImage("ui/health-fill.png", sliceLeft = 4, sliceRight = 4), fillImageMode = CanvasFillImageMode.Capped)
        }
        panel("Skills", id = "skills") {
            layout = CanvasLayout(direction = CanvasDirection.Row, gap = 4f)
            size(224.dp, 40.dp)
            button("Skill 1", id = "skill-1") {
                size(40.dp, 40.dp)
                action = "skill.1"
                button = CanvasButton(cooldownSeconds = 2f, longPressAction = "skill.1.aim")
                controlId = "skill-1"
                sweep("Cooldown", id = "skill-1-cooldown") {
                    size(40.dp, 40.dp)
                    sweep = CanvasSweep(mask = CanvasMask.Circle, track = "#00000060")
                }
            }
            stepper("Amount", id = "amount") { stepper = CanvasStepper(value = 1f, min = 1f, max = 99f) }
        }
        joystick("Move", id = "move") {
            anchor = CanvasAnchor.BottomLeft
            touchOnly = true
            joystick = CanvasJoystick(mode = CanvasJoystickMode.Floating, deadZone = 0.1f)
        }
    }

    @Test
    fun theJsonReadsBackToTheSamePrefabAndWritesTheSameText() {
        val prefab = unitFrame()
        val json = CanvasPrefabs.toJson(prefab)

        val read = CanvasPrefabs.fromJson(json)

        assertEquals(prefab, read)
        assertEquals(json, CanvasPrefabs.toJson(read), "a second save is the same file")
    }

    @Test
    fun everyKindHelperWritesFocusedComponentsWithoutALegacyRecord() {
        val prefab = canvasPrefab(guid = "g", name = "All kinds") {
            text("Words") { text = "Harbor Town" }
            panel("Box")
            image("Picture")
            view("Camera")
            bar("Bar")
            sweep("Sweep")
            button("Button")
            textField("Field")
            joystick("Stick")
            lookArea("Look")
            stepper("Stepper")
        }
        val nodes = listOf(prefab.root) + prefab.root.children
        assertTrue(nodes.all { node -> node.components.none { it is SceneCanvasElement } })
        assertTrue(nodes.all { node -> node.components.count { it is SceneCanvasRect } == 1 })
        val button = prefab.root.children.single { it.name == "Button" }
        assertTrue(button.components.any { it is SceneCanvasButton })
        assertTrue(button.components.any { it is SceneCanvasText })
        assertEquals(prefab, prefab.withCanvasComponents(), "builder output is already migrated")
        assertEquals(prefab, CanvasPrefabs.fromJson(CanvasPrefabs.toJson(prefab)))
    }

    @Test
    fun thePrefabFormatsOwnReaderReadsItOnceCanvasElementsAreRegistered() {
        for (binding in CanvasSceneComponents.bindings) {
            @Suppress("UNCHECKED_CAST") // Each binding's serializer reads its own schema class.
            SceneSerializers.register(binding.schemaClass as KClass<SceneComponent>, binding.serializer as KSerializer<SceneComponent>)
        }
        val prefab = unitFrame()

        val read = ScenePrefab.fromJson(CanvasPrefabs.toJson(prefab))

        assertEquals(prefab, read)
        assertEquals(prefab.toJson(), read.toJson(), "the default prefab JSON writes it the same too")
    }

    @Test
    fun anElementWritesAsTheLiveElementSavesItBack() {
        fun SceneNode.all(): List<SceneNode> = listOf(this) + children.flatMap { it.all() }
        val elements = unitFrame().root.all().map { node -> checkNotNull(node.canvasElementView()) }

        elements.forEach { element ->
            assertEquals(element, with(CanvasElementBinding) { element.toComponent().toSceneComponent() }, "${element.elementId} loads and saves unchanged")
        }
    }

    @Test
    fun typedSettingsWriteTheFieldsAnEditorWrites() {
        val prefab = unitFrame()
        val root = prefab.root.canvas()
        val name = assertNotNull(prefab.findCanvasNode("name")).canvas()

        assertEquals("Unit Frame", prefab.root.name)
        assertEquals(CanvasElementKind.Panel, root.kind)
        assertEquals(16f, root.offsetX)
        assertEquals(240f, root.width)
        assertEquals("#00000080", root.background, "a colour with alpha keeps it")
        assertEquals("#FFFFFF", root.color, "an opaque colour drops it, as the default does")
        assertEquals("#F0E6D2", name.color)
        assertEquals(14f, name.fontSize)
        assertEquals("hud.unit.name", name.textKey)
        assertEquals(CanvasElementKind.Sweep, assertNotNull(prefab.findCanvasNode("skill-1-cooldown")).canvas().kind)
    }

    @Test
    fun anUnsetElementIsTheFieldsDefaults() {
        val prefab = canvasPrefab(guid = "g", name = "Empty", kind = CanvasElementKind.Text) {}

        assertEquals(SceneCanvasElement(kind = CanvasElementKind.Text), prefab.root.canvas())
    }

    @Test
    fun otherComponentsRideBesideTheElement() {
        val prefab = canvasPrefab(guid = "g", name = "Tagged") { component(SceneTag(setOf("hud"))) }

        assertEquals(listOf(SceneCanvasRect(), SceneTag(setOf("hud"))), prefab.root.components)
        assertFailsWith<IllegalArgumentException> {
            canvasPrefab(guid = "g", name = "Twice") { component(SceneCanvasElement()) }
        }
        assertFailsWith<IllegalArgumentException> {
            canvasPrefab(guid = "g", name = "Twice") { component(SceneCanvasText(text = "set through the scope")) }
        }
    }

    @Test
    fun aPrefabWithAProblemIsNotBuilt() {
        val failure = assertFailsWith<SceneValidationException> {
            canvasPrefab(guid = " ", name = "Broken") {
                text("Label", id = "label")
                text("Other", id = "label")
                panel("Box") { sweep = CanvasSweep() }
                text("Bad id", id = "-nope")
            }
        }
        val messages = failure.issues.map { it.message }

        assertTrue(messages.any { "guid" in it }, "a blank guid: $messages")
        assertTrue(failure.issues.any { it.path == "Broken/Other" && "\"label\" is already used at Broken/Label" in it.message }, "a repeated id: $messages")
        assertTrue(
            failure.issues.any { it.path == "Broken/Box" && it.message == "sweep does nothing on a Panel, so it is not saved" },
            "a field on a kind it does nothing for: $messages",
        )
        assertTrue(messages.any { "elementId \"-nope\"" in it }, "an id that is not one: $messages")
    }

    @Test
    fun twoElementsOfOneNameAreReported() {
        val failure = assertFailsWith<SceneValidationException> {
            canvasPrefab(guid = "g", name = "Twins") {
                text("Label")
                text("Label")
            }
        }

        assertTrue(failure.issues.any { "duplicate node name 'Label'" in it.message }, failure.message)
    }

    @Test
    fun aMissingIdFindsNothing() {
        val prefab = unitFrame()

        assertNull(prefab.findCanvasNode("missing"))
        assertNull(prefab.findCanvasNode(""), "an empty id is no id")
    }

    @Test
    fun codeFindsAnElementByItsIdAfterARenameAndInEachInstance() {
        val world = World()
        val prefab = unitFrame()
        val first = world.place(prefab.root)
        val second = world.place(prefab.root)
        // An editor names a second instance's root apart, and a designer renames a child.
        world.get<Name>(second)!!.value = "Unit Frame 2"
        val health = assertNotNull(world.findCanvasElement(first, "health"))
        world.get<Name>(health)!!.value = "HP"

        assertEquals(health, world.findCanvasElement(first, "health"), "a rename leaves the id")
        val otherHealth = assertNotNull(world.findCanvasElement(second, "health"))
        assertNotEquals(health, otherHealth, "each instance keeps its own")
        assertEquals("Health", world.get<Name>(otherHealth)!!.value)
        assertEquals(first, world.findCanvasElement(first, "frame"), "the root itself is found")
        val cooldown = assertNotNull(world.findCanvasElement(second, "skill-1-cooldown"), "deep in the tree")
        assertEquals(CanvasElementKind.Sweep, world.get<CanvasElement>(cooldown)!!.kind)
        assertNull(world.findCanvasElement(first, "missing"))
        world.destroy(first)
        assertNull(world.findCanvasElement(first, "health"), "a destroyed root has nothing under it")
    }

    private fun SceneNode.canvas(): SceneCanvasElement = checkNotNull(canvasElementView())

    /** Places [node] and everything under it, as a scene loader does for a prefab instance. */
    private fun World.place(node: SceneNode, parent: Entity? = null): Entity {
        val entity = create()
        add(entity, Name(node.name.orEmpty()))
        add(entity, Transform(parent = parent))
        add(entity, node.canvas().toComponent())
        node.children.forEach { place(it, entity) }
        return entity
    }
}
