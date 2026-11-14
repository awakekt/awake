/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [PassBindings] sends a binding only when the object bound there changed since the last pipeline bind. */
class PassBindingsTest {
    private class Obj(val name: String) {
        override fun equals(other: Any?) = other is Obj && other.name == name
        override fun hashCode() = name.hashCode()
    }

    @Test
    fun aRepeatedBindingIsSentOnce() {
        val bindings = PassBindings()
        val group = Obj("shadow")
        assertTrue(bindings.bindGroup(1, group))
        assertFalse(bindings.bindGroup(1, group))
        assertTrue(bindings.bindVertexBuffer(0, group))
        assertFalse(bindings.bindVertexBuffer(0, group))
        assertTrue(bindings.bindIndexBuffer(group))
        assertFalse(bindings.bindIndexBuffer(group))
    }

    @Test
    fun anEqualButDistinctObjectIsSent() {
        val bindings = PassBindings()
        assertTrue(bindings.bindGroup(0, Obj("a")))
        assertTrue(bindings.bindGroup(0, Obj("a")), "identity, not equality, decides")
    }

    @Test
    fun slotsAndGroupsAreTrackedApart() {
        val bindings = PassBindings()
        val buffer = Obj("mesh")
        assertTrue(bindings.bindVertexBuffer(0, buffer))
        assertTrue(bindings.bindVertexBuffer(1, buffer))
        assertTrue(bindings.bindGroup(0, buffer))
        assertTrue(bindings.bindGroup(2, buffer))
        assertFalse(bindings.bindVertexBuffer(1, buffer))
    }

    @Test
    fun aPipelineBindForgetsEverything() {
        val bindings = PassBindings()
        val value = Obj("x")
        bindings.bindGroup(0, value)
        bindings.bindVertexBuffer(0, value)
        bindings.bindIndexBuffer(value)
        bindings.forget()
        assertTrue(bindings.bindGroup(0, value))
        assertTrue(bindings.bindVertexBuffer(0, value))
        assertTrue(bindings.bindIndexBuffer(value))
    }

    @Test
    fun aSlotPastTheGuaranteedLimitsIsNeverElided() {
        val bindings = PassBindings()
        val value = Obj("x")
        repeat(2) { assertTrue(bindings.bindGroup(4, value)) }
        repeat(2) { assertTrue(bindings.bindVertexBuffer(8, value)) }
    }

    /**
     * The gate: the shape #1545 measured, static draws over a few shared meshes under one shadow
     * group, each draw binding its mesh, its own material and the shadow group. Only material
     * changes and mesh switches are sent.
     */
    @Test
    fun staticDrawsOverSharedMeshesSendOnlyWhatChanged() {
        val bindings = PassBindings()
        val meshes = List(MESHES) { Obj("mesh$it") }
        val shadow = Obj("shadow")
        var sent = 0
        var calls = 0
        fun count(send: Boolean) { calls++; if (send) sent++ }
        repeat(DRAWS) { draw ->
            val mesh = meshes[draw * MESHES / DRAWS]
            count(bindings.bindVertexBuffer(0, mesh))
            count(bindings.bindGroup(0, Obj("material$draw")))
            count(bindings.bindGroup(1, shadow))
            count(bindings.bindIndexBuffer(mesh))
        }
        assertEquals(DRAWS * 4, calls)
        assertEquals(DRAWS + 1 + MESHES * 2, sent)
    }

    private companion object {
        const val DRAWS = 1_500
        const val MESHES = 40
    }
}
