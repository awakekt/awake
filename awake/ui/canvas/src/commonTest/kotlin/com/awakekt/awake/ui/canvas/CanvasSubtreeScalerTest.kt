/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Vec2
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [CanvasItem.scaler] and [CanvasScaleScope.Subtree]: scaling only one subtree on a canvas while
 * sibling subtrees keep their own scale, across layout, safe area, followers, hit-testing and drag.
 */
class CanvasSubtreeScalerTest {

    /** 960 by 540 against a 640 by 360 reference gives 1.5. */
    private val onePointFive = CanvasScaler(
        referenceWidth = 640f,
        referenceHeight = 360f,
        match = 1f,
        scope = CanvasScaleScope.Subtree,
    )

    @Test
    fun aSubtreeScalerScalesItsSubtreeAtOnePointFiveWhileASiblingStaysOneToOne() {
        val hud = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            offsetX = 10f; offsetY = 10f; width = 100f; height = 40f
        }
        val hudButton = CanvasElement().apply {
            kind = CanvasElementKind.Button
            offsetX = 10f; offsetY = 8f; width = 80f; height = 24f
        }
        val window = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            anchor = CanvasAnchor.TopRight
            offsetX = 10f; offsetY = 10f; width = 120f; height = 60f
        }
        val windowButton = CanvasElement().apply {
            kind = CanvasElementKind.Button
            offsetX = 10f; offsetY = 10f; width = 60f; height = 20f
        }
        val items = listOf(
            CanvasItem(1, hud, scaler = onePointFive),
            CanvasItem(2, hudButton, parent = hud),
            CanvasItem(3, window),
            CanvasItem(4, windowButton, parent = window),
        )
        val host = ComposeHost()
        fun frame(x: Int = 0, y: Int = 0, pressed: Boolean = false, down: Boolean = false, released: Boolean = false): FrameOutput =
            host.frame(FrameInput(960, 540, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
                CanvasOverlay(items)
            }
        fun tap(x: Int, y: Int) {
            frame(x, y)
            frame(x, y, pressed = true, down = true)
            frame(x, y, released = true)
        }

        val out = frame()

        // HUD subtree is 1.5x: panel (15, 15, 150, 60), child button (15 + 15, 15 + 12, 120, 36) = (30, 27, 120, 36).
        assertEquals(listOf(15, 15, 150, 60), out.boundsOf(1))
        assertEquals(listOf(30, 27, 120, 36), out.boundsOf(2))
        // Sibling window stays 1:1: panel (960 - 10 - 120, 10, 120, 60) = (830, 10, 120, 60), button (840, 20, 60, 20).
        assertEquals(listOf(830, 10, 120, 60), out.boundsOf(3))
        assertEquals(listOf(840, 20, 60, 20), out.boundsOf(4))

        // Picker hits the scaled button in the 1.5x-only region (x = 135 would miss an unscaled 80-wide button ending at 100).
        assertSame(items[1], canvasItemAt(items, 135f, 45f))
        assertSame(items[3], canvasItemAt(items, 870f, 30f))
        assertSame(items[0], canvasItemAt(items, 155f, 45f), "past the button's 1.5x right edge (150), only the panel is under the point")

        // Tap inside the 1.5x-extended part of hudButton: hits hudButton, reports unscaled element-local coords.
        tap(135, 45)
        val local = assertNotNull(hudButton.consumePressAt(), "135, 45 is inside the 1.5x button [30..150, 27..63)")
        assertTrue(abs(local.x - 70f) < 1e-3f && abs(local.y - 12f) < 1e-3f, "local press point in 80x24 space: $local")
        assertFalse(windowButton.consumePress())

        // Negative control: 5 px past the 1.5x button's right edge misses both buttons.
        tap(155, 45)
        assertFalse(hudButton.consumePress())
        assertFalse(windowButton.consumePress())

        // Tap on the 1:1 sibling button lands cleanly.
        tap(870, 30)
        assertTrue(windowButton.consumePress())
        assertFalse(hudButton.consumePress())
    }

    @Test
    fun aSubtreeScalerCanKeepOneBranchOneToOneInsideAScaledCanvas() {
        val wholeCanvas = CanvasScaler(referenceWidth = 480f, referenceHeight = 270f, match = 1f) // 2x in 960x540
        val pixelExact = CanvasScaler(mode = CanvasScaleMode.ConstantPixelSize, pixelsPerUnit = 1f, scope = CanvasScaleScope.Subtree)
        val scaledBar = CanvasElement().apply { offsetX = 10f; offsetY = 10f; width = 100f; height = 20f }
        val crispWindow = CanvasElement().apply {
            anchor = CanvasAnchor.TopRight
            offsetX = 10f; offsetY = 10f; width = 100f; height = 50f
        }
        val items = listOf(
            CanvasItem(1, scaledBar),
            CanvasItem(2, crispWindow, scaler = pixelExact),
        )

        val out = ComposeHost().frame(FrameInput(960, 540)) {
            CanvasOverlay(items, scaler = wholeCanvas)
        }

        assertEquals(listOf(20, 20, 200, 40), out.boundsOf(1), "whole-canvas 2x")
        assertEquals(listOf(850, 10, 100, 50), out.boundsOf(2), "subtree ConstantPixelSize stays 1:1")
    }

    @Test
    fun safeAreaInsetsKeepTheirHostPixelWidthOnASubtreeScaledRoot() {
        val hud = CanvasElement().apply { offsetX = 0f; offsetY = 0f; width = 80f; height = 20f }
        val sibling = CanvasElement().apply { anchor = CanvasAnchor.BottomLeft; offsetX = 0f; offsetY = 0f; width = 80f; height = 20f }
        val items = listOf(
            CanvasItem(1, hud, scaler = onePointFive),
            CanvasItem(2, sibling),
        )

        val out = ComposeHost().frame(FrameInput(960, 540)) {
            CanvasOverlay(items, safeArea = CanvasInsets(left = 30f, top = 18f, bottom = 12f))
        }

        // Safe area is 30 px left, 18 px top regardless of subtree scale; size scales to 120x30.
        assertEquals(listOf(30, 18, 120, 30), out.boundsOf(1))
        assertEquals(listOf(30, 508, 80, 20), out.boundsOf(2))
    }

    @Test
    fun flexLayoutClusterAndFollowersFollowTheirSubtreesScale() {
        val row = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            offsetX = 0f; offsetY = 0f; width = 120f; height = 32f
            layout = CanvasLayout(direction = CanvasDirection.Row, gap = 4f, padding = 6f)
        }
        val slotA = CanvasElement().apply { width = 20f; height = 20f }
        val slotB = CanvasElement().apply { width = 20f; height = 20f }
        val scaledFollower = CanvasElement().apply {
            follow = "A"; anchor = CanvasAnchor.Center; offsetX = 0f; offsetY = 0f; width = 40f; height = 20f
        }
        val unscaledFollower = CanvasElement().apply {
            follow = "B"; anchor = CanvasAnchor.Center; offsetX = 0f; offsetY = 0f; width = 40f; height = 20f
        }
        val resolver = object : CanvasResolver {
            override fun point(target: String, offset: Vec3f): Vec2? = when (target) {
                "A" -> Vec2(300f, 200f)
                "B" -> Vec2(600f, 200f)
                else -> null
            }
        }
        val items = listOf(
            CanvasItem(1, row, scaler = onePointFive),
            CanvasItem(2, slotA, parent = row),
            CanvasItem(3, slotB, parent = row),
            CanvasItem(4, scaledFollower, parent = row),
            CanvasItem(5, unscaledFollower),
        )

        val out = ComposeHost().frame(FrameInput(960, 540)) {
            CanvasOverlay(items, resolver = resolver)
        }

        // Row padding 6 * 1.5 = 9, slotA width 20 * 1.5 = 30, gap 4 * 1.5 = 6 => slotB starts at 9 + 30 + 6 = 45.
        assertEquals(listOf(9, 9, 30, 30), out.boundsOf(2))
        assertEquals(listOf(45, 9, 30, 30), out.boundsOf(3))
        // Scaled follower is 60x30 centred on (300, 200); unscaled follower is 40x20 centred on (600, 200).
        assertEquals(listOf(270, 185, 60, 30), out.boundsOf(4))
        assertEquals(listOf(580, 190, 40, 20), out.boundsOf(5))
    }

    @Test
    fun dragAndDropWorksAcrossScaledAndUnscaledSubtrees() {
        val wholeCanvas = CanvasScaler(referenceWidth = 480f, referenceHeight = 270f, match = 1f) // 2x canvas
        val source = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            offsetX = 10f; offsetY = 10f; width = 40f; height = 40f; draggable = true
        }
        val unscaledTarget = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            anchor = CanvasAnchor.TopRight
            offsetX = 20f; offsetY = 20f; width = 60f; height = 60f; dropTarget = true
        }
        val pixelExact = CanvasScaler(mode = CanvasScaleMode.ConstantPixelSize, pixelsPerUnit = 1f, scope = CanvasScaleScope.Subtree)
        val items = listOf(
            CanvasItem(1, source, scaler = onePointFive), // 1.5x -> (15, 15, 60, 60) px
            CanvasItem(2, unscaledTarget, scaler = pixelExact), // 1x -> (880, 20, 60, 60) px
        )
        val host = ComposeHost()
        fun frame(x: Int, y: Int, pressed: Boolean = false, down: Boolean = false, released: Boolean = false) =
            host.frame(FrameInput(960, 540, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
                CanvasOverlay(items, scaler = wholeCanvas)
            }

        frame(40, 40)
        frame(40, 40, pressed = true, down = true)
        frame(910, 50, down = true)
        frame(910, 50, released = true)

        assertSame(source, unscaledTarget.dropped, "drag from 1.5x subtree onto 1x target inside a 2x canvas")
    }

    @Test
    fun rotatedPressableElementInScaledSubtreeReportsUnscaledRotatedPoint() {
        val map = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            pressable = true
            offsetX = 0f; offsetY = 0f; width = 100f; height = 100f
            style = CanvasStyle(rotation = 90f)
        }
        val items = listOf(CanvasItem(1, map, scaler = onePointFive)) // 150x150 px on screen
        val host = ComposeHost()
        fun frame(x: Int, y: Int, pressed: Boolean = false, down: Boolean = false, released: Boolean = false) =
            host.frame(FrameInput(960, 540, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
                CanvasOverlay(items)
            }

        // Centre of 150x150 px box is (75, 75) -> (50, 50) in 100x100 element units regardless of 90 deg rotation.
        frame(75, 75)
        frame(75, 75, pressed = true, down = true)
        frame(75, 75, released = true)

        val point = assertNotNull(map.consumePressAt())
        assertEquals(50f, point.x, 1e-3f)
        assertEquals(50f, point.y, 1e-3f)
    }

    @Test
    fun scopeDefaultsToCanvasAndIsLeftOutOfCompactJson() {
        val json = Json { encodeDefaults = false }
        assertEquals(CanvasScaleScope.Canvas, CanvasScaler().scope)
        assertEquals("{}", json.encodeToString(CanvasScaler.serializer(), CanvasScaler()))

        val subtree = CanvasScaler(scope = CanvasScaleScope.Subtree, snap = CanvasScaleSnap.HalfStep)
        val encoded = json.encodeToString(CanvasScaler.serializer(), subtree)

        assertEquals("""{"snap":"HalfStep","scope":"Subtree"}""", encoded)
        assertEquals(subtree, json.decodeFromString(CanvasScaler.serializer(), encoded))
    }

    @Test
    fun existingWholeCanvasScalerRendersUnchanged() {
        val a = CanvasElement().apply { offsetX = 10f; offsetY = 8f; width = 80f; height = 24f }
        val b = CanvasElement().apply { anchor = CanvasAnchor.BottomRight; offsetX = 12f; offsetY = 6f; width = 40f; height = 20f }
        val scaler = CanvasScaler(referenceWidth = 640f, referenceHeight = 360f, match = 1f)

        val out = ComposeHost().frame(FrameInput(960, 540)) {
            CanvasOverlay(listOf(CanvasItem(1, a), CanvasItem(2, b)), scaler = scaler)
        }

        assertEquals(listOf(15, 12, 120, 36), out.boundsOf(1))
        assertEquals(listOf(882, 501, 60, 30), out.boundsOf(2))
        assertNull(ItemsWithDefaultConstructor(a).scaler)
    }

    private fun ItemsWithDefaultConstructor(element: CanvasElement): CanvasItem = CanvasItem(1, element)
}

private fun FrameOutput.boundsOf(id: Int): List<Int> {
    val node = assertNotNull(semantics.findSubtreeTag("canvas-element-$id"), "missing canvas-element-$id")
    return listOf(node.x, node.y, node.width, node.height)
}

private fun List<SemanticsNode>.findSubtreeTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findSubtreeTag(tag) }
