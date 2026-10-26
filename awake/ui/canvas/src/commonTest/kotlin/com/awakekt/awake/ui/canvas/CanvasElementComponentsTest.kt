/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A [CanvasElement] is its components: its kind follows from them, and its flat properties read and write them. */
class CanvasElementComponentsTest {
    @Test
    fun aNewElementIsAnEmptyTextWithARectAndAPointer() {
        val element = CanvasElement()

        assertEquals(CanvasElementKind.Text, element.kind)
        assertNull(element.widget)
        assertEquals(listOf(CanvasRect::class, CanvasPointerState::class, CanvasText::class), element.components().map { it::class })
    }

    @Test
    fun theKindFollowsTheWidgetThenTheWords() {
        val element = CanvasElement()

        element.set(CanvasBarWidget())
        assertEquals(CanvasElementKind.Bar, element.kind)
        element.remove(CanvasBarWidget::class)
        assertEquals(CanvasElementKind.Text, element.kind)
        element.remove(CanvasText::class)
        assertEquals(CanvasElementKind.Panel, element.kind)
    }

    @Test
    fun settingTheKindGivesTheElementThatKindsWidget() {
        for (kind in CanvasElementKind.entries) {
            val element = CanvasElement().apply { this.kind = kind }

            assertEquals(kind, element.kind)
            assertEquals(kind, element.widget?.kind ?: element.kind)
        }
        assertNull(CanvasElement().apply { kind = CanvasElementKind.Panel }.get<CanvasText>(), "a Panel gives up its words")
    }

    @Test
    fun aWidgetTakesThePlaceOfTheOneBefore() {
        val element = CanvasElement()
        element.set(CanvasButtonWidget())
        element.set(CanvasJoystickWidget())

        assertIs<CanvasJoystickWidget>(element.widget)
        assertNull(element.get<CanvasButtonWidget>())
    }

    @Test
    fun switchingBetweenTwoGaugesKeepsTheirSharedFill() {
        val element = CanvasElement().apply {
            kind = CanvasElementKind.Bar
            value = 0.25f
            kind = CanvasElementKind.Sweep
        }

        assertEquals(0.25f, element.get<CanvasSweepWidget>()?.value)
        assertEquals(0.25f, element.value)
    }

    @Test
    fun switchingBetweenControlsKeepsTheirSharedAction() {
        val element = CanvasElement().apply {
            kind = CanvasElementKind.Button
            action = "jump"
            kind = CanvasElementKind.Joystick
        }

        assertEquals("jump", element.get<CanvasJoystickWidget>()?.action)
    }

    @Test
    fun aFlatPropertyReadsItsDefaultWithoutItsComponentAndAddsItOnlyForAnotherValue() {
        val element = CanvasElement()

        assertEquals(CanvasAppearance.DEFAULT_COLOR, element.color)
        element.color = CanvasAppearance.DEFAULT_COLOR
        assertNull(element.get<CanvasAppearance>(), "writing the default adds nothing")
        element.color = "#FF0000"
        assertEquals("#FF0000", element.get<CanvasAppearance>()?.color)
        element.followBounds = true
        assertEquals(true, element.get<CanvasFollow>()?.bounds)
        element.followArrow = true
        assertTrue(element.get<CanvasFollowArrow>() != null)
        element.followArrow = false
        assertNull(element.get<CanvasFollowArrow>())
    }

    @Test
    fun aWidgetOptionIsIgnoredByAnotherWidgetAndAddsItsWidgetToNone() {
        val button = CanvasElement().apply { kind = CanvasElementKind.Button }
        button.bar = CanvasBar(segments = 4)
        assertEquals(CanvasElementKind.Button, button.kind)
        assertNull(button.bar)

        val text = CanvasElement()
        text.bar = CanvasBar(segments = 4)
        assertEquals(CanvasElementKind.Bar, text.kind)
        assertEquals(4, text.get<CanvasBarWidget>()?.settings?.segments)
    }

    @Test
    fun aPanelIsNeverGivenWordsSoItStaysAPanel() {
        val panel = CanvasElement().apply { kind = CanvasElementKind.Panel }
        panel.text = "ignored"
        panel.fontSize = 30f

        assertEquals(CanvasElementKind.Panel, panel.kind)
        assertEquals("", panel.text)
    }

    @Test
    fun aPanelsTextArgsGoToItsTooltip() {
        val panel = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            tooltip = "hud.slot.tooltip"
            textArgs = mapOf("count" to "3")
        }

        assertEquals(mapOf("count" to "3"), panel.get<CanvasTooltip>()?.textArgs)
        assertEquals(mapOf("count" to "3"), panel.textArgs)
        assertNull(panel.get<CanvasText>())
    }

    @Test
    fun aTextsTextArgsStayWithItsWords() {
        val text = CanvasElement().apply { textArgs = mapOf("name" to "Ada") }

        assertEquals(mapOf("name" to "Ada"), text.get<CanvasText>()?.textArgs)
        assertNull(text.get<CanvasTooltip>())
    }

    @Test
    fun getSetAndRemoveWorkByType() {
        val element = CanvasElement()
        val layout = CanvasLayout(gap = 4f)
        val rect = CanvasRect().apply { width = 64f }

        element.set(layout)
        element.set(rect)
        assertSame(layout, element.get<CanvasLayout>())
        assertSame(layout, element.layout)
        assertSame(rect, element.rect)
        assertEquals(64f, element.width)
        assertSame(layout, element.remove(CanvasLayout::class))
        assertNull(element.layout)
        assertFailsWith<IllegalArgumentException> { element.remove(CanvasRect::class) }
        assertFailsWith<IllegalArgumentException> { element.set("not a component") }
    }

    @Test
    fun changingAComponentThroughTheElementBumpsItsVersion() {
        val element = CanvasElement()
        val before = element.componentsVersion

        element.width = 10f
        assertEquals(before, element.componentsVersion, "a field write changes no component")
        element.kind = CanvasElementKind.Button
        assertNotEquals(before, element.componentsVersion)
    }

    @Test
    fun aStepperStartsAtItsSettingsValueAndStepsWithinItsRange() {
        val stepper = CanvasStepperWidget().apply { settings = CanvasStepper(value = 9f, min = 0f, max = 10f) }

        assertEquals(9f, stepper.number)
        assertTrue(stepper.stepBy(5))
        assertEquals(10f, stepper.number)
        assertTrue(stepper.consumeChange())
        assertFalse(stepper.consumeChange())
        assertFalse(stepper.stepBy(1), "held at the top of its range")
    }

    @Test
    fun aButtonReportsOnceWhenItBecomesUsableAgain() {
        val button = CanvasButtonWidget().apply { cooldown = 1f }

        assertTrue(button.coolingDown)
        assertFalse(button.consumeReady())
        button.cooldown = 0f
        assertTrue(button.usable)
        assertTrue(button.consumeReady())
        assertFalse(button.consumeReady())
    }

    @Test
    fun theElementsPressesAreItsPointerStates() {
        val element = CanvasElement().apply { kind = CanvasElementKind.Button }

        element.press()
        assertEquals(1, element.pointer.presses)
        assertEquals(1, element.presses)
        assertTrue(element.pointer.consumePress())
        assertFalse(element.consumePress(), "the element reads the same tap its pointer state took")
    }
}
