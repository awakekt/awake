/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.LinearGradient
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.math.ceil

/** The path CircleShape emits, clipping a quad larger than the circle, at a fractional origin. */
fun circleClipScenario(diameter: Float): UiScenario {
    val inset = 13.25f
    val size = ceil(diameter + inset * 2f).toInt()
    val bounds = Rectangle(inset, inset, diameter, diameter)
    return UiScenario(
        name = "circle-clip-$diameter",
        font = null,
        primitives = listOf(
            UiDrawPrimitive.ClipPathPush(DrawShape.Circle.toPath(bounds), bounds),
            UiDrawPrimitive.Quad(0f, 0f, size.toFloat(), size.toFloat(), Color.White),
            UiDrawPrimitive.ClipPop(Rectangle(0f, 0f, size.toFloat(), size.toFloat())),
        ),
        size = size,
    )
}

/** A blurred circular shadow, with distinct corner colours to expose gradient interpolation. */
fun roundShadowScenario(gradient: Boolean): UiScenario = UiScenario(
    name = "round-shadow-$gradient",
    font = null,
    size = 96,
    primitives = listOf(
        UiDrawPrimitive.ShadowQuad(
            16f, 16f, 64f, 64f, 32f, 0f, 0f, 8f, 0f, Color.White,
            gradient = if (gradient) {
                LinearGradient(
                    Color.White,
                    Color(1f, 0f, 0f, 1f),
                    Color(0f, 1f, 0f, 1f),
                    Color(0f, 0f, 1f, 1f),
                )
            } else {
                null
            },
        ),
    ),
)
