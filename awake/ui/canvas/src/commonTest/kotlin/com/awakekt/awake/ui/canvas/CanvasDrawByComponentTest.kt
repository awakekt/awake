/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The overlay draws and routes the pointer by an element's components, never by its kind: a button
 * widget takes taps wherever it is, an input component makes a picture or a box pressable, words are
 * drawn where the element shows them, and an element's opacity is worked out in one place.
 */
class CanvasDrawByComponentTest {
    private fun element(vararg components: Any): CanvasElement = CanvasElement().apply {
        remove(CanvasText::class)
        components.forEach(::set)
    }

    @Test
    fun aButtonWidgetTakesTapsAndAnInputMakesOnlyAPictureOrABoxPressable() {
        val pressable = CanvasInput().apply { pressable = true }

        assertTrue(element(CanvasButtonWidget()).takesTaps)
        assertTrue(element(CanvasImageWidget(), pressable).takesTaps)
        assertTrue(element(CanvasInput().apply { this.pressable = true }).takesTaps, "a box with no widget and no words")
        assertFalse(element(CanvasText(), CanvasInput().apply { this.pressable = true }).takesTaps, "words are a Text, which never takes a tap")
        assertFalse(element(CanvasBarWidget(), CanvasInput().apply { this.pressable = true }).takesTaps)
        assertFalse(element(CanvasImageWidget()).takesTaps)
    }

    @Test
    fun wordsAreShownByAnElementWithNoWidgetAButtonOrAField() {
        assertTrue(element(CanvasText()).showsWords)
        assertTrue(element(CanvasText(), CanvasButtonWidget()).showsWords)
        assertTrue(element(CanvasText(), CanvasTextFieldWidget()).showsWords)
        assertFalse(element(CanvasText(), CanvasBarWidget()).showsWords, "a bar's words are its label, which the bar draws")
        assertFalse(element(CanvasButtonWidget()).showsWords)
    }

    @Test
    fun anElementsOpacityIsItsOwnTimesTheElementsItIsDrawnIn() {
        val window = element(CanvasAppearance().apply { style = CanvasStyle(alpha = 0.5f) })
        val icon = element(CanvasAppearance().apply { style = CanvasStyle(alpha = 0.5f) }, CanvasImageWidget())
        val plain = element()
        val tree = CanvasTree(listOf(CanvasItem(1, window), CanvasItem(2, icon, window), CanvasItem(3, plain, icon)), true, emptyMap(), CanvasResolver.None)

        assertEquals(0.5f, tree.opacityOf(window))
        assertEquals(0.25f, tree.opacityOf(icon))
        assertEquals(0.25f, tree.opacityOf(plain), "an element without a style of its own takes its parents'")
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
