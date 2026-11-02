/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

/**
 * The height of the ground under a point, for [NavDebugSource] to lay its lines on. A `fun interface` rather than a
 * Kotlin function type, so the query and its answer stay primitive and drawing allocates nothing.
 */
fun interface NavGroundHeight {
    /** The ground's height at world (`x`, `z`). Return a number, not `NaN`, off the edge of the map. */
    fun heightAt(x: Float, z: Float): Float

    /** Ground heights every game can use. */
    companion object {
        /** Level ground at `y = 0`. */
        val FLAT: NavGroundHeight = NavGroundHeight { _, _ -> 0f }
    }
}
