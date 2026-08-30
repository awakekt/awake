/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.draw

import io.github.awakelab.awake.compose.ui.graphics.drawscope.DrawScope

/**
 * A [DrawScope] that can also draw the content it wraps, wherever the caller wants it.
 *
 * Compose's own shape. The wrapped content is a call rather than an implicit ordering, which is what
 * lets one modifier put paint on both sides of it.
 */
interface ContentDrawScope : DrawScope {
    /** Draws the rest of the chain and this node's children. */
    fun drawContent()
}

internal class ContentDrawScopeImpl(
    scope: DrawScope,
    private val content: () -> Unit,
) : ContentDrawScope,
    DrawScope by scope {
    override fun drawContent() = content()
}
