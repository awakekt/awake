/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.graphics.BlendMode
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.math.PI

private const val RAD_TO_DEG_CANVAS: Float = (180.0 / PI).toFloat()
private const val HALF_BYTE_ROUNDING: Int = 127
private const val BYTE_MAX: Int = 255
private const val BYTE_MASK: Int = 0xFF

private val WHITE_PREMULTIPLIED_BITMAP = ImageBitmap(
    width = 1,
    height = 1,
    pixels = byteArrayOf(-1, -1, -1, -1),
)

/**
 * The premultiplied copies of one overlay's particle sheets, kept by its [CanvasTree] across frames
 * (the overlay remembers it). It holds only copies of the overlay's current `images` and is cleared
 * when that map changes, so it is bounded by the images the overlay was given. One overlay draws on
 * one thread, so it needs no lock.
 */
internal class PremultipliedImages {
    private var source: Map<String, ImageBitmap>? = null
    private val copies = HashMap<ImageBitmap, ImageBitmap>()

    /** The premultiplied copy of [bitmap], one of [images], made on first use. */
    fun of(images: Map<String, ImageBitmap>, bitmap: ImageBitmap): ImageBitmap {
        if (images !== source) {
            copies.clear()
            source = images
        }
        return copies.getOrPut(bitmap) { premultiplyImageBitmap(bitmap) }
    }

    /** How many copies it holds, for tests. */
    val size: Int get() = copies.size
}

internal class CanvasParticlesClock {
    var stamp: Long = 0L
        private set

    var deltaSeconds: Float = 0f
        private set

    fun advance(deltaSeconds: Float) {
        stamp += 1L
        this.deltaSeconds = if (deltaSeconds.isFinite() && deltaSeconds > 0f) deltaSeconds else 0f
    }
}

internal val LocalCanvasParticlesClock = compositionLocalOf { CanvasParticlesClock() }

/**
 * Steps any visible follower emitters whose `FollowerPlan.drawn` was `false` this pass so they
 * continue advancing while off-screen without composing a visible node.
 */
internal fun stepUncomposedVisibleFollowers(tree: CanvasTree, clock: CanvasParticlesClock) {
    for (item in tree.roots) {
        val element = item.element
        val particles = element.particles
        val isUncomposedVisibleFollower =
            particles != null &&
                tree.shows(element) &&
                element.follow.isNotEmpty() &&
                tree.followerPlans[element]?.drawn != true
        if (isUncomposedVisibleFollower) {
            stepEmitterForFrame(particles, element, clock, tree.canvasDensity, tree.densityOf(element))
        }
    }
}

context(_: Composer)
internal fun ParticlesOverContent(element: CanvasElement, tree: CanvasTree) {
    val clock = LocalCanvasParticlesClock.current
    val canvasDensity = LocalCanvasDensity.current
    Box(
        Modifier.fillMaxSize().then(
            CanvasParticlesElement(
                element = element,
                images = tree.images,
                premultiplied = tree.premultiplied,
                clock = clock,
                canvasDensity = canvasDensity,
                behindContent = false,
            ),
        ),
    )
}

context(_: Composer)
internal fun Modifier.canvasParticlesBehind(element: CanvasElement, tree: CanvasTree): Modifier {
    val clock = LocalCanvasParticlesClock.current
    val canvasDensity = LocalCanvasDensity.current
    return this then CanvasParticlesElement(
        element = element,
        images = tree.images,
        premultiplied = tree.premultiplied,
        clock = clock,
        canvasDensity = canvasDensity,
        behindContent = true,
    )
}

