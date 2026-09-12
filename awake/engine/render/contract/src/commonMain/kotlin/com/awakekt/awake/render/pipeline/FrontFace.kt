/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

/**
 * The winding order that defines the front-facing side of a triangle.
 *
 * CounterClockwise is the engine default across mesh generators, terrain, and glTF standards.
 */
enum class FrontFace {
    CounterClockwise,
    Clockwise,
}
