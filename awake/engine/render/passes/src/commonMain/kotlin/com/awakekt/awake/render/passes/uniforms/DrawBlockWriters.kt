/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.uniforms

import com.awakekt.awake.render.renderer.SkinnedDrawLayout
import com.awakekt.awake.render.renderer.UniformWriter

/**
 * The writers a renderer packs draws' own blocks with, kept from frame to frame.
 *
 * `drawBlockFloats` without them builds a [UniformWriter], and so a new array, for every draw it
 * packs, and a textured or lit-shadow draw a material payload array besides: per draw, per frame,
 * garbage a populated scene paid for on every frame the draw moved. Passing one of these instead
 * refills arrays it already holds.
 *
 * The array a pack returns belongs to these writers: it is overwritten by the next pack of a draw
 * with the same layout, so the caller copies or uploads it before packing another draw, as both
 * backends' uniform stages do. Keep one per renderer and use it from the thread that renders.
 */
class DrawBlockWriters {
    internal val particle = UniformWriter(ParticleDrawLayout)
    internal val pbrTextured = UniformWriter(MaterialUniformLayouts.PbrTexturedDraw)
    internal val lit = UniformWriter(MaterialUniformLayouts.LitDraw)
    internal val litShadow = UniformWriter(MaterialUniformLayouts.LitShadowDraw)
    internal val skinned = UniformWriter(SkinnedDrawLayout)
    internal val sprite = UniformWriter(SpriteDrawLayout)
    internal val worldText = UniformWriter(WorldTextDrawLayout)
    internal val trail = UniformWriter(TrailDrawLayout)

    /** A textured draw's material payload, refilled per draw instead of copied out of its floats. */
    internal val texturedPayload = FloatArray(MaterialUniformLayouts.PbrTexturedMaterial.total)

    /** A lit-shadow draw's material payload, likewise. */
    internal val pbrPayload = FloatArray(MaterialUniformLayouts.PbrMaterial.total)
}

