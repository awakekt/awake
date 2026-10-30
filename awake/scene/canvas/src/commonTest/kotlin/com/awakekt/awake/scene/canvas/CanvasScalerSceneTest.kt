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
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import com.awakekt.awake.scene.canvas.CanvasScalerBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasScalerBinding.toSceneComponent
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneSerializers
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasInsets
import com.awakekt.awake.ui.canvas.CanvasScaleMode
import com.awakekt.awake.ui.canvas.CanvasScaleScope
import com.awakekt.awake.ui.canvas.CanvasScaleSnap
import com.awakekt.awake.ui.canvas.CanvasScaler
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A scene's `canvas_scaler` and an element's `safeArea`, saved and drawn. */
class CanvasScalerSceneTest {

    private val world = World()

    @Test
    fun aSceneCanvasDrawsAtItsScenesScaler() {
        world.add(world.create(), CanvasScaler(referenceWidth = 1600f, referenceHeight = 1200f, match = 0f))
        val entity = world.create().also { world.add(it, CanvasElement().apply { offsetX = 0f; offsetY = 0f; width = 100f; height = 40f }) }

        val out = ComposeHost().frame(FrameInput(800, 600)) { SceneCanvas(world) }

        assertEquals(50, assertNotNull(out.node(entity)).width, "an 800-wide screen is half the 1600 reference")
    }

    @Test
    fun aSceneWithoutAScalerDrawsAtTheHostsScale() {
        val entity = world.create().also { world.add(it, CanvasElement().apply { width = 100f; height = 40f }) }

        assertNull(world.canvasScaler())
        val out = ComposeHost().frame(FrameInput(800, 600)) { SceneCanvas(world, scale = 2f) }

        assertEquals(200, assertNotNull(out.node(entity)).width)
    }

    @Test
    fun aSceneCanvasKeepsItsElementsInTheSafeArea() {
        val entity = world.create().also { world.add(it, CanvasElement().apply { offsetX = 0f; offsetY = 0f; width = 10f; height = 10f }) }

        val out = ComposeHost().frame(FrameInput(800, 600)) { SceneCanvas(world, safeArea = CanvasInsets(left = 44f, top = 20f)) }

        val node = assertNotNull(out.node(entity))
        assertEquals(listOf(44, 20), listOf(node.x, node.y))
    }

    @Test
    fun aScalerRoundTripsThroughItsSceneComponent() {
        val scaler = CanvasScaler(CanvasScaleMode.ConstantPhysicalSize, 1920f, 1080f, 1f, 2f, 96f, 0.5f, 3f, 1.25f, CanvasScaleSnap.HalfStep)

        assertEquals(scaler, scaler.toSceneComponent().toComponent())
    }

    @Test
    fun aSnapRoundTripsThroughSceneJson() {
        val json = Json { encodeDefaults = false }
        val saved = CanvasScaler(snap = CanvasScaleSnap.WholeStep).toSceneComponent()

        val text = json.encodeToString(SceneCanvasScaler.serializer(), saved)

        assertEquals("""{"snap":"WholeStep"}""", text)
        assertEquals(CanvasScaleSnap.WholeStep, json.decodeFromString(SceneCanvasScaler.serializer(), text).toComponent().snap)
        assertEquals(CanvasScaleSnap.None, json.decodeFromString(SceneCanvasScaler.serializer(), "{}").toComponent().snap, "a scene saved before snap")
    }

    @Test
    fun aSceneCanvasDrawsAtItsScenesSnappedScale() {
        // 1808 by 1017 against the default reference is 1.4125; a half step draws the 100 unit box at 150.
        world.add(world.create(), SceneCanvasScaler(snap = CanvasScaleSnap.HalfStep).toComponent())
        val entity = world.create().also { world.add(it, CanvasElement().apply { offsetX = 0f; offsetY = 0f; width = 100f; height = 40f }) }

        val out = ComposeHost().frame(FrameInput(1808, 1017)) { SceneCanvas(world) }

        assertEquals(150, assertNotNull(out.node(entity)).width)
    }

    @Test
    fun aScalerOutOfRangeIsReported() {
        assertTrue(SceneCanvasScaler().validate("node").isEmpty())

        val issues = SceneCanvasScaler(match = 2f, playerScale = 0f).validate("node")

        assertEquals(2, issues.size, issues.toString())
        assertTrue(issues.all { it.message.startsWith("canvas_scaler.") })
    }

    @Test
    fun anElementSavedBeforeSafeAreaKeepsInsideIt() {
        val json = Json { ignoreUnknownKeys = true }

        val old = json.decodeFromString(SceneCanvasElement.serializer(), """{"kind":"Text","text":"HP"}""")

        assertTrue(old.safeArea)
        assertTrue(old.toComponent().safeArea)
    }

    @Test
    fun anElementsSafeAreaOptOutRoundTrips() {
        val element = CanvasElement().apply { safeArea = false }

        assertFalse(element.toSceneComponent().toComponent().safeArea)
    }

