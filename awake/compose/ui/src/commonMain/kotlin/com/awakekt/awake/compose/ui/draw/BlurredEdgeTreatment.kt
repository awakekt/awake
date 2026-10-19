/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.draw

import com.awakekt.awake.compose.ui.graphics.RectangleShape
import com.awakekt.awake.compose.ui.graphics.Shape
import kotlin.jvm.JvmInline

/**
 * Strategy used to handle content edges when applying [Modifier.blur].
 *
 * @property shape The shape used to clip the blurred content, or `null` for unbounded blur that
 * expands beyond the original layout bounds.
 */
@JvmInline
value class BlurredEdgeTreatment(val shape: Shape?) {
    /** Predefined [BlurredEdgeTreatment] values. */
    companion object {
        /**
         * Clips the blurred content to a rectangular boundary matching the layout bounds.
         */
        val Rectangle: BlurredEdgeTreatment = BlurredEdgeTreatment(RectangleShape)

        /**
         * Allows blurred pixels to expand freely into adjacent space using decal edge treatment.
         */
        val Unbounded: BlurredEdgeTreatment = BlurredEdgeTreatment(null)
    }
}
