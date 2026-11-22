/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f

/**
 * One flare element placed along the screen line from the sun (`position = 0`) through the
 * screen centre (`position = 1`) and beyond (`position > 1`).
 *
 * @property position Placement along the sun-to-centre axis: `0` sits on the sun, `1` at the
 *   screen centre, `2` opposite the sun across the centre.
 * @property size Radius in NDC units (aspect-corrected so elements stay circular).
 * @property color RGB tint of the element.
 * @property intensity Brightness multiplier of this element.
 * @property ring When true (and no sprite texture is bound), draws a soft optical halo ring
 *   instead of a filled glow disc.
 */
data class SunFlareElement(
    val position: Float,
    val size: Float,
    val color: Color = Color.White,
    val intensity: Float = 1f,
    val ring: Boolean = false,
)

/**
 * How the sun flare looks this frame, read every frame so a scene can update sun direction,
 * visibility, or intensity without rebuilding the pipeline (#1537).
 *
 * @property enabled Whether the flare draws this frame.
 * @property intensity Overall brightness multiplier (`0` hides the flare).
 * @property sunVisibility Sun visibility from `0` (hidden / night) to `1` (full day).
 * @property followsDirection When true, uses [sunDirection]; when false, reads the pass's light direction.
 * @property elements Ordered flare elements (up to [MAX_SUN_FLARE_ELEMENTS]) placed along the sun-to-centre axis.
 */
class SunFlareLook(
    var enabled: Boolean = true,
    var intensity: Float = 1f,
    var sunVisibility: Float = 1f,
    var followsDirection: Boolean = false,
    var elements: List<SunFlareElement> = DEFAULT_ELEMENTS,
) {
    /** Direction toward the sun while [followsDirection] is true. */
    val sunDirection: Vec3f = Vec3f(0f, 1f, 0f)

    /** Constants and default optical flare preset. */
    companion object {
        /** Default warm solar halo and four subtle optical ghosts along the screen axis. */
        val DEFAULT_ELEMENTS: List<SunFlareElement> = listOf(
            SunFlareElement(position = 0.0f, size = 0.28f, color = Color(1.0f, 0.94f, 0.78f), intensity = 0.65f),
            SunFlareElement(position = 0.0f, size = 0.14f, color = Color(1.0f, 0.98f, 0.90f), intensity = 0.90f),
            SunFlareElement(position = 0.45f, size = 0.11f, color = Color(1.0f, 0.85f, 0.55f), intensity = 0.24f, ring = true),
            SunFlareElement(position = 0.85f, size = 0.06f, color = Color(0.85f, 0.95f, 1.0f), intensity = 0.20f),
            SunFlareElement(position = 1.35f, size = 0.14f, color = Color(0.75f, 0.90f, 1.0f), intensity = 0.18f, ring = true),
            SunFlareElement(position = 1.75f, size = 0.08f, color = Color(1.0f, 0.78f, 0.55f), intensity = 0.22f),
        )
    }
}
