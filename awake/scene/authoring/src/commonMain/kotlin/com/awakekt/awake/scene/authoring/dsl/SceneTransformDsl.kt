/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring.dsl

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Configures the [Transform] component of this entity.
 *
 * @param x The X position offset.
 * @param y The Y position offset.
 * @param z The Z position offset.
 * @param sx The X scale factor.
 * @param sy The Y scale factor.
 * @param sz The Z scale factor.
 * @param rx The X rotation angle in radians.
 * @param ry The Y rotation angle in radians.
 * @param rz The Z rotation angle in radians.
 */
fun EntityScope.transform(
    x: Float = 0f,
    y: Float = 0f,
    z: Float = 0f,
    sx: Float = 1f,
    sy: Float = 1f,
    sz: Float = 1f,
    rx: Float = 0f,
    ry: Float = 0f,
    rz: Float = 0f,
) = configure(::Transform) {
    position.set(x, y, z)
    scale.set(sx, sy, sz)
    rotation.set(rx, ry, rz)
}

/**
 * Configures the [Transform] position vector of this entity.
 *
 * @param position The position vector.
 */
fun EntityScope.transform(
    position: Vec3f,
) = configure(::Transform) {
    this.position.set(position)
}
