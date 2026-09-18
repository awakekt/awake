/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

import com.awakekt.awake.core.math.ClipSpace

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

/**
 * Unprojects a screen-space normalized device coordinate [ndcXy] to a normalized world-space ray direction.
 *
 * Emits the far-plane point at NDC z = 1.0, transforms it through [inverseViewProjection], applies the
 * perspective divide by w, and subtracts [cameraEye], normalizing the resulting ray.
 *
 * Because [inverseViewProjection] is the exact algebraic inverse of the active backend's projection matrix,
 * the resulting world-space ray is completely agnostic to backend-specific Y-inversion (Vulkan flipY)
 * or depth range conventions.
 *
 * @param inverseViewProjection A `mat4x4<f32>` expression inverting the scene's view-projection matrix.
 * @param cameraEye A `vec3<f32>` or `vec4<f32>` expression representing the world-space camera position.
 * @param ndcXy A `vec2<f32>` expression representing screen-space NDC coordinates in `[-1, 1]`.
 */
fun unprojectFarRay(
    inverseViewProjection: AslExpr,
    cameraEye: AslExpr,
    ndcXy: AslExpr,
): AslExpr {
    val far = inverseViewProjection * vec4(ndcXy, 1f.lit, 1f.lit)
    return normalize(far.xyz / far.w - cameraEye.xyz)
}

/**
 * Unprojects a screen-space normalized device coordinate [ndcXy] and depth sample [depth] to a world-space position.
 *
 * Transforms the NDC coordinate through [inverseViewProjection] and applies the perspective divide by w.
 *
 * @param inverseViewProjection A `mat4x4<f32>` expression inverting the scene's view-projection matrix.
 * @param ndcXy A `vec2<f32>` expression representing screen-space NDC coordinates in `[-1, 1]`.
 * @param depth A `f32` expression representing the fragment or sampled buffer depth (default 1.0 on far plane).
 */
fun unprojectClipToWorld(
    inverseViewProjection: AslExpr,
    ndcXy: AslExpr,
    depth: AslExpr = 1f.lit,
): AslExpr {
    val clip = inverseViewProjection * vec4(ndcXy, depth, 1f.lit)
    return clip.xyz / clip.w
}
