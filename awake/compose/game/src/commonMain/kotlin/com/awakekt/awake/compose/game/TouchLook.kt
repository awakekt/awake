/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.core.color.Color

/**
 * How a touch control looks: a flat colour, or an [ImageFill] over it for a skinned base and knob.
 * Nothing here is a second theme; a game passes its own fills.
 *
 * @property base The pad's fill, or null for [baseColor].
 * @property knob The knob's fill, or null for [knobColor].
 * @property baseColor The pad's flat colour when [base] is null.
 * @property knobColor The knob's flat colour when [knob] is null.
 */
class TouchLook(
    val base: ImageFill? = null,
    val knob: ImageFill? = null,
    val baseColor: Color = Color(1f, 1f, 1f, 0.18f),
    val knobColor: Color = Color(1f, 1f, 1f, 0.55f),
)
