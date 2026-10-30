/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.core.color.Color
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Negative control: bypassing `CanvasStateStyle.shadow` and `scale` makes hovered `(50, 8)` read
 * `[0, 0, 0, 255]` instead of green spread shadow (`g > 140`), and makes pressed `(30, 50)` read
 * `[127, 0, 63, 255]` (unscaled fill over idle blue shadow) instead of `[0, 0, 0, 255]`.
 */
class CanvasStateStyleTest {

    @Test
    fun legacyJsonBytesAndDefaultStylesRemainUnchanged() {
        val compact = Json { encodeDefaults = false }
        assertEquals("{}", compact.encodeToString(CanvasStyle()))
        assertEquals("{}", compact.encodeToString(CanvasStateStyle()))
        assertEquals("{}", compact.encodeToString(CanvasControlTheme()))

        val legacyState = """{"background":"#112233","alpha":0.8}"""
        val decodedState = Json.decodeFromString<CanvasStateStyle>(legacyState)
        assertEquals(null, decodedState.shadow)
        assertEquals(null, decodedState.scale)
        assertEquals(legacyState, compact.encodeToString(decodedState))

        val rich = CanvasStyle(
            transitionSeconds = 0.15f,
            hovered = CanvasStateStyle(shadow = CanvasShadow("#00FF0080", offsetY = 0f, blur = 8f, spread = 2f), scale = 1.05f),
            pressed = CanvasStateStyle(shadow = CanvasShadow("#00FFFFC0", offsetX = 4f, offsetY = 1f, blur = 4f), scale = 0.92f, alpha = 0.75f),
        )
        assertEquals(rich, Json.decodeFromString<CanvasStyle>(Json.encodeToString(rich)))
    }

