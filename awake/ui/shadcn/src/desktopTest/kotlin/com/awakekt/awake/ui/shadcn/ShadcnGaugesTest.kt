/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.testing.assertMatchesBaseline
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.ui.shadcn.components.ShadcnNumberStepper
import com.awakekt.awake.ui.shadcn.components.ShadcnProgressBar
import com.awakekt.awake.ui.shadcn.components.ShadcnProgressOrientation
import com.awakekt.awake.ui.shadcn.components.ShadcnRadialSweep
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** [ShadcnRadialSweep], [ShadcnProgressBar] and [ShadcnNumberStepper]: how they look, and how the stepper steps. */
class ShadcnGaugesTest {

    @Test
    fun radialSweepsMatchTheirBaseline() = baseline("radial-sweep", 280, 64) {
        Row(Modifier.padding(all = 12.dp), horizontalArrangement = Arrangement.spacedByHorizontal(16.dp)) {
            ShadcnRadialSweep(0.3f, size = 40.dp)
            // A cooldown over a square icon: a shade cut to a rounded square, nothing under what is ready.
            ShadcnRadialSweep(0.65f, size = 40.dp, shape = DrawShape.RoundedRectangle(8.dp), color = Color(0f, 0f, 0f, 0.6f), trackColor = Color.Transparent)
            ShadcnRadialSweep(0.65f, size = 40.dp, startAngle = 90f, clockwise = false, thickness = 6.dp)
            ShadcnRadialSweep(1f, size = 40.dp, thickness = 6.dp)
            ShadcnRadialSweep(0f, size = 40.dp)
        }
    }

    @Test
    fun progressBarsMatchTheirBaseline() = baseline("progress-bar", 320, 200) {
        Row(Modifier.padding(all = 12.dp), horizontalArrangement = Arrangement.spacedByHorizontal(16.dp)) {
            Column(Modifier.width(180.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShadcnProgressBar(0.6f, thickness = 20.dp, trailing = 0.8f, label = "60 / 100")
                ShadcnProgressBar(0.5f, thickness = 12.dp, segments = 5)
                ShadcnProgressBar(0.7f, thickness = 12.dp, ticks = 4)
                ShadcnProgressBar(0.35f)
            }
            ShadcnProgressBar(0.4f, Modifier.height(150.dp), orientation = ShadcnProgressOrientation.Vertical, thickness = 12.dp, trailing = 0.55f)
            ShadcnProgressBar(0.7f, orientation = ShadcnProgressOrientation.Radial, label = "70%")
        }
    }

    @Test
    fun aStepperMatchesItsBaseline() = baseline("number-stepper", 200, 60) {
        Column(Modifier.padding(all = 12.dp)) { ShadcnNumberStepper(3f, max = 10f) }
    }

    @Test
    fun aPressStepsAndAHoldRepeats() {
        var value = 3f
        val host = ComposeHost()
        val frame = { pointers: List<PointerFrame>, delta: Float ->
            host.frame(FrameInput(200, 60, pointers = pointers, deltaSeconds = delta)) {
                provideShadcnTheme(theme) { ShadcnNumberStepper(value, max = 10f, onValueChange = { value = it }) }
            }
        }
        frame(emptyList(), 0f)

        frame(listOf(PointerFrame(1, PLUS, 18, down = true, pressed = true)), 0.016f)
        assertEquals(4f, value, "a press steps once")
        repeat(4) { frame(listOf(PointerFrame(1, PLUS, 18, down = true)), 0.1f) }
        assertEquals(5f, value, "a hold repeats once it reaches the delay")
        repeat(20) { frame(listOf(PointerFrame(1, PLUS, 18, down = true)), 0.1f) }
        frame(listOf(PointerFrame(1, PLUS, 18, down = false, released = true)), 0.016f)
        assertEquals(10f, value, "and stops at the maximum")

        frame(listOf(PointerFrame(1, MINUS, 18, down = true, pressed = true)), 0.016f)
        frame(listOf(PointerFrame(1, MINUS, 18, down = false, released = true)), 0.016f)
        assertEquals(9f, value)
    }

    @Test
    fun keysStepItWhileItHasFocus() {
        var value = 50f
        val host = ComposeHost()
        val frame = { pointers: List<PointerFrame>, keys: List<KeyEvent> ->
            host.frame(FrameInput(200, 60, pointers = pointers, keyEvents = keys)) {
                provideShadcnTheme(theme) { ShadcnNumberStepper(value, onValueChange = { value = it }) }
            }
        }
        frame(emptyList(), emptyList())
        // A click in the field gives it the keyboard.
        frame(listOf(PointerFrame(1, FIELD, 18, down = true, pressed = true)), emptyList())
        frame(listOf(PointerFrame(1, FIELD, 18, down = false, released = true)), emptyList())

        for ((key, expected) in listOf(Key.ArrowUp to 51f, Key.PageDown to 41f, Key.End to 100f, Key.Home to 0f)) {
            frame(emptyList(), listOf(KeyEvent(key, KeyEventType.Down)))
            frame(emptyList(), emptyList())
            assertEquals(expected, value, "$key")
        }
    }

    private fun baseline(name: String, width: Int, height: Int, content: context(Composer) () -> Unit) {
        composeFrame(width, height) { provideShadcnTheme(theme) { Column { content() } } }.assertMatchesBaseline(
            name,
            width = width,
            height = height,
            background = theme.palette.background,
            font = UiFonts.default(),
        )
    }

    private val theme = ShadcnThemeValues(ShadcnTheme)

    private companion object {
        /** The middle of the minus button: the first 36 dp. */
        const val MINUS = 18

        /** The middle of the field after it, 64 dp wide. */
        const val FIELD = 72

        /** The middle of the plus button after the field. */
        const val PLUS = 126
    }
}
