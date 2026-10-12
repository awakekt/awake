/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.alpha
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression for #724: an enclosing `alpha` dimmed a colour fill but left `drawImageFill`'s
 * texture quads at full opacity, since [drawTexture] never folded the scope's dim into the
 * primitive it emitted.
 */
class ImageFillAlphaTest {
    private val image = ImageBitmap(4, 4, ByteArray(4 * 4 * 4) { -1 })

    private fun paint(dim: Float): List<UiDrawPrimitive.Texture> {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Spacer(Modifier.size(20.dp).alpha(dim).drawBehind { drawImageFill(ImageFill(image)) })
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        return Painter().paint(root).filterIsInstance<UiDrawPrimitive.Texture>()
    }

    @Test
    fun aDimReachesAnImageFillsTexture() {
        val textures = paint(dim = 0.2f)

        assertEquals(listOf(0.2f), textures.map { it.alpha })
    }

    @Test
    fun noDimLeavesTheTextureAtFullAlpha() {
        val textures = paint(dim = 1f)

        assertEquals(listOf(1f), textures.map { it.alpha })
    }
}
