/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** [StableDrawSlots] and [StableMaterialSlots] give a draw the same slot by what it is, and give slots back by their eviction rule. */
class StableDrawSlotsTest {
    private val slots = StableDrawSlots()
    private val meshes = List(8) { Any() }
    private val material = Any()

    private fun batch(vararg drawn: Int): List<Int> {
        slots.begin()
        return drawn.map { slots.slotFor(meshes[it], material) }
    }

    @Test
    fun aDrawCulledEarlierLeavesEveryLaterDrawInItsSlot() {
        val first = batch(0, 1, 2, 3)
        val culled = batch(1, 2, 3)
        assertEquals(first.drop(1), culled)
        val reordered = batch(3, 2, 1)
        assertEquals(first.drop(1).reversed(), reordered)
    }

    @Test
    fun drawsSharingMeshAndMaterialAreToldApartByTheirOrder() {
        val first = batch(0, 0, 0)
        assertEquals(3, first.toSet().size)
        assertEquals(first, batch(0, 0, 0))
    }

    @Test
    fun aKeyThePreviousBatchDidNotDrawGivesItsSlotBackAndTheNextDrawTakesIt() {
        val first = batch(0, 1)
        batch(1)
        assertEquals(2, slots.inUse, "kept through the batch that missed it")
        batch(1)
        assertEquals(1, slots.inUse, "given back when the next batch began")
        val newcomer = batch(1, 5)
        assertEquals(first[0], newcomer[1], "a freed slot is taken before the pool grows")
    }

    @Test
    fun aKeyDrawnFewerTimesGivesBackTheSlotsPastItsCount() {
        batch(0, 0, 0)
        batch(0)
        batch(0)
        assertEquals(1, slots.inUse)
    }

    @Test
    fun slotsInUseStayBoundedOverALongSessionOfChangingDraws() {
        repeat(1_000) { frame -> batch(frame % 8, (frame + 3) % 8) }
        assertTrue(slots.inUse <= 4, "never more than two consecutive batches' draws, was ${slots.inUse}")
        assertNotEquals(0, slots.inUse)
    }

    @Test
    fun perMaterialSlotsStayCompactWithinEachMaterial() {
        val materialSlots = StableMaterialSlots()
        val matA = Any()
        val matB = Any()
        materialSlots.begin()
        val a0 = materialSlots.slotFor(matA, meshes[0], SlotKind.Single)
        val a1 = materialSlots.slotFor(matA, meshes[1], SlotKind.Single)
        val b0 = materialSlots.slotFor(matB, meshes[2], SlotKind.Single)
        assertEquals(listOf(0, 1), listOf(a0, a1))
        assertEquals(0, b0)

        materialSlots.begin()
        assertEquals(a1, materialSlots.slotFor(matA, meshes[1], SlotKind.Single))
        assertEquals(b0, materialSlots.slotFor(matB, meshes[2], SlotKind.Single))
    }
}
