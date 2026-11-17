/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * What a host that skips unchanged frames sees of a canvas element (#1512): a write of the value
 * already there is no change, any other write is one, whether made through the element or straight
 * to a component it got from elsewhere, and so is adding, replacing or removing a component.
 */
class CanvasRevisionTest {
    @Test
    fun writingTheValueAlreadyThereChangesNothing() {
        val element = CanvasElement().apply { kind = CanvasElementKind.Bar; offsetX = 10f; text = "HP"; value = 0.5f }
        val before = element.contentRevision
        element.offsetX = 10f
        element.text = "H" + "P"
        element.value = 0.5f
        element.rect.visible = true
        assertEquals(before, element.contentRevision)
    }

    @Test
    fun aChangedValueChangesTheRevisionThroughTheElementOrTheComponent() {
        val element = CanvasElement().apply { kind = CanvasElementKind.Button }
        var last = element.contentRevision
        fun changed(what: String, write: () -> Unit) {
            write()
            assertNotEquals(last, element.contentRevision, what)
            last = element.contentRevision
        }
        changed("offsetX") { element.offsetX = 3f }
        changed("rect.offsetX") { element.rect.offsetX = 4f }
        changed("text part") { element.get(CanvasText::class)!!.text = "Fire" }
        changed("widget") { (element.widget as CanvasButtonWidget).charges = 2 }
        changed("pointer") { element.pointer.focused = true }
        changed("compose content") { element.composeContent = CanvasComposeContent(null) {} }
    }

    @Test
    fun addingReplacingAndRemovingComponentsChangeTheRevision() {
        val element = CanvasElement()
        var last = element.contentRevision
        fun changed(what: String, write: () -> Unit) {
            write()
            assertNotEquals(last, element.contentRevision, what)
            last = element.contentRevision
        }
        changed("add a look") { element.set(CanvasAppearance()) }
        // A fresh part's own revision starts at 0 again; the replacement still counts.
        changed("replace it with an equal one") { element.set(CanvasAppearance()) }
        changed("remove it") { element.remove(CanvasAppearance::class) }
        changed("a new kind") { element.kind = CanvasElementKind.Bar }
        element.layout = null
        assertEquals(last, element.contentRevision, "clearing a layout it does not have")
    }

    @Test
    fun aRevisionIsCountedOnceForEachChange() {
        val rect = CanvasRect()
        rect.width = rect.width
        assertEquals(0, rect.revision)
        rect.width += 1f
        rect.width = rect.width
        assertEquals(1, rect.revision)
    }
}