    @Test
    fun badScaleShadowAndTransitionReportTheirFieldsAndThemeTokensResolve() {
        val bad = CanvasStyle(
            transitionSeconds = -0.1f,
            hovered = CanvasStateStyle(scale = -0.5f),
            pressed = CanvasStateStyle(shadow = CanvasShadow(color = "cyan", blur = -2f), scale = Float.NaN),
        )
        assertEquals(
            listOf(
                "transitionSeconds must not be negative",
                "hovered.scale must not be negative",
                "pressed.shadow.color \"cyan\" must be #RRGGBB or #RRGGBBAA",
                "pressed.shadow.blur must not be negative",
                "pressed.scale must not be negative",
            ),
            bad.problems(),
        )
        assertEquals(
            listOf("transitionSeconds must not be negative"),
            CanvasControlTheme(preset = CanvasThemePreset.None, transitionSeconds = -1f).problems(),
        )

        val theme = CanvasControlTheme(
            preset = CanvasThemePreset.None,
            colors = mapOf("glow" to "#00FFFF80"),
            transitionSeconds = 0.12f,
            skin = CanvasControlSkin(
                button = CanvasButtonSkin(
                    background = CanvasSlot(
                        style = CanvasStyle(
                            pressed = CanvasStateStyle(shadow = CanvasShadow(color = "@glow", offsetY = 0f, blur = 6f), scale = 0.94f),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(emptyList(), theme.problems())
        val resolved = assertNotNull(theme.resolve(null)?.button?.background?.style?.pressed)
        assertEquals("#00FFFF80", resolved.shadow?.color)
        assertEquals(0.94f, resolved.scale)
    }

    @Test
    fun pressedAndHoveredButtonsChangeShadowScaleAndAlphaFromDataOnly() {
        for (actionButton in listOf(false, true)) {
            val button = styledFeedbackButton(actionButton)
            val host = ComposeHost()
            fun render(pointerX: Int = FrameInput.UNKNOWN_POINTER, pointerY: Int = FrameInput.UNKNOWN_POINTER, down: Boolean = false): ByteArray {
                val input = if (actionButton && down) {
                    FrameInput(SIZE, SIZE, pointerX = pointerX, pointerY = pointerY, pointers = listOf(PointerFrame(1L, pointerX, pointerY, down = true, pressed = true)))
                } else {
                    FrameInput(SIZE, SIZE, pointerX = pointerX, pointerY = pointerY, pointerDown = down, pointerPressed = down)
                }
                return host.frame(input) { CanvasOverlay(listOf(CanvasItem(1, button)), showTouchControls = true) }
                    .primitives.rasterize(SIZE, SIZE, Color.Black)
            }

            val idle = render()
            assertEquals(listOf(255, 0, 0, 255), idle.rgbaAt(50, 50), "actionButton=$actionButton idle centre")
            assertEquals(listOf(255, 0, 0, 255), idle.rgbaAt(24, 50), "actionButton=$actionButton idle unscaled edge")
            assertTrue(idle.rgbaAt(50, 86)[2] > 220 && idle.rgbaAt(50, 86)[0] == 0, "actionButton=$actionButton idle blue drop shadow below")
            assertEquals(listOf(0, 0, 0, 255), idle.rgbaAt(50, 12), "actionButton=$actionButton nothing above in idle")

            if (!actionButton) {
                val hovered = render(pointerX = 50, pointerY = 50, down = false)
                val topGlow = hovered.rgbaAt(50, 8)
                assertTrue(topGlow[1] > 140 && topGlow[0] == 0, "hovered green spread shadow above button: $topGlow")
                assertTrue(hovered.rgbaAt(50, 50)[0] in 195..210, "hovered alpha=0.8 dims red fill: ${hovered.rgbaAt(50, 50)}")
            }

            val pressed = render(pointerX = 50, pointerY = 50, down = true)
            assertEquals(listOf(0, 0, 0, 255), pressed.rgbaAt(24, 50), "actionButton=$actionButton 0.5x scale pulls fill inward from x=24")
            assertTrue(pressed.rgbaAt(50, 50)[0] in 126..129, "actionButton=$actionButton pressed alpha=0.5 at centre: ${pressed.rgbaAt(50, 50)}")
            assertEquals(listOf(0, 0, 0, 255), pressed.rgbaAt(50, 86), "actionButton=$actionButton idle drop shadow is replaced")
            val rightShadow = pressed.rgbaAt(70, 50)
            assertTrue(rightShadow[1] > 100 && rightShadow[2] > 100 && rightShadow[0] == 0, "actionButton=$actionButton pressed cyan shadow to the right: $rightShadow")
        }
    }

    @Test
    fun scaleDoesNotMoveLayoutOrChangeHitTestBounds() {
        val row = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            offsetX = 10f; offsetY = 20f; width = 160f; height = 60f
            layout = CanvasLayout(direction = CanvasDirection.Row, gap = 10f)
        }
        val button = CanvasElement().apply {
            kind = CanvasElementKind.Button
            width = 60f; height = 60f; background = "#FF0000"
            style = CanvasStyle(
                hovered = CanvasStateStyle(scale = 1.5f),
                pressed = CanvasStateStyle(scale = 0.5f),
            )
        }
        val neighbour = CanvasElement().apply {
            kind = CanvasElementKind.Button
            width = 60f; height = 60f; background = "#00FF00"
        }
        val items = listOf(CanvasItem(1, row), CanvasItem(2, button, parent = row), CanvasItem(3, neighbour, parent = row))
        val host = ComposeHost()
        fun step(px: Int = FrameInput.UNKNOWN_POINTER, py: Int = FrameInput.UNKNOWN_POINTER, down: Boolean = false, pressed: Boolean = false, released: Boolean = false) =
            host.frame(FrameInput(200, 100, pointerX = px, pointerY = py, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
                CanvasOverlay(items)
            }

        step()
        val idleButtonBounds = assertNotNull(button.screenBounds)
        val idleNeighbourBounds = assertNotNull(neighbour.screenBounds)
        assertEquals(10f, idleButtonBounds.x)
        assertEquals(80f, idleNeighbourBounds.x)

        // Hover scales paint to 1.5x (visually [ -5 .. 85 ], overlapping neighbour at x=80..85),
        // yet layout bounds stay fixed and clicking at x=82 hits the neighbour, not the scaled button.
        step(px = 40, py = 50)
        assertEquals(idleButtonBounds, button.screenBounds)
        assertEquals(idleNeighbourBounds, neighbour.screenBounds)
        step(px = 82, py = 50, down = true, pressed = true)
        step(px = 82, py = 50, down = false, released = true)
        assertFalse(button.consumePress(), "1.5x visual overflow outside layout box must not capture clicks")
        assertTrue(neighbour.consumePress(), "click at x=82 lands on the neighbour's unmoved box")

        // Press at (14, 24) -- inside the unscaled [10..70] x [20..80] box, but outside the 0.5x
        // scaled quad [25..55] x [35..65] -- still presses and releases cleanly as a tap.
        step(px = 14, py = 24, down = true, pressed = true)
        assertEquals(idleButtonBounds, button.screenBounds, "0.5x pressed scale leaves layout bounds unchanged")
        assertEquals(idleNeighbourBounds, neighbour.screenBounds, "0.5x pressed scale leaves sibling bounds unchanged")
        step(px = 14, py = 24, down = false, released = true)
        assertTrue(button.consumePress(), "releasing inside unscaled corner outside 0.5x visual quad still taps")
    }

    @Test
    fun statesInterpolateOverStyleOrThemeDurationAndSnapAtZero() {
        for (fromTheme in listOf(false, true)) {
            val button = styledFeedbackButton(actionButton = false).apply {
                style = style.copy(transitionSeconds = if (fromTheme) null else 0.2f)
            }
            val controls = if (fromTheme) CanvasControls(theme = CanvasControlTheme(preset = CanvasThemePreset.None, transitionSeconds = 0.2f)) else null
            val host = ComposeHost()
            fun render(px: Int = FrameInput.UNKNOWN_POINTER, py: Int = FrameInput.UNKNOWN_POINTER, down: Boolean = false, dt: Float = 0.05f): ByteArray =
                host.frame(FrameInput(SIZE, SIZE, pointerX = px, pointerY = py, pointerDown = down, deltaSeconds = dt)) {
                    CanvasOverlay(listOf(CanvasItem(1, button)), controls = controls)
                }.primitives.rasterize(SIZE, SIZE, Color.Black)

            val idle = render(dt = 0.05f)
            assertEquals(255, idle.rgbaAt(50, 50)[0], "fromTheme=$fromTheme initial frame snaps to idle")

            // Halfway (0.1s of 0.2s) into pressed (scale 1.0 -> 0.5 => 0.75, alpha 1.0 -> 0.5 => 0.75).
            val mid = render(px = 50, py = 50, down = true, dt = 0.1f)
            val midCentreRed = mid.rgbaAt(50, 50)[0]
            assertTrue(midCentreRed in 180..202, "fromTheme=$fromTheme halfway alpha ~0.75 gives ~191 red, got $midCentreRed")
            assertTrue(mid.rgbaAt(30, 50)[0] > 150, "fromTheme=$fromTheme at 0.75x scale [27.5..72.5], x=30 is still covered")

            // Reversing mid-flight toward hovered (alpha=0.8, scale=1.1) starts from 0.75, not jumping to 0.5 or 1.1.
            val reversed = render(px = 50, py = 50, down = false, dt = 0.02f)
            assertTrue(reversed.rgbaAt(50, 50)[0] in 185..205, "fromTheme=$fromTheme smooth mid-flight reversal: ${reversed.rgbaAt(50, 50)}")

            // Finish transition to hovered.
            val settledHover = render(px = 50, py = 50, down = false, dt = 0.25f)
            assertTrue(settledHover.rgbaAt(50, 50)[0] in 198..210, "fromTheme=$fromTheme settled at hovered alpha=0.8")

            // Explicit style transitionSeconds = 0f overrides the theme and snaps in one frame.
            button.style = button.style.copy(transitionSeconds = 0f)
            val snappedPress = render(px = 50, py = 50, down = true, dt = 0.01f)
            assertTrue(snappedPress.rgbaAt(50, 50)[0] in 126..129, "fromTheme=$fromTheme duration=0 snaps immediately")
            assertEquals(listOf(0, 0, 0, 255), snappedPress.rgbaAt(30, 50), "fromTheme=$fromTheme 0.5x scale snapped immediately")
        }
    }

    private fun styledFeedbackButton(actionButton: Boolean): CanvasElement = CanvasElement().apply {
        kind = CanvasElementKind.Button
        offsetX = 20f; offsetY = 20f; width = 60f; height = 60f
        background = "#FF0000"
        if (actionButton) button = CanvasButton()
        style = CanvasStyle(
            shadow = CanvasShadow(color = "#0000FF", offsetY = 12f, blur = 0f),
            hovered = CanvasStateStyle(
                shadow = CanvasShadow(color = "#00FF00", offsetY = 0f, blur = 0f, spread = 10f),
                scale = 1.1f,
                alpha = 0.8f,
            ),
            pressed = CanvasStateStyle(
                shadow = CanvasShadow(color = "#00FFFF", offsetX = 14f, offsetY = 0f, blur = 0f, spread = 4f),
                scale = 0.5f,
                alpha = 0.5f,
            ),
        )
    }

    private fun ByteArray.rgbaAt(x: Int, y: Int): List<Int> = (0..3).map { this[(y * SIZE + x) * 4 + it].toInt() and 0xFF }

    private companion object {
        const val SIZE = 100
    }
}
