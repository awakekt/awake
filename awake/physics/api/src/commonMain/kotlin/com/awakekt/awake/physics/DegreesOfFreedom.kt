/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

/** Allowed motion in world axes, fixed when a body is created. Independent of its collision shape. */
enum class DegreesOfFreedom {
    /** Translation and rotation about all three axes. */
    ALL,

    /**
     * Translation on X/Y and rotation about Z only, for a 2D simulation in the XY plane.
     * Preserves the body's initial Z and orientation; it does not flatten its 3D collision shape.
     * [PhysicsWorld.shiftOrigin] can still translate the plane with the rest of the world.
     */
    PLANE_2D,
}