private class CanvasParticlesElement(
    private val element: CanvasElement,
    private val images: Map<String, ImageBitmap>,
    private val premultiplied: PremultipliedImages,
    private val clock: CanvasParticlesClock,
    private val canvasDensity: Float,
    private val behindContent: Boolean,
) : ModifierNodeElement<CanvasParticlesDrawNode>() {
    override fun create(): CanvasParticlesDrawNode =
        CanvasParticlesDrawNode(element, images, premultiplied, clock, canvasDensity, behindContent)

    override fun update(node: CanvasParticlesDrawNode) {
        node.element = element
        node.images = images
        node.premultiplied = premultiplied
        node.clock = clock
        node.canvasDensity = canvasDensity
        node.behindContent = behindContent
    }
}

private class CanvasParticlesDrawNode(
    var element: CanvasElement,
    var images: Map<String, ImageBitmap>,
    var premultiplied: PremultipliedImages,
    var clock: CanvasParticlesClock,
    var canvasDensity: Float,
    var behindContent: Boolean,
) : Modifier.Node(),
    DrawModifierNode {
    private val pictureMask = PictureMask()

    override fun DrawScope.draw(drawContent: () -> Unit) {
        val particles = element.particles
        if (particles != null) {
            val effectiveCanvasDensity = if (canvasDensity > 0f) canvasDensity else density
            stepEmitterForFrame(particles, element, clock, effectiveCanvasDensity, density)
            if (particles.enabled && !particles.isFinished && particles.liveParticleCount > 0) {
                drawEmitterClippedOrUnclipped(particles, effectiveCanvasDensity)
            }
        }
        if (behindContent) drawContent()
    }

    private fun DrawScope.drawEmitterClippedOrUnclipped(
        particles: CanvasParticles,
        effectiveCanvasDensity: Float,
    ) {
        val bitmap = resolvePremultipliedBitmap(particles, images, premultiplied) ?: return
        val widget = element.widget
        val radius = element.style.cornerRadius ?: 0f
        when {
            !particles.clipped -> unclipped {
                drawAliveParticles(particles, bitmap, effectiveCanvasDensity)
            }
            widget is CanvasViewWidget && ((element.view ?: CanvasView.Default).mask != CanvasMask.Box || radius > 0f) -> {
                val settings = element.view ?: CanvasView.Default
                pictureMask.clip(this, settings.mask, radius) {
                    drawAliveParticles(particles, bitmap, effectiveCanvasDensity)
                }
            }
            widget is CanvasSweepWidget && element.sweepMask() != DrawShape.Rectangle -> {
                val bounds = Rectangle(0f, 0f, width.toFloat(), height.toFloat())
                clippedPath(element.sweepMask().toPath(bounds, density = density)) {
                    drawAliveParticles(particles, bitmap, effectiveCanvasDensity)
                }
            }
            else -> drawAliveParticles(particles, bitmap, effectiveCanvasDensity)
        }
    }

    private fun DrawScope.drawAliveParticles(
        particles: CanvasParticles,
        bitmap: ImageBitmap,
        effectiveCanvasDensity: Float,
    ) {
        val sim = particles.simulation
        val cols = particles.columns.coerceAtLeast(1)
        val rows = particles.rows.coerceAtLeast(1)
        val cellW = bitmap.width.toFloat() / cols
        val cellH = bitmap.height.toFloat() / rows
        val aspect = if (cellH > 0f) cellW / cellH else 1f
        val blend = if (particles.additive) BlendMode.Plus else BlendMode.SourceOver
        val widthDp = width / density
        val heightDp = height / density
        val anchorPxX = particles.localAnchorDpX(widthDp) * density
        val anchorPxY = particles.localAnchorDpY(heightDp) * density
        val placed = element.placedBounds
        val originDpX = placed?.x ?: element.offsetX
        val originDpY = placed?.y ?: element.offsetY

        for (slot in 0 until sim.maxParticles) {
            val opacity = if (sim.isAlive(slot)) sim.alphaAt(slot) else 0f
            val quadH = if (opacity > 0f) sim.scaleAt(slot) * density else 0f
            val quadW = quadH * aspect
            if (quadW <= 0f || quadH <= 0f) continue
            val centerX: Float
            val centerY: Float
            if (particles.localSpace) {
                centerX = anchorPxX + sim.posX[slot] * density
                centerY = anchorPxY + sim.posY[slot] * density
            } else {
                centerX = (sim.posX[slot] - originDpX) * effectiveCanvasDensity
                centerY = (sim.posY[slot] - originDpY) * effectiveCanvasDensity
            }
            val region = particles.regionForFrame(sim.frameAt(slot), cols, rows)
            val tint = particles.particleTintAt(slot, opacity)
            val rotationDeg = sim.rotation[slot] * RAD_TO_DEG_CANVAS
            drawTexture(
                material = bitmap,
                x = centerX - quadW * 0.5f,
                y = centerY - quadH * 0.5f,
                width = quadW,
                height = quadH,
                region = region,
                tint = tint,
                filterQuality = FilterQuality.Low,
                premultiplied = true,
                rotationDegrees = rotationDeg,
                blendMode = blend,
            )
        }
    }
}

