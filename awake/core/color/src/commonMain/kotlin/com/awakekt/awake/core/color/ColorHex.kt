/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.color

import kotlin.math.roundToInt

/**
 * This colour as hex text, the form a canvas element, a theme or a prefab saves: `#RRGGBB` when it
 * is opaque and `#RRGGBBAA` otherwise, in upper case. Each channel is clamped to `[0, 1]` and
 * rounded to the nearest of 256 steps, so [Color.fromHex] reads it back to the same text, and a
 * colour written by code diffs cleanly against one an editor wrote.
 *
 * Opaque is an alpha that rounds to 255, so `#FFFFFF` and `#FFFFFFFF` both come back as `#FFFFFF`.
 *
 * @return The colour as `#RRGGBB` or `#RRGGBBAA`.
 */
fun Color.toHex(): String = toHex(includeAlpha = channelByte(a) < MAX_CHANNEL)

/**
 * This colour as hex text: `#RRGGBBAA` when [includeAlpha], else `#RRGGBB` with the alpha dropped.
 * Channels are clamped and rounded as [toHex] does.
 *
 * @param includeAlpha Whether to write the alpha byte.
 * @return The colour as `#RRGGBBAA` or `#RRGGBB`.
 */
fun Color.toHex(includeAlpha: Boolean): String = buildString(if (includeAlpha) HEX_WITH_ALPHA else HEX_OPAQUE) {
    append('#')
    appendChannel(r)
    appendChannel(g)
    appendChannel(b)
    if (includeAlpha) appendChannel(a)
}

private fun StringBuilder.appendChannel(channel: Float) {
    val byte = channelByte(channel)
    append(HEX_DIGITS[byte shr NIBBLE_BITS])
    append(HEX_DIGITS[byte and NIBBLE_MASK])
}

/** NaN, which [coerceIn] passes through, writes as 0 rather than failing. */
private fun channelByte(channel: Float): Int =
    if (channel.isNaN()) 0 else (channel.coerceIn(0f, 1f) * MAX_CHANNEL).roundToInt()

private const val HEX_DIGITS = "0123456789ABCDEF"
private const val MAX_CHANNEL = 255
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0xF
private const val HEX_OPAQUE = 7
private const val HEX_WITH_ALPHA = 9
