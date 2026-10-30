/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.passes.sprites.SpriteDrawInput

/**
 * Appends one [SpriteDrawInput] quad per live particle of this emitter to [into], in slot order, on
 * [sortLayer] (the rank of [ParticleEmitter2D.sortingLayer] among the caller's layers) at
 * [ParticleEmitter2D.sortOrder]. Appends nothing while the emitter is disabled or has no live
 * particle.
 *
 * Where a particle is drawn:
 * - a world-space emitter's particle is at its simulated `(x, y)`, at depth [depth];
 * - a [ParticleEmitter2D.localSpace] emitter's particle is at its simulated `(x, y)` under [parent],
 *   its entity's world matrix, or at depth [depth] when [parent] is null;
 * - a [BurstEmitter2DPool] burst is at its simulated `(x, y)` at the depth it was fired at, and
 *   ignores [parent] and [depth].
 *
 * Each quad's model matrix is the emitter's own for that slot, rewritten on the next call, so a
 * draw list is valid until this emitter is drawn again. A slot's tint is reused while it does not
 * change. The [SpriteDrawInput] itself, its boxed `worldSize`, and a tint that changes (a fade or an
 * [ParticleEmitter2D.endTint]) are still allocated per particle per draw, as they are for sprites;
 * stepping, the hooks and [BurstEmitter2DPool.spawn] allocate nothing.
 */
fun ParticleEmitter2D.appendSpriteDraws(
    into: MutableList<SpriteDrawInput>,
    sortLayer: Int,
    parent: Mat4? = null,
    depth: Float = 0f,
) {
    val sim = simulation
    if (!enabled || sim.liveParticleCount == 0) return
    val underParent = !pinned && localSpace && parent != null
    val z = if (pinned) pinnedZ else depth
    for (slot in 0 until maxParticles) {
        if (!sim.isAlive(slot)) continue
        val model = slotModels[slot]
        if (underParent && parent != null) {
            localScratch.setEulerTRS(sim.posX[slot], sim.posY[slot], 0f, 0f, 0f, sim.rotation[slot], 1f, 1f, 1f)
            Mat4.multiplyColumnMajor(parent, localScratch, model)
        } else {
            model.setEulerTRS(sim.posX[slot], sim.posY[slot], z, 0f, 0f, sim.rotation[slot], 1f, 1f, 1f)
        }
        into.add(
            SpriteDrawInput(
                texture = texture,
                model = model,
                columns = columns,
                rows = rows,
                frame = sim.frameAt(slot),
                tint = tintAt(slot),
                sortOrder = sortOrder,
                sortLayer = sortLayer,
                worldSize = sim.scaleAt(slot),
                additive = additive,
            ),
        )
    }
}

/**
 * [slot]'s straight-alpha tint now: [ParticleEmitter2D.tint], blended toward
 * [ParticleEmitter2D.endTint] over its life when set, with its alpha scaled by the simulation's
 * alpha. The slot's last tint is returned again while it is unchanged.
 */
internal fun ParticleEmitter2D.tintAt(slot: Int): Color {
    val sim = simulation
    val start = tint
    val end = endTint
    val alpha = sim.alphaAt(slot)
    val r: Float
    val g: Float
    val b: Float
    val a: Float
    if (end == null) {
        r = start.r
        g = start.g
        b = start.b
        a = start.a * alpha
    } else {
        val fraction = sim.lifeFractionAt(slot)
        r = start.r + (end.r - start.r) * fraction
        g = start.g + (end.g - start.g) * fraction
        b = start.b + (end.b - start.b) * fraction
        a = (start.a + (end.a - start.a) * fraction) * alpha
    }
    val last = slotTints[slot]
    if (last != null && last.matches(r, g, b, a)) return last
    return Color(r, g, b, a).also { slotTints[slot] = it }
}

private fun Color.matches(red: Float, green: Float, blue: Float, alpha: Float): Boolean =
    r == red && g == green && b == blue && a == alpha
