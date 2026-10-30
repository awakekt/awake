/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlin.random.Random

private const val DEFAULT_CANVAS_BURST_SLOTS: Int = 16
private const val DEFAULT_CANVAS_BURST_PARTICLES: Int = 32
private const val DEFAULT_CANVAS_BURST_ID_BASE: Int = 900_000

/**
 * Fixed-capacity resident pool of one-shot [CanvasParticles] burst slots in logical canvas `dp`
 * units for tap ripples, coin/pickup pops, button sparks, and HUD impacts with zero per-burst heap
 * allocations.
 *
 * Pre-allocates [maxSlots] hidden [CanvasItem] entries in [items] at construction. Pass [items]
 * alongside your overlay items (for example `baseItems + burstPool.items`); idle slots remain
 * `visible = false` so [CanvasOverlay] skips composing them, and active slots automatically hide and
 * recycle once their particles finish aging out.
 *
 * **Overflow policy (skip and count):** when all [maxSlots] slots are currently active, [spawn]
 * skips the burst without evicting an in-flight effect or allocating a new slot, increments
 * [droppedBurstCount], and returns `false`.
 *
 * @property maxSlots Number of pre-allocated burst slots (`> 0`).
 * @property slotMaxParticles Particle pool capacity of each slot (`> 0`).
 * @property firstItemId Starting `CanvasItem.id` assigned to [items] (`firstItemId + slotIndex`).
 * @param randomFactory Supplies the [Random] instance for each slot constructed at initialization.
 */
class CanvasBurstPool(
    val maxSlots: Int = DEFAULT_CANVAS_BURST_SLOTS,
    val slotMaxParticles: Int = DEFAULT_CANVAS_BURST_PARTICLES,
    val firstItemId: Int = DEFAULT_CANVAS_BURST_ID_BASE,
    randomFactory: (slotIndex: Int) -> Random = { Random.Default },
) {
    init {
        require(maxSlots > 0) { "maxSlots must be positive, got $maxSlots" }
        require(slotMaxParticles > 0) { "slotMaxParticles must be positive, got $slotMaxParticles" }
    }

    private val slotElements = Array(maxSlots) {
        CanvasElement().apply {
            kind = CanvasElementKind.Panel
            anchor = CanvasAnchor.TopLeft
            width = 0f
            height = 0f
            safeArea = false
            visible = false
        }
    }

    private val slotEmitters = Array(maxSlots) { index ->
        CanvasParticles(maxParticles = slotMaxParticles, random = randomFactory(index)).also { emitter ->
            emitter.enabled = false
            emitter.pooledForBurst = true
            emitter.autoHideOwnerOnBurstComplete = true
            slotElements[index].particles = emitter
        }
    }

    /** Pre-allocated [CanvasItem] list to include in [CanvasOverlay]'s `items`. */
    val items: List<CanvasItem> = List(maxSlots) { index ->
        CanvasItem(id = firstItemId + index, element = slotElements[index])
    }

    private var nextCursor: Int = 0

    /** Cumulative count of [spawn] calls skipped because all [maxSlots] slots were active. */
    var droppedBurstCount: Long = 0L
        private set

    /** Number of slots currently active (`visible && enabled && !simulation.isComplete`). */
    val activeSlotCount: Int
        get() {
            var count = 0
            for (i in 0 until maxSlots) {
                if (isSlotActive(i)) count += 1
            }
            return count
        }

    /**
     * Fires a one-shot burst of [count] particles at `(xDp, yDp)` from the canvas top-left corner,
     * drawn at [order], using [configure] to customize the recycled [CanvasParticles] in place with
     * zero heap allocations.
     *
     * Returns `true` when a free slot was acquired, or `false` (incrementing [droppedBurstCount])
     * when all [maxSlots] slots are currently active.
     */
    fun spawn(
        xDp: Float,
        yDp: Float,
        count: Int = slotMaxParticles,
        order: Int = 0,
        configure: (CanvasParticles) -> Unit = {},
    ): Boolean {
        val slot = acquireFreeSlot()
        if (slot < 0) {
            droppedBurstCount += 1L
            return false
        }
        val element = slotElements[slot]
        val emitter = slotEmitters[slot]
        element.offsetX = xDp
        element.offsetY = yDp
        element.order = order
        element.placedBounds = null
        emitter.resetDefaults()
        configure(emitter)
        return launchSlot(element, emitter, xDp, yDp, count)
    }

    /** Clears all active bursts and hides every resident slot. */
    fun reset() {
        for (i in 0 until maxSlots) {
            slotEmitters[i].reset()
            slotEmitters[i].enabled = false
            slotElements[i].visible = false
        }
        nextCursor = 0
    }

    /**
     * Steps all active burst slots by [deltaSeconds] outside [CanvasOverlay] (for headless or unit
     * tests) without allocating an iterator.
     */
    fun step(deltaSeconds: Float) {
        for (i in 0 until maxSlots) {
            if (slotElements[i].visible) {
                slotEmitters[i].step(deltaSeconds, 0f, 0f)
            }
        }
    }

    private fun launchSlot(
        element: CanvasElement,
        emitter: CanvasParticles,
        xDp: Float,
        yDp: Float,
        count: Int,
    ): Boolean {
        val sim = emitter.simulation
        sim.reset()
        emitter.lastFrameStamp = 0L
        emitter.spawnRate = 0f
        emitter.burstCount = null
        emitter.burstCycle = null
        emitter.syncToSimulation(0f, 0f)
        if (emitter.localSpace) {
            sim.setOrigin(0f, 0f, 0f)
        } else {
            sim.setOrigin(xDp + emitter.offsetX, yDp + emitter.offsetY, 0f)
        }
        sim.burstLimit = 0
        val spawned = sim.emit(count)
        emitter.enabled = spawned > 0
        element.visible = spawned > 0
        return true
    }

    private fun acquireFreeSlot(): Int {
        for (offset in 0 until maxSlots) {
            val idx = (nextCursor + offset) % maxSlots
            if (!isSlotActive(idx)) {
                nextCursor = (idx + 1) % maxSlots
                return idx
            }
        }
        return -1
    }

    private fun isSlotActive(slot: Int): Boolean {
        val element = slotElements[slot]
        val emitter = slotEmitters[slot]
        return element.visible && emitter.enabled && !emitter.simulation.isComplete
    }
}
