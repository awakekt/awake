/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.input.nestedscroll.NestedScrollConnection
import io.github.awakelab.awake.compose.ui.input.nestedscroll.NestedScrollDispatcher
import io.github.awakelab.awake.compose.ui.input.nestedscroll.NestedScrollSource
import io.github.awakelab.awake.compose.ui.input.nestedscroll.nestedScroll
import io.github.awakelab.awake.compose.ui.layout.Layout
import io.github.awakelab.awake.compose.ui.layout.MeasurePolicy
import io.github.awakelab.awake.compose.ui.platform.ComposeHost
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import kotlin.test.Test
import kotlin.test.assertEquals

class NestedScrollTest {

    private val stackPolicy = MeasurePolicy { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(200, 200) {
            placeables.forEach { it.placeAt(0, 0) }
        }
    }

    @Test
    fun parentConnectionReceivesPreScrollAndPostScrollFromChild() {
        var preConsumedY = 0f
        var postConsumedY = 0f

        val parentConnection = object : NestedScrollConnection {
            override fun onPreScroll(availableX: Float, availableY: Float, source: NestedScrollSource): FloatArray {
                // Parent consumes half of available Y
                val consumedY = availableY / 2f
                preConsumedY += consumedY
                return floatArrayOf(0f, consumedY)
            }

            override fun onPostScroll(
                consumedX: Float,
                consumedY: Float,
                availableX: Float,
                availableY: Float,
                source: NestedScrollSource,
            ): FloatArray {
                postConsumedY += availableY
                return floatArrayOf(0f, availableY)
            }
        }

        val childDispatcher = NestedScrollDispatcher()
        val host = ComposeHost()

        val content: context(Composer) () -> Unit = {
            Layout(
                nodeType = "parent",
                modifier = Modifier.nestedScroll(parentConnection),
                measurePolicy = stackPolicy,
                content = {
                    Layout(
                        nodeType = "child",
                        modifier = Modifier.nestedScroll(object : NestedScrollConnection {}, childDispatcher),
                        measurePolicy = stackPolicy,
                    )
                },
            )
        }

        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 200), content)

        // Child receives 100 delta
        val pre = childDispatcher.dispatchPreScroll(0f, 100f, NestedScrollSource.UserInput)
        assertEquals(50f, pre[1]) // Parent consumed 50
        assertEquals(50f, preConsumedY)

        // Child consumed 30 of the remaining 50 -> 20 left for post-scroll
        val post = childDispatcher.dispatchPostScroll(0f, 30f, 0f, 20f, NestedScrollSource.UserInput)
        assertEquals(20f, post[1]) // Parent consumed remaining 20
        assertEquals(20f, postConsumedY)
    }
}
