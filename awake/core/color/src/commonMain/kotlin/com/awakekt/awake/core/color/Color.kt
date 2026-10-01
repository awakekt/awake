/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.color

/**
 * Represents an RGBA color with normalized floating-point channels in the range `[0.0, 1.0]`.
 *
 * @property r The red component fraction from `0.0` to `1.0`.
 * @property g The green component fraction from `0.0` to `1.0`.
 * @property b The blue component fraction from `0.0` to `1.0`.
 * @property a The alpha opacity fraction from `0.0` (fully transparent) to `1.0` (fully opaque).
 */
data class Color(val r: Float = 0f, val g: Float = 0f, val b: Float = 0f, val a: Float = 1f) {
    /**
     * Retrieves the color channel component by its zero-based index.
     *
     * @param index The channel index: `0` for red, `1` for green, `2` for blue, or `3` for alpha.
     * @return The floating-point value of the selected channel.
     * @throws IndexOutOfBoundsException If [index] is not within the range `[0, 3]`.
     */
    operator fun get(index: Int): Float = when (index) {
        0 -> r
        1 -> g
        2 -> b
        3 -> a
        else -> throw IndexOutOfBoundsException("Color channel index $index is out of bounds.")
    }

    /**
     * Creates a copy of this color with the specified alpha transparency.
     *
     * @param alpha The new alpha value, clamped to `[0.0, 1.0]`.
     * @return A new [Color] instance with the updated alpha component.
     */
    fun withAlpha(alpha: Float): Color = copy(a = alpha.coerceIn(0f, 1f))

    /**
     * Multiplies the RGB color channels by a scalar brightness factor.
     *
     * @param multiplier The brightness scaling multiplier.
     * @return A new [Color] instance with scaled and clamped RGB channels.
     */
    fun brighten(multiplier: Float): Color = Color(
        r = (r * multiplier).coerceAtMost(1f),
        g = (g * multiplier).coerceAtMost(1f),
        b = (b * multiplier).coerceAtMost(1f),
        a = a,
    )

    /**
     * Checks whether this color is completely transparent.
     *
     * @return `true` if the alpha channel is less than or equal to `0.0`, `false` otherwise.
     */
    fun isTransparent(): Boolean = a <= 0f

    /**
     * Linearly interpolates between this color and [other] by the given [fraction].
     *
     * @param other The target color to interpolate towards.
     * @param fraction The interpolation factor, clamped to `[0.0, 1.0]`.
     * @return The interpolated [Color].
     */
    fun lerp(other: Color, fraction: Float): Color = Color(
        r = r + (other.r - r) * fraction.coerceIn(0f, 1f),
        g = g + (other.g - g) * fraction.coerceIn(0f, 1f),
        b = b + (other.b - b) * fraction.coerceIn(0f, 1f),
        a = a + (other.a - a) * fraction.coerceIn(0f, 1f),
    )

    /**
     * Converts this color into a four-element float array containing `[r, g, b, a]`.
     *
     * @return A newly allocated [FloatArray] with four normalized channel values.
     */
    fun toFloatArray(): FloatArray = floatArrayOf(r, g, b, a)

    /** Factory methods and predefined color constants. */
    companion object {
        /** Fully transparent black color (`#00000000`). */
        val Transparent = Color(0f, 0f, 0f, 0f)

        /** Opaque black color (`#000000FF`). */
        val Black = Color(0f, 0f, 0f, 1f)

        /** Opaque white color (`#FFFFFFFF`). */
        val White = Color(1f, 1f, 1f, 1f)

        /**
         * Constructs a color from an array of channel values.
         *
         * @param channels A float array where indices `0` to `3` map to `[r, g, b, a]`.
         * @return A new [Color] constructed from the provided channel array.
         */
        fun fromChannels(channels: FloatArray): Color = Color(
            r = channels.getOrElse(0) { 0f },
            g = channels.getOrElse(1) { 0f },
            b = channels.getOrElse(2) { 0f },
            a = channels.getOrElse(3) { 1f },
        )

        /**
         * Constructs an opaque or alpha-blended color from a packed `0xRRGGBB` RGB integer literal.
         *
         * An overload rather than a separate `hex()` helper: two spellings of one idea is how a
         * codebase ends up with two implementations of it, and the string form already lives here.
         *
         * @param rgb The packed 24-bit RGB integer literal (`0xRRGGBB`).
         * @param alpha The alpha opacity fraction from `0.0` to `1.0`. Defaults to `1.0`.
         * @return A new [Color] initialized from the RGB integer and alpha value.
         */
        fun fromHex(rgb: Int, alpha: Float = 1f): Color = Color(
            r = ((rgb shr 16) and 0xFF) / 255f,
            g = ((rgb shr 8) and 0xFF) / 255f,
            b = (rgb and 0xFF) / 255f,
            a = alpha,
        )

        /**
         * Parses a hexadecimal color string in `#RRGGBB` or `#RRGGBBAA` format.
         *
         * @param hex The hex string with or without a leading `#`.
         * @return The parsed [Color].
         * @throws IllegalArgumentException If [hex] is not a valid 6-character or 8-character hex string.
         */
        fun fromHex(hex: String): Color {
            val h = hex.removePrefix("#")
            return when (h.length) {
                6 -> {
                    val r = h.substring(0, 2).toInt(16) / 255f
                    val g = h.substring(2, 4).toInt(16) / 255f
                    val b = h.substring(4, 6).toInt(16) / 255f
                    Color(r, g, b, 1f)
                }
                8 -> {
                    val r = h.substring(0, 2).toInt(16) / 255f
                    val g = h.substring(2, 4).toInt(16) / 255f
                    val b = h.substring(4, 6).toInt(16) / 255f
                    val a = h.substring(6, 8).toInt(16) / 255f
                    Color(r, g, b, a)
                }
                else -> throw IllegalArgumentException("Invalid hex color: $hex")
            }
        }
    }
}
