/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import io.github.awakelab.awake.core.math.ClipSpace

/**
 * The `0..1` texture coordinate for a `-1..1` clip-space XY, in [clipSpace]'s own convention.
 *
 * **Do not hand-roll this.** `xy * 0.5 + 0.5` is right for a Y-down NDC and mirrored for a Y-up
 * one, and which of those a shader is compiled against is a fact that lives on the CPU, in
 * [ClipSpace], in another module. Two shaders have gone wrong here already: `depth_fog` was fixed
 * with a hand-passed boolean, and `lit_shadow` then sampled every shadow map upside down on WebGPU
 * -- shadows landed on the far side of the scene from what cast them, and nothing failed.
 *
 * X is the same in both conventions; only Y is not.
 *
 * @param ndcXy A `vec2f` in clip space, already divided by w.
 * @param clipSpace The convention this shader is being emitted for -- see
 * `aslShaderSet { clipSpace -> ... }`, which supplies it per backend.
 */
fun ndcToUv(ndcXy: AslExpr, clipSpace: ClipSpace): AslExpr {
    val u = (ndcXy.x + 1f.lit) * 0.5f.lit
    // A Y-flipped projection has already made light/clip-space Y run the way a texture's rows do.
    val v = if (clipSpace.flipY) (ndcXy.y + 1f.lit) * 0.5f.lit else (1f.lit - ndcXy.y) * 0.5f.lit
    return vec2(u, v)
}
