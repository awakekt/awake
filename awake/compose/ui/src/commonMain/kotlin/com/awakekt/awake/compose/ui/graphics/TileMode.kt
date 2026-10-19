/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import kotlin.jvm.JvmInline

/**
 * Defines how edges are sampled when a shader or render effect reads outside its source bounds.
 *
 * @property value Internal numeric representation matching standard tile mode indices.
 */
@JvmInline
value class TileMode internal constructor(val value: Int) {
    override fun toString(): String = when (this) {
        Clamp -> "Clamp"
        Repeated -> "Repeated"
        Mirror -> "Mirror"
        Decal -> "Decal"
        else -> "Unknown"
    }

    /** Predefined [TileMode] values. */
    companion object {
        /** Replicate edge texels. */
        val Clamp: TileMode = TileMode(0)

        /** Repeat the image horizontally and vertically. */
        val Repeated: TileMode = TileMode(1)

        /** Repeat the image horizontally and vertically, alternating mirror reflection. */
        val Mirror: TileMode = TileMode(2)

        /** Render transparent black (zero alpha) outside the source bounds. */
        val Decal: TileMode = TileMode(3)
    }
}
