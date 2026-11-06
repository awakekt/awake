/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.RememberObserver
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.SubcomposeLayout
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object ForgetChildType

private val frame = FrameInput(viewportWidth = 100, viewportHeight = 100)

/** What a subcomposition remembers is forgotten when the subcomposition or its layout goes. */
class SubcomposeForgetTest {

    private val forgotten = mutableListOf<String>()

    private inner class Tracked(val name: String) : RememberObserver {
        override fun onForgotten() {
            forgotten += name
        }
    }

    context(_: Composer)
    private fun TrackedSlots(slots: List<String>) {
        SubcomposeLayout { constraints ->
            val placeables = slots.flatMap { id ->
                subcompose(id) {
                    remember<Tracked> { Tracked(id) }
                    Layout(ForgetChildType, measurePolicy = MeasurePolicy { _, _ -> layout(10, 10) {} })
                }.map { it.measure(constraints) }
            }
            layout(100, 100) { placeables.forEach { it.placeAt(0, 0) } }
        }
    }

    @Test
    fun aSlotNoLongerSubcomposedIsForgotten() {
        val host = ComposeHost()
        var slots = listOf("a", "b")
        val content: context(Composer)
        () -> Unit = { TrackedSlots(slots) }

        host.frame(frame, content)
        host.frame(frame, content)
        assertTrue(forgotten.isEmpty(), "a kept slot is not forgotten")

        slots = listOf("b")
        host.frame(frame, content)

        assertEquals(listOf("a"), forgotten)
    }

    @Test
    fun aRemovedSubcomposeLayoutForgetsItsSlotsOnce() {
        val host = ComposeHost()
        var show = true
        val content: context(Composer)
        () -> Unit = { if (show) TrackedSlots(listOf("a", "b")) }

        host.frame(frame, content)
        show = false
        host.frame(frame, content)

        assertEquals(listOf("a", "b"), forgotten.sorted())
    }
}
