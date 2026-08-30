/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.draw

import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.graphics.drawscope.DrawScope
import io.github.awakelab.awake.compose.ui.node.DrawModifierNode
import io.github.awakelab.awake.compose.ui.unit.Density
import io.github.awakelab.awake.compose.ui.unit.LayoutDirection
import io.github.awakelab.awake.core.math2d.Size2D

/**
 * Creates a [Modifier] that allows the caller to cache draw operations (such as calculating paths,
 * matrices, or gradients) across frames until layout size, density, or layout direction changes.
 */
fun Modifier.drawWithCache(onBuildDrawCache: CacheDrawScope.() -> DrawResult): Modifier =
    this then DrawWithCacheElement(onBuildDrawCache)

fun interface DrawResult {
    fun DrawScope.draw(drawContent: () -> Unit)
}

/**
 * Scope provided to the lambda of [Modifier.drawWithCache].
 * Exposes current measured size, density, and layout direction.
 */
class CacheDrawScope internal constructor() : Density {
    override var density: Float = 1f
        internal set

    override val fontScale: Float
        get() = 1f

    var size: Size2D = Size2D(0f, 0f)
        internal set

    var layoutDirection: LayoutDirection = LayoutDirection.Ltr
        internal set

    /**
     * Issues drawing commands behind the layout content.
     */
    fun onDrawBehind(block: DrawScope.() -> Unit): DrawResult =
        DrawResult { drawContent ->
            block()
            drawContent()
        }

    /**
     * Issues drawing commands with explicit control over when [ContentDrawScope.drawContent] is invoked.
     */
    fun onDrawWithContent(block: ContentDrawScope.() -> Unit): DrawResult =
        DrawResult { drawContent ->
            val contentScope = object : ContentDrawScope, DrawScope by this {
                override fun drawContent() {
                    drawContent()
                }
            }
            contentScope.block()
        }
}

private class DrawWithCacheElement(
    private val onBuildDrawCache: CacheDrawScope.() -> DrawResult,
) : ModifierNodeElement<DrawWithCacheNode>() {
    override fun create(): DrawWithCacheNode = DrawWithCacheNode(onBuildDrawCache)

    override fun update(node: DrawWithCacheNode) {
        node.onBuildDrawCache = onBuildDrawCache
    }

    override fun toString(): String = "drawWithCache()"
}

private class DrawWithCacheNode(
    var onBuildDrawCache: CacheDrawScope.() -> DrawResult,
) : Modifier.Node(), DrawModifierNode {
    private val cacheScope = CacheDrawScope()
    private var cachedResult: DrawResult? = null
    private var lastWidth: Int = -1
    private var lastHeight: Int = -1
    private var lastDensity: Float = -1f
    private var lastLayoutDirection: LayoutDirection? = null

    /**
     * Rebuilt on geometry only, never on the callback.
     *
     * There was a `lastCallback` field here for comparing the lambda and it was never read, which
     * is the right answer written the wrong way round. Upstream Compose can invalidate on a changed
     * lambda because its compiler makes one stable while its captures are unchanged; there is no
     * such plugin here, so a capturing lambda is a fresh instance every pass and identity would miss
     * on every frame -- turning the cache off entirely, in the engine where tessellating a shape per
     * frame was twenty milliseconds.
     *
     * The consequence, which is real: a `drawWithCache` whose lambda captures a value that changes
     * without the size, density or direction changing keeps drawing the old result. A caller that
     * needs that must key its own cache, the way `BorderNode` does.
     */
    override fun DrawScope.draw(drawContent: () -> Unit) {
        val w = width
        val h = height
        val d = density
        val ld = layoutDirection

        if (cachedResult == null ||
            w != lastWidth ||
            h != lastHeight ||
            d != lastDensity ||
            ld != lastLayoutDirection
        ) {
            cacheScope.density = d
            cacheScope.size = Size2D(w.toFloat(), h.toFloat())
            cacheScope.layoutDirection = ld
            cachedResult = cacheScope.onBuildDrawCache()
            lastWidth = w
            lastHeight = h
            lastDensity = d
            lastLayoutDirection = ld
        }

        cachedResult?.run { draw(drawContent) } ?: drawContent()
    }

    override fun toString(): String = "drawWithCache()"
}
