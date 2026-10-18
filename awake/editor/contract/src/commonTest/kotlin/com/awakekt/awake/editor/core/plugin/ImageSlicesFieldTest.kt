/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageSlicesFieldTest {

    @Test
    fun aHostThatCannotDrawThePictureEditsTheSlicesAsNumbers() {
        val fields = NumberFields(typed = 6)
        var written: ImageSlices? = null

        fields.imageSlices("Frame", "ui/window.png", ImageSlices(width = 24, height = 24, left = 8, top = 8, right = 8, bottom = 8)) { written = it }

        assertEquals(
            listOf("Frame", "Region X", "Region Y", "Region Width", "Region Height", "Slice Left", "Slice Top", "Slice Right", "Slice Bottom"),
            fields.labels,
        )
        assertEquals(ImageSlices(width = 24, height = 24, left = 8, top = 8, right = 8, bottom = 6), written, "the last field typed writes its own inset")
    }

    @Test
    fun aGuideDraggedPastThePictureStopsAtItsEdge() {
        val dragged = ImageSlices(x = -3, y = 2, width = 40, height = 0, left = 30, top = 1, right = 4, bottom = 50)

        assertEquals(ImageSlices(x = 0, y = 2, width = 24, height = 0, left = 20, top = 0, right = 4, bottom = 22), dragged.keptInside(24, 24))
    }

    @Test
    fun slicesThatFitAreKeptAsTheyAre() {
        val slices = ImageSlices(x = 4, y = 4, width = 16, height = 8, left = 3, top = 2, right = 3, bottom = 2)

        assertEquals(slices, slices.keptInside(24, 24))
    }

    /** Records each field's label; every whole number typed is [typed]. */
    private class NumberFields(private val typed: Int) : InspectorFieldScope {
        val labels = mutableListOf<String>()

        override fun section(title: String, block: InspectorFieldScope.() -> Unit) {
            labels += title
            block()
        }

        override fun integer(label: String, value: Int, write: (Int) -> Unit) {
            labels += label
            if (label == "Slice Bottom") write(typed)
        }

        override fun scalar(label: String, value: Float, write: (Float) -> Unit) = Unit

        override fun toggle(label: String, value: Boolean, write: (Boolean) -> Unit) = Unit

        override fun <T : Enum<T>> options(label: String, value: T, cases: List<T>, write: (T) -> Unit) = Unit

        override fun vector(label: String, value: Vec3f) = Unit
    }
}