private fun stepEmitterForFrame(
    particles: CanvasParticles,
    element: CanvasElement,
    clock: CanvasParticlesClock,
    canvasDensity: Float,
    elementDensity: Float,
) {
    if (clock.stamp != 0L && particles.lastFrameStamp == clock.stamp) return
    particles.lastFrameStamp = clock.stamp
    particles.lastDensityRatio = if (canvasDensity > 0f && elementDensity > 0f) {
        elementDensity / canvasDensity
    } else {
        1f
    }
    if (clock.deltaSeconds > 0f) {
        particles.step(clock.deltaSeconds, element.width, element.height)
    } else if (particles.enabled && !particles.isFinished && particles.simulation.isComplete) {
        particles.finish()
    }
}

private fun CanvasParticles.particleTintAt(slot: Int, opacity: Float): Color {
    val target = endTint ?: return Color(tint.r, tint.g, tint.b, (tint.a * opacity).coerceIn(0f, 1f))
    val fraction = simulation.lifeFractionAt(slot)
    val r = tint.r + (target.r - tint.r) * fraction
    val g = tint.g + (target.g - tint.g) * fraction
    val b = tint.b + (target.b - tint.b) * fraction
    val a = tint.a + (target.a - tint.a) * fraction
    return Color(r, g, b, (a * opacity).coerceIn(0f, 1f))
}

internal fun resolvePremultipliedBitmap(
    particles: CanvasParticles,
    images: Map<String, ImageBitmap>,
    premultiplied: PremultipliedImages,
): ImageBitmap? {
    val key = particles.texture
    if (key.isEmpty()) return WHITE_PREMULTIPLIED_BITMAP
    val source = images[key]
    return when {
        source == null -> null
        particles.cachedSourceBitmap === source -> particles.cachedPremultipliedBitmap as ImageBitmap
        else -> {
            val converted = premultiplied.of(images, source)
            particles.cachedSourceBitmap = source
            particles.cachedPremultipliedBitmap = converted
            converted
        }
    }
}

internal fun premultiplyImageBitmap(source: ImageBitmap): ImageBitmap {
    val src = source.pixels
    val dst = ByteArray(src.size)
    var offset = 0
    while (offset < src.size) {
        val r = src[offset].toInt() and BYTE_MASK
        val g = src[offset + 1].toInt() and BYTE_MASK
        val b = src[offset + 2].toInt() and BYTE_MASK
        val a = src[offset + 3].toInt() and BYTE_MASK
        dst[offset] = ((r * a + HALF_BYTE_ROUNDING) / BYTE_MAX).toByte()
        dst[offset + 1] = ((g * a + HALF_BYTE_ROUNDING) / BYTE_MAX).toByte()
        dst[offset + 2] = ((b * a + HALF_BYTE_ROUNDING) / BYTE_MAX).toByte()
        dst[offset + 3] = a.toByte()
        offset += ImageBitmap.BYTES_PER_PIXEL
    }
    return ImageBitmap(source.width, source.height, dst)
}
