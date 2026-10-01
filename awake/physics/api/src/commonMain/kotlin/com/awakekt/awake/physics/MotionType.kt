/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

/**
 * Motion type for rigid bodies defining simulation behavior.
 *
 * Mirrors Jolt Physics' `EMotionType` -- kept as its own backend-neutral enum rather than
 * re-exporting a native type, since this module has zero native binding dependencies.
 */
enum class MotionType {
    /** Static body that never moves and has infinite mass (e.g. terrain, static structures). */
    STATIC,

    /** Kinematic body driven by explicit velocity or position updates rather than forces (e.g. elevators, moving platforms). */
    KINEMATIC,

    /** Fully simulated dynamic rigid body affected by forces, impulses, gravity, and collisions. */
    DYNAMIC,
}
