// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.renderer

import io.github.ronjunevaldoz.awake.core.math.Vec3f

/**
 * One positional light. [color] is already intensity-multiplied, matching [SceneLight.color].
 *
 * [range] is where the light reaches zero, in world units. Attenuation is inverse-square
 * windowed to that radius, so a light contributes nothing past it -- a hard cutoff rather than
 * an asymptote, because the shader iterates a fixed slot count and a light that never quite
 * ends can never be culled out of one.
 */
data class PointLight(
    val position: Vec3f,
    val color: Vec3f,
    val range: Float,
)

/**
 * Scene-wide lighting for [Renderer.draw]'s lit pass.
 *
 * [direction] is the direction the sun shines FROM and [color] is already intensity-multiplied
 * (one multiply in the shader, not a separate intensity uniform). Backend-neutral; `RenderSystem`
 * builds one from the scene's `Light` entities, which can depend on this module where the reverse
 * would be a cycle.
 *
 * [points] is **borrowed for the frame, not owned**: `RenderSystem` refills one list each frame
 * rather than allocating, so a `SceneLight` retained past `Renderer.draw` sees the next frame's
 * lights. Same contract `drawCalls` already has, and for the same reason -- this is the render
 * path, where `skills/awake-core-math` rules out per-frame allocation. Copy it if you need it to
 * outlive the call.
 *
 * [points] is capped at [MAX_POINT_LIGHTS] by the uniform block, which reserves a fixed number of
 * slots rather than a variable-length buffer -- the lit shaders are a single uniform bind with no
 * storage-buffer path on either backend. Extra lights past the cap are dropped by
 * `pointLightFloats`, nearest-first, not silently reordered.
 */
data class SceneLight(
    val direction: Vec3f,
    val color: Vec3f,
    val points: List<PointLight> = emptyList(),
)

/** Point-light slots the lit shaders declare. Raising this changes both shaders and the two
 * uniform layouts together -- the count is baked into the block's float total. */
const val MAX_POINT_LIGHTS = 4
