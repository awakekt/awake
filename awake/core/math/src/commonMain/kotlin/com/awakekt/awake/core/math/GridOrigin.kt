/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

/**
 * Where sample `(0, 0)` of a regular grid sits relative to the grid's own origin.
 *
 * [Centered] is what every other mesh does -- `generate { cube() }` and `plane()` both straddle
 * the origin -- and it is what makes a `Transform.position` mean where the thing is, which LOD
 * distance, culling spheres, streaming and collider placement all read.
 *
 * [Corner] exists for content authored against the older convention, where the grid grew into
 * +X/+Z from its position. It is not a per-consumer setting: whichever a grid declares, the mesh
 * built from it, the heights sampled from it, the navigation baked from it and the collider
 * shaped from it all follow, because a disagreement between any two of those is a half-map error
 * that looks like drift rather than like a wrong flag.
 */
enum class GridOrigin {
    /** The grid straddles its origin, spanning `-extent/2 .. +extent/2`. */
    Centered,

    /** The grid starts at its origin, spanning `0 .. extent`. */
    Corner,
}
