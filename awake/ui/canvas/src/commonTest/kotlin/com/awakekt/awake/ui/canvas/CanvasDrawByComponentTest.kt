/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The overlay draws and routes the pointer by an element's components, never by its kind: a button
 * widget takes taps wherever it is, an input component makes a picture or a box pressable, words are
 * drawn where the element shows them.
 */
class CanvasDrawByComponentTest {
    private fun element(vararg components: Any): CanvasElement = CanvasElement().apply {
        remove(CanvasText::class)
        components.forEach(::set)
        offsetX = 0f
        offsetY = 0f
        width = 40f
        height = 24f
    }

    @Test
    fun aButtonWidgetTakesTapsAndAnInputMakesOnlyAPictureOrABoxPressable() {
        val pressable = CanvasInput().apply { pressable = true }

        val cases = listOf(
            element(CanvasButtonWidget()) to true,
            element(CanvasImageWidget(), pressable) to true,
            element(CanvasInput().apply { this.pressable = true }) to true,
            element(CanvasText(), CanvasInput().apply { this.pressable = true }) to false,
            element(CanvasBarWidget(), CanvasInput().apply { this.pressable = true }) to false,
            element(CanvasImageWidget()) to false,
        )
        for ((element, expected) in cases) {
            val host = ComposeHost()
            fun frame(down: Boolean = false, pressed: Boolean = false, released: Boolean = false) {
                host.frame(FrameInput(100, 100, pointerX = 10, pointerY = 10, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
                    CanvasOverlay(listOf(CanvasItem(1, element)))
                }
            }
            frame()
            frame(down = true, pressed = true)
            frame(released = true)

            assertEquals(expected, element.pointer.consumePress(), "${element.kind}: actual pointer routing")
        }
    }

    @Test
    fun wordsAreShownByAnElementWithNoWidgetAButtonOrAField() {
        val words = CanvasText().apply { text = "Ready" }
        val cases = listOf(
            element(words) to true,
            element(words, CanvasButtonWidget()) to true,
            element(words, CanvasTextFieldWidget()) to true,
            element(words, CanvasBarWidget()) to false,
            element(CanvasButtonWidget()) to false,
        )
        for ((element, expected) in cases) {
            val tree = CanvasTree(listOf(CanvasItem(1, element)), true, emptyMap(), CanvasResolver.None)
            val out = ComposeHost().frame(FrameInput(100, 100)) {
                Box { GhostElementView(element, Modifier, TextStyle.Default, tree, emptyList()) }
            }

            assertEquals(expected, out.primitives.any { it is UiDrawPrimitive.Glyph }, "${element.kind}: drag ghost words")
        }
    }

    @Test
    fun eachWidgetDrawsItsOwnView() {
        val items = listOf(
            CanvasBarWidget(), CanvasSweepWidget(), CanvasStepperWidget(), CanvasButtonWidget(), CanvasTextFieldWidget(),
            CanvasJoystickWidget(), CanvasViewWidget(), CanvasLookAreaWidget(), CanvasImageWidget(),
        ).mapIndexed { index, widget -> CanvasItem(index, element(CanvasText(), widget).apply { offsetY = index * 30f; height = 24f }) }

        val out = ComposeHost().frame(FrameInput(400, 400)) { CanvasOverlay(items, showTouchControls = true) }

        for (item in items) {
            assertTrue(out.semantics.any { it.hasTag("canvas-element-${item.id}") }, "${item.element.kind} is drawn")
        }
    }

    private fun com.awakekt.awake.compose.ui.semantics.SemanticsNode.hasTag(tag: String): Boolean =
        testTag == tag || children.any { it.hasTag(tag) }
}
