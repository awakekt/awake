/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.jvm.JvmInline

/**
 * Represents the numerical weight (thickness) of a typeface font, typically ranging from 100 to 900.
 *
 * @property value The integer weight value conforming to standard OpenType weight specifications.
 */
@JvmInline
value class FontWeight(val value: Int) {
    /**
     * Standard predefined font weight constants.
     */
    companion object {
        /** Thin font weight (100). */
        val Thin = FontWeight(100)

        /** Extra-light font weight (200). */
        val ExtraLight = FontWeight(200)

        /** Light font weight (300). */
        val Light = FontWeight(300)

        /** Normal or regular font weight (400). */
        val Normal = FontWeight(400)

        /** Medium font weight (500). */
        val Medium = FontWeight(500)

        /** Semi-bold font weight (600). */
        val SemiBold = FontWeight(600)

        /** Bold font weight (700). */
        val Bold = FontWeight(700)

        /** Extra-bold font weight (800). */
        val ExtraBold = FontWeight(800)

        /** Black or heavy font weight (900). */
        val Black = FontWeight(900)
    }
}