    @Test
    fun aSubtreeScalerScalesOnlyItsNodeAndDescendantsWhileLeavingSiblingWindowsOneToOne() {
        val subtreeScaler = CanvasScaler(
            referenceWidth = 640f,
            referenceHeight = 360f,
            match = 1f,
            scope = CanvasScaleScope.Subtree,
        )
        val hud = world.create().also {
            world.add(it, Transform())
            world.add(it, subtreeScaler)
            world.add(it, CanvasElement().apply { kind = CanvasElementKind.Panel; offsetX = 10f; offsetY = 10f; width = 100f; height = 40f })
        }
        val hudButton = world.create().also {
            world.add(it, Transform(parent = hud))
            world.add(it, CanvasElement().apply { kind = CanvasElementKind.Button; offsetX = 10f; offsetY = 8f; width = 80f; height = 24f })
        }
        val window = world.create().also {
            world.add(it, Transform())
            world.add(
                it,
                CanvasElement().apply {
                    kind = CanvasElementKind.Panel; anchor = CanvasAnchor.TopRight
                    offsetX = 10f; offsetY = 10f; width = 120f; height = 60f
                },
            )
        }
        val windowButton = world.create().also {
            world.add(it, Transform(parent = window))
            world.add(it, CanvasElement().apply { kind = CanvasElementKind.Button; offsetX = 10f; offsetY = 10f; width = 60f; height = 20f })
        }

        // Subtree scalers are ignored by whole-canvas lookup.
        assertNull(world.canvasScaler())

        val host = ComposeHost()
        fun frame(x: Int = 0, y: Int = 0, pressed: Boolean = false, down: Boolean = false, released: Boolean = false): FrameOutput =
            host.frame(FrameInput(960, 540, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
                SceneCanvas(world)
            }
        fun tap(x: Int, y: Int) {
            frame(x, y)
            frame(x, y, pressed = true, down = true)
            frame(x, y, released = true)
        }

        val out = frame()
        val hudNode = assertNotNull(out.node(hud))
        val hudButtonNode = assertNotNull(out.node(hudButton))
        val windowNode = assertNotNull(out.node(window))
        val windowButtonNode = assertNotNull(out.node(windowButton))

        assertEquals(listOf(15, 15, 150, 60), listOf(hudNode.x, hudNode.y, hudNode.width, hudNode.height))
        assertEquals(listOf(30, 27, 120, 36), listOf(hudButtonNode.x, hudButtonNode.y, hudButtonNode.width, hudButtonNode.height))
        assertEquals(listOf(830, 10, 120, 60), listOf(windowNode.x, windowNode.y, windowNode.width, windowNode.height))
        assertEquals(listOf(840, 20, 60, 20), listOf(windowButtonNode.x, windowButtonNode.y, windowButtonNode.width, windowButtonNode.height))

        assertEquals(hudButton, world.canvasElementAt(135f, 45f))
        assertEquals(windowButton, world.canvasElementAt(870f, 30f))

        tap(135, 45)
        assertTrue(world.get<CanvasElement>(hudButton)!!.consumePress())
        assertFalse(world.get<CanvasElement>(windowButton)!!.consumePress())

        tap(870, 30)
        assertTrue(world.get<CanvasElement>(windowButton)!!.consumePress())
        assertFalse(world.get<CanvasElement>(hudButton)!!.consumePress())
    }

    @Test
    fun aSubtreeScalerOnAGroupingNodeScalesEveryElementBeneathIt() {
        val group = world.create().also {
            world.add(it, Transform())
            world.add(it, CanvasScaler(referenceWidth = 640f, referenceHeight = 360f, match = 1f, scope = CanvasScaleScope.Subtree))
        }
        val bar = world.create().also {
            world.add(it, Transform(parent = group))
            world.add(it, CanvasElement().apply { offsetX = 10f; offsetY = 10f; width = 80f; height = 20f })
        }
        val sibling = world.create().also {
            world.add(it, Transform())
            world.add(it, CanvasElement().apply { anchor = CanvasAnchor.TopRight; offsetX = 10f; offsetY = 10f; width = 80f; height = 20f })
        }

        val out = ComposeHost().frame(FrameInput(960, 540)) { SceneCanvas(world) }

        assertEquals(120, assertNotNull(out.node(bar)).width, "under the grouping node's 1.5x subtree scaler")
        assertEquals(80, assertNotNull(out.node(sibling)).width, "sibling outside the group stays 1:1")
    }

    @Test
    fun wholeCanvasAndSubtreeScalersCoexistAndValidateInASceneDocument() {
        val doc = SceneDocument(
            name = "MixedScaleHud",
            nodes = listOf(
                SceneNode(
                    name = "RootScaler",
                    components = listOf(SceneCanvasScaler(referenceWidth = 480f, referenceHeight = 270f, match = 1f)),
                ),
                SceneNode(
                    name = "HudBar",
                    components = listOf(SceneCanvasRect(width = 100f, height = 20f)),
                ),
                SceneNode(
                    name = "PixelWindow",
                    components = listOf(
                        SceneCanvasScaler(
                            mode = CanvasScaleMode.ConstantPixelSize,
                            pixelsPerUnit = 1f,
                            snap = CanvasScaleSnap.WholeStep,
                            scope = CanvasScaleScope.Subtree,
                        ),
                        SceneCanvasRect(anchor = CanvasAnchor.TopRight, width = 100f, height = 40f),
                    ),
                ),
            ),
        )
        assertEquals(emptyList(), SceneValidator.validate(doc))

        val sceneJson = SceneSerializers.createJson()
        val roundTripped = sceneJson.decodeFromString(SceneDocument.serializer(), sceneJson.encodeToString(SceneDocument.serializer(), doc))
        val loaded = loadCanvas(roundTripped.nodes)

        assertEquals(CanvasScaleScope.Canvas, assertNotNull(loaded.canvasScaler()).scope)
        val out = ComposeHost().frame(FrameInput(960, 540)) { SceneCanvas(loaded) }
        val widths = loaded.canvasDrawOrder().map { assertNotNull(out.node(it)).width }
        assertEquals(listOf(200, 100), widths, "HudBar draws at 2x while PixelWindow stays 1:1")
    }
}

private fun FrameOutput.node(entity: Entity): SemanticsNode? = semantics.findTag("canvas-element-${entity.id}")

private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }
