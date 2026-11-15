/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Joining a frame's staged and built UI primitives allocates nothing once warm (#1621): `+` copied
 * every primitive twice a frame, about 8 B per primitive, 40,472 B at 5,000.
 *
 * Desktop only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class UiPrimitiveJoinAllocationTest {
    private val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val built = List(BUILT) { UiDrawPrimitive.Quad(it.toFloat(), 0f, 4f, 4f, Color(1f, 1f, 1f, 1f)) }
    private val staged = List(STAGED) { UiDrawPrimitive.Quad(it.toFloat(), 8f, 4f, 4f, Color(1f, 0f, 0f, 1f)) }

    /** The quietest window's bytes per frame: a reset and two joins, as the runtime does. */
    private fun bytesPerFrame(join: UiPrimitiveJoin, staged: List<UiDrawPrimitive>): Long {
        fun frame() {
            join.reset()
            join.join(staged, built)
            join.join(staged, built)
        }
        repeat(WARMUP) { frame() }
        var quietest = Long.MAX_VALUE
        repeat(WINDOWS) {
            val start = bean.currentThreadAllocatedBytes
            repeat(PER_WINDOW) { frame() }
            quietest = minOf(quietest, (bean.currentThreadAllocatedBytes - start) / PER_WINDOW)
        }
        return quietest
    }

    @Test
    fun nothingStagedHandsBackTheBuiltListAndAllocatesNothing() {
        val join = UiPrimitiveJoin()
        assertSame(built, join.join(emptyList(), built))
        assertEquals(0, bytesPerFrame(join, emptyList()))
    }

    @Test
    fun somethingStagedRefillsOneListAndAllocatesNothingOnceWarm() {
        val join = UiPrimitiveJoin()
        val joined = join.join(staged, built)
        assertEquals(staged + built, joined, "staged first, then built")
        assertEquals(0, bytesPerFrame(join, staged))
    }

    private companion object {
        const val BUILT = 5_000
        const val STAGED = 40
        const val WARMUP = 300
        const val WINDOWS = 20
        const val PER_WINDOW = 50
    }
}
