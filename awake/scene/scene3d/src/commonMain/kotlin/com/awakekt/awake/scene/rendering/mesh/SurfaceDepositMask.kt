/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

/**
 * Scales and randomizes the scene's dynamic [com.awakekt.awake.scene.rendering.deposit.SurfaceDeposit]
 * on this entity's [MeshRenderer] and modular character slots.
 *
 * - `scale = 0` exempts the entity completely (for example a character's face/eyes, sheltered indoor
 *   meshes, or hot lava/fire surfaces).
 * - `scale = 1` (the default for entities without this component) receives the scene's deposit unchanged.
 * - Values between `0` and `1` (or up to `2`) let individual characters dynamically accumulate,
 *   melt under roofs, or shake off snow and ash independently of the outdoor world, with zero
 *   per-frame allocations.
 * - [seed] shifts the procedural noise coordinates for this entity so distinct characters wearing
 *   identical gear receive unique organic clump patterns.
 *
 * @property scale Per-entity multiplier applied to the frame's surface deposit amount (`0..2`).
 * @property seed Per-entity spatial randomization seed shifting deposit noise coordinates.
 */
class SurfaceDepositMask(var scale: Float = 1f, var seed: Float = 0f)
