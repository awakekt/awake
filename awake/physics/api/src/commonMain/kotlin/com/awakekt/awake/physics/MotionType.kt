/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics

/** Mirrors Jolt Physics' `EMotionType` (STATIC never moves, KINEMATIC is driven by
 * explicit position/velocity sets rather than forces, DYNAMIC is simulated) -- kept as its
 * own backend-neutral enum rather than re-exporting a jolt-jni type, since this module has
 * zero jolt-jni (or any native binding) dependency at all. */
enum class MotionType { STATIC, KINEMATIC, DYNAMIC }
