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
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasInsets
import com.awakekt.awake.ui.canvas.CanvasScaleMode
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
}

private fun FrameOutput.node(entity: Entity): SemanticsNode? = semantics.findTag("canvas-element-${entity.id}")

private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }
