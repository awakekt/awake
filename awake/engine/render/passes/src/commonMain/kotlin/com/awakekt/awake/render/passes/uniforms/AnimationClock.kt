/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.core.math.Vec3f

/*
 * The clock a draw's uniform block carries, or 0 for a draw nothing in its shader moves with.
 *
 * Every lit and textured block has a time slot, and writing the frame's clock there into a draw
 * that does not animate changed its block every frame: a static prop's uniforms never matched the
 * frame before, so they were re-uploaded every frame though nothing moved (#1452). The shaders
 * only ever multiply the clock by the animation's own rates, so a still draw renders exactly the
 * same with 0 there.
 */

/**
 * [timeSeconds] for a draw whose vertex wave moves it, and 0 otherwise.
 *
 * The wave displaces by `(sin + cos) * vertexAnimation.x`, so an amplitude of 0 displaces nothing
 * whatever the phase, and the animated normal is only selected for an amplitude above 0.
 */
internal fun vertexAnimationClock(vertexAnimation: Vec3f, timeSeconds: Float): Float =
    if (vertexAnimation.x != 0f) timeSeconds else 0f

/**
 * [timeSeconds] for a textured draw whose texture animation moves with it, and 0 otherwise.
 *
 * The shader scrolls the image by `scroll.xy * time` and plays `floor(time * frames.z)` frames of
 * a `frames.x` by `frames.y` sheet, which it reads only when the sheet has more than one cell. With
 * no scroll, and no frame rate or a single-cell sheet, the clock reaches no sample.
 */
@Suppress("LongParameterList") // The two vec4s, element by element, so a still draw allocates nothing.
internal fun textureAnimationClock(
    sheetX: Float,
    sheetY: Float,
    frameRate: Float,
    scrollX: Float,
    scrollY: Float,
    timeSeconds: Float,
): Float {
    val scrolls = scrollX != 0f || scrollY != 0f
    val playsFrames = frameRate != 0f && sheetX * sheetY > SINGLE_CELL
    return if (scrolls || playsFrames) timeSeconds else 0f
}

/** The shader's own test for a sheet of more than one cell: `sheet.x * sheet.y > 1.5`. */
private const val SINGLE_CELL = 1.5f
