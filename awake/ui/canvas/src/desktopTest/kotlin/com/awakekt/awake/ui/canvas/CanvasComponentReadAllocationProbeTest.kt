/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Reading a component through [CanvasElementBase.get], as a HUD does every frame, allocates nothing
 * once warm, for every component kind (#1169). The types are held in vals, as a caller that reads
 * every frame keeps them: on the JVM each `X::class` written out makes a new class reference.
 *
 * The quietest of several windows must be 0 bytes, so the JIT's own allocations while it
 * recompiles, which can land in more than one window on a busy machine, do not fail the probe; one
 * read that allocates would show in every window.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class CanvasComponentReadAllocationProbeTest {
    /** An element of every kind, each with every component that is not a widget. */
    private val elements = CanvasElementKind.entries.map { kind ->
        CanvasElement().apply {
            this.kind = kind
            set(CanvasText())
            set(CanvasAppearance())
            set(CanvasLayout())
            set(CanvasCluster())
            set(CanvasBinding(node = "player"))
            set(CanvasFollow())
            set(CanvasFollowArrow())
            set(CanvasInput())
            set(CanvasTooltip())
        }
    }

    @Test
    fun readingEveryComponentKindAllocatesNothing() {
        for (element in elements) {
            for (type in COMPONENT_TYPES) assertNotNull(element.get(type), "a ${element.kind} element has a ${type.simpleName}")
        }
        for (type in WIDGET_TYPES) assertNotNull(elements.firstNotNullOfOrNull { it.get(type) }, "an element has a ${type.simpleName}")
        var found = 0
        fun reads() {
            for (e in elements.indices) {
                val element = elements[e]
                for (type in COMPONENT_TYPES) if (element.get(type) != null) found++
                for (type in WIDGET_TYPES) if (element.get(type) != null) found++
            }
        }
        repeat(WARMUP_ROUNDS) { reads() }

        val windows = LongArray(MEASURED_WINDOWS)
        repeat(MEASURED_WINDOWS) { window ->
            val start = allocated()
            repeat(MEASURED_ROUNDS) { reads() }
            windows[window] = allocated() - start
        }
        windows.sort()

        val reads = MEASURED_ROUNDS * elements.size * (COMPONENT_TYPES.size + WIDGET_TYPES.size)
        println("CanvasComponentRead: ${windows.joinToString()} bytes per $reads reads ($found found)")
        assertEquals(0L, windows[0], "fewest bytes over $reads reads")
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        val COMPONENT_TYPES: Array<KClass<out Any>> = arrayOf(
            CanvasRect::class, CanvasPointerState::class, CanvasText::class, CanvasAppearance::class, CanvasLayout::class,
            CanvasCluster::class, CanvasBinding::class, CanvasFollow::class, CanvasFollowArrow::class, CanvasInput::class,
            CanvasTooltip::class,
        )
        val WIDGET_TYPES: Array<KClass<out Any>> = arrayOf(
            CanvasWidget::class, CanvasBarWidget::class, CanvasButtonWidget::class, CanvasTextFieldWidget::class,
            CanvasJoystickWidget::class, CanvasImageWidget::class, CanvasViewWidget::class, CanvasLookAreaWidget::class,
            CanvasSweepWidget::class, CanvasStepperWidget::class,
        )
        const val WARMUP_ROUNDS = 5_000
        const val MEASURED_ROUNDS = 500
        const val MEASURED_WINDOWS = 5
    }
}
