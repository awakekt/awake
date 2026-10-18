/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import com.awakekt.awake.ui.canvas.CanvasBar
import com.awakekt.awake.ui.canvas.CanvasBarDirection
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasMask
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasSweep
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A `canvas_element`'s `bar`, `sweep` and `stepper` settings: saved, checked, and drawn where they say. */
class CanvasGaugesSceneTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aBarSavedBeforeItHadSettingsStillReadsAndDrawsAsBefore() {
        val element = json.decodeFromString(SceneCanvasElement.serializer(), """{ "kind": "Bar", "value": 0.5 }""")

        assertNull(element.bar)
        assertNull(element.sweep)
        assertNull(element.stepper)
        assertEquals(emptyList(), element.validate("nodes[0]"))
        val written = Json.encodeToString(SceneCanvasElement.serializer(), element)
        assertTrue("bar" !in written && "sweep" !in written && "stepper" !in written, "nothing new is written for an old element")
    }

    @Test
    fun everySettingRoundTrips() {
        val saved = listOf(
            SceneCanvasElement(
                kind = CanvasElementKind.Bar,
                bar = CanvasBar(CanvasBarDirection.Radial, 6f, "#FFFFFF20", "#E5484D", 0.3f, 2f, 5, 3f, 4, "#000000", label = true),
                bind = CanvasBinding("Player", value = "health.current", max = "health.max", text = "{health.current}"),
            ),
            SceneCanvasElement(kind = CanvasElementKind.Sweep, sweep = CanvasSweep(45f, false, CanvasMask.Pill, 4f, "#00000080")),
            SceneCanvasElement(kind = CanvasElementKind.Stepper, stepper = CanvasStepper(2.5f, -5f, 5f, 0.5f, 1, 0.3f, 0.05f)),
        )

        for (element in saved) {
            assertEquals(element, element.toComponent().toSceneComponent())
            assertEquals(element, json.decodeFromString(SceneCanvasElement.serializer(), Json.encodeToString(SceneCanvasElement.serializer(), element)))
        }
    }

    @Test
    fun aStepperSavesItsNumberAsWhereItStarts() {
        val element = SceneCanvasElement(kind = CanvasElementKind.Stepper, stepper = CanvasStepper(value = 2f)).toComponent()

        element.stepBy(3)

        assertEquals(5f, element.toSceneComponent().stepper?.value)
    }

    @Test
    fun validationNamesBadSettingsAndSettingsOnTheWrongKind() {
        val issues = SceneCanvasElement(
            kind = CanvasElementKind.Panel,
            bar = CanvasBar(trailSpeed = 0f),
            sweep = CanvasSweep(),
            stepper = CanvasStepper(value = 20f, max = 10f),
        ).validate("nodes[0]").map { it.message }

        assertEquals(
            listOf(
                "canvas_element.bar only sets up a Bar",
                "canvas_element.sweep only sets up a Sweep",
                "canvas_element.stepper only sets up a Stepper",
                "canvas_element.bar.trailSpeed must be above 0",
                "canvas_element.stepper.value must be between min and max",
            ),
            issues,
        )
    }

    @Test
    fun aSweepTurnsFromItsStartAngleTheWayItIsTold() {
        // A quarter from 3 o'clock, counter-clockwise, is the top right.
        val pixels = draw(CanvasSweep(startAngle = 90f, clockwise = false), 0.25f)

        assertEquals(RED, pixels(75, 25), "top right")
        assertEquals(BLACK, pixels(75, 75), "bottom right")
        assertEquals(BLACK, pixels(25, 25), "top left")
    }

    @Test
    fun aCircleMaskKeepsThePieInsideTheCircle() {
        val pixels = draw(CanvasSweep(mask = CanvasMask.Circle), 1f)

        assertEquals(RED, pixels(50, 50))
        assertEquals(BLACK, pixels(3, 3), "the box's corner is outside the circle")
    }

    @Test
    fun aRingLeavesItsMiddleOut() {
        val pixels = draw(CanvasSweep(thickness = 10f), 0.5f)

        assertEquals(RED, pixels(95, 50), "on the ring at 3 o'clock")
        assertEquals(BLACK, pixels(50, 50), "the middle")
        assertEquals(BLACK, pixels(5, 50), "past half a turn")
    }

    @Test
    fun aVerticalBarFillsUpFromTheBottom() {
        val world = World().also { world ->
            world.add(
                world.create(),
                CanvasElement().apply {
                    kind = CanvasElementKind.Bar; offsetX = 0f; offsetY = 0f; width = 100f; height = 100f; value = 0.25f; color = "#FF0000"
                    bar = CanvasBar(direction = CanvasBarDirection.BottomToTop)
                },
            )
        }
        val pixels = rasterize(world)

        assertEquals(RED, pixels(50, 90))
        assertEquals(BLACK, pixels(50, 60))
    }

    /** One 100 x 100 Sweep at [value] as [sweep] says, red over black, as a reader of 0xRRGGBB at (x, y). */
    private fun draw(sweep: CanvasSweep, value: Float): (Int, Int) -> Int {
        val world = World()
        world.add(
            world.create(),
            CanvasElement().apply {
                kind = CanvasElementKind.Sweep; offsetX = 0f; offsetY = 0f; width = 100f; height = 100f
                this.value = value; color = "#FF0000"; this.sweep = sweep
            },
        )
        return rasterize(world)
    }

    private fun rasterize(world: World): (Int, Int) -> Int {
        val pixels = ComposeHost().frame(FrameInput(100, 100)) { SceneCanvas(world) }.primitives.rasterize(100, 100, Color.Black)
        // Red or black to within a step: an anti-aliased edge's fringe is a hair under full red.
        return { x, y ->
            val offset = (y * 100 + x) * 4
            if ((pixels[offset].toInt() and 0xFF) > HALF) RED else BLACK
        }
    }

    private companion object {
        const val RED = 0xFF0000
        const val BLACK = 0x000000
        const val HALF = 128
    }
}
