/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

/**
 * Fades this entity's opaque meshes by a screen-space dither: below `1`, the shader discards the
 * rest of [opacity]'s share of their pixels in a fixed ordered pattern, so they read as see-through
 * while they stay opaque. They need no depth sorting and cast their shadows as before. `1` draws them
 * exactly as an entity without it.
 *
 * It fades the entity's own [MeshRenderer] and modular character slots. Something that fades a whole
 * model sets one on each entity under it.
 *
 * @property opacity How much of the mesh shows, from `0` to `1`.
 */
class DitherFade(var opacity: Float = 1f)
