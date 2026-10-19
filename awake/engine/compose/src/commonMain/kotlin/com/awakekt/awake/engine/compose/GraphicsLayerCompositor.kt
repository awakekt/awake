/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.compose

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerPlaceholder
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.graphics2d.TextureRegion
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.requiresDestinationSampling
import com.awakekt.awake.core.graphics2d.translatedBy
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.effects.BlurChain
import com.awakekt.awake.render.effects.LayerBlurEffect
import com.awakekt.awake.render.imagepass.ImagePassPlacement
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.UiTargetCompositeMode
import com.awakekt.awake.render.texture.RenderTarget
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renderer-owned resources for the renderer-neutral texture slots in [FrameOutput]: graphics layers
 * and drawn [ImageBitmap]s.
 */
class GraphicsLayerCompositor {
    private val slots = mutableMapOf<Int, Slot>()
    private var frameTargets: FrameTargets? = null
    private val images = ImageTextures()

    /**
     * Composites offscreen graphics layers and blends destination-sampled primitives into resolved draw primitives.
     *
     * @param renderer The renderer used to allocate render targets and draw UI primitives to textures.
     * @param primitives The top-level UI draw primitives to composite.
     * @param layers The active graphics layer frames rendered during the current frame.
     * @param font The active font used for text rendering within composited textures.
     * @param viewportWidth The width of the viewport in pixels.
     * @param viewportHeight The height of the viewport in pixels.
     * @return The list of resolved UI draw primitives ready for final presentation.
     */
    fun composite(
        renderer: Renderer,
        primitives: List<UiDrawPrimitive>,
        layers: List<GraphicsLayerFrame>,
        font: UiFont,
        viewportWidth: Int,
        viewportHeight: Int,
    ): List<UiDrawPrimitive> {
        val used = HashSet<Int>(layers.size)
        val materials = HashMap<Int, Material>(layers.size)
        val flat = flattenable(primitives, layers)
        for (layer in layers) {
            if (layer.id in flat) continue
            used += layer.id
            val slot = slotFor(renderer, layer)
            val childPrimitives = layer.primitives
                .resolve(renderer, materials, flat)
                .map { it.translatedBy(layer.effectInsetX.toFloat(), layer.effectInsetY.toFloat()) }
            renderer.drawUiToTexture(slot.target, childPrimitives, font)
            materials[layer.id] = if (layer.blurRadiusX > 0f || layer.blurRadiusY > 0f) {
                slot.blur(renderer, layer.blurRadiusX, layer.blurRadiusY)
            } else {
                slot.material
            }
        }
        slots.entries.removeAll { (id, slot) ->
            if (id in used) {
                false
            } else {
                slot.destroy()
                true
            }
        }
        val resolved = primitives.resolve(renderer, materials, flat)
        if (resolved.none { it is UiDrawPrimitive.Texture && it.blendMode.requiresDestinationSampling }) {
            frameTargets?.destroy()
            frameTargets = null
            return resolved
        }
        return targetsFor(renderer, viewportWidth, viewportHeight).composite(renderer, resolved, font)
    }

    private fun targetsFor(renderer: Renderer, width: Int, height: Int): FrameTargets {
        val existing = frameTargets
        if (existing != null && existing.width == width && existing.height == height) return existing
        existing?.destroy()
        return FrameTargets(renderer, width.coerceAtLeast(1), height.coerceAtLeast(1)).also { frameTargets = it }
    }

    private fun slotFor(renderer: Renderer, layer: GraphicsLayerFrame): Slot {
        val existing = slots[layer.id]
        val targetWidth = (layer.width + layer.effectInsetX * 2).coerceAtLeast(1)
        val targetHeight = (layer.height + layer.effectInsetY * 2).coerceAtLeast(1)
        if (existing != null && existing.target.width == targetWidth && existing.target.height == targetHeight) {
            return existing
        }
        existing?.destroy()
        val target = renderer.createRenderTarget(targetWidth, targetHeight)
        return Slot(target, renderer.createMaterial(renderTarget = target)).also { slots[layer.id] = it }
    }

    /** Releases renderer resources retained between frames by graphics layers and destination blending. */
    fun dispose() {
        slots.values.forEach(Slot::destroy)
        slots.clear()
        frameTargets?.destroy()
        frameTargets = null
        images.dispose()
    }

    private fun List<UiDrawPrimitive>.resolve(
        renderer: Renderer,
        materials: Map<Int, Material>,
        flat: Map<Int, GraphicsLayerFrame>,
    ): List<UiDrawPrimitive> =
        flatMap { primitive ->
            if (primitive !is UiDrawPrimitive.Texture) return@flatMap listOf(primitive)
            when (val slot = primitive.material) {
                is GraphicsLayerPlaceholder -> flat[slot.id]?.drawnDirectly(primitive)?.resolve(renderer, materials, flat)
                    ?: listOf(primitive.copy(material = materials.getValue(slot.id)))
                is ImageBitmap -> listOf(primitive.copy(material = images.materialFor(renderer, slot, primitive.filterQuality)))
                else -> listOf(primitive)
            }
        }

    /**
     * The [layers] that skip their offscreen pass, by id: those [drawnDirectly] can draw straight
     * into the parent. Rendering a layer offscreen makes the CPU wait for the GPU on Vulkan.
     */
    private fun flattenable(primitives: List<UiDrawPrimitive>, layers: List<GraphicsLayerFrame>): Map<Int, GraphicsLayerFrame> {
        if (layers.isEmpty()) return emptyMap()
        val composites = HashMap<Int, UiDrawPrimitive.Texture>()
        fun collect(list: List<UiDrawPrimitive>) = list.forEach { primitive ->
            val slot = (primitive as? UiDrawPrimitive.Texture)?.material as? GraphicsLayerPlaceholder
            if (slot != null) composites[slot.id] = primitive
        }
        collect(primitives)
        layers.forEach { collect(it.primitives) }
        return layers.filter { layer -> composites[layer.id]?.let(layer::drawnDirectly) != null }.associateBy { it.id }
    }

    private data class Slot(
        val target: RenderTarget,
        val material: Material,
        var blurTarget: RenderTarget? = null,
        var blurMaterial: Material? = null,
    ) {
        fun blur(renderer: Renderer, radiusX: Float, radiusY: Float): Material {
            val radius = maxOf(radiusX, radiusY)
            val blur = BlurChain.forRadius(radius, renderer.effectQuality, targetOutputLevel = 0) ?: return material
            val currentBlurTarget = blurTarget
            val target = if (currentBlurTarget != null &&
                currentBlurTarget.width == this.target.width &&
                currentBlurTarget.height == this.target.height
            ) {
                currentBlurTarget
            } else {
                blurMaterial?.destroy()
                currentBlurTarget?.destroy()
                renderer.createRenderTarget(this.target.width, this.target.height).also {
                    blurTarget = it
                    blurMaterial = renderer.createMaterial(renderTarget = it)
                }
            }
            val chain = LayerBlurEffect.buildChain(
                source = ImagePassSource.RenderTargetColor(this.target),
                blur = blur,
                width = this.target.width,
                height = this.target.height,
            )
            val placement = ImagePassPlacement(primitiveIndex = 0, chain = chain)
            renderer.drawUiToTexture(
                target = target,
                primitives = emptyList(),
                font = null,
                imagePasses = listOf(placement),
            )
            return requireNotNull(blurMaterial)
        }

        fun destroy() {
            blurMaterial?.destroy()
            blurTarget?.destroy()
            material.destroy()
            target.destroy()
        }
    }

    /** Three reusable full-frame targets: one staging source and an alternating destination pair. */
    private class FrameTargets(renderer: Renderer, val width: Int, val height: Int) {
        private val first = Target(renderer, width, height)
        private val second = Target(renderer, width, height)
        private val source = renderer.createRenderTarget(width, height)

        private class Target(renderer: Renderer, width: Int, height: Int) {
            val target = renderer.createRenderTarget(width, height)
            val material = renderer.createMaterial(renderTarget = target)
            fun destroy() {
                material.destroy()
                target.destroy()
            }
        }

        fun composite(renderer: Renderer, primitives: List<UiDrawPrimitive>, font: UiFont): List<UiDrawPrimitive> {
            var current: Target? = null
            val pending = mutableListOf<UiDrawPrimitive>()
            val activeClips = mutableListOf<UiDrawPrimitive>()

            fun alternate(): Target = if (current === first) second else first

            fun flushPending() {
                if (pending.isEmpty()) return
                val previous = current
                if (previous == null) {
                    renderer.drawUiToTexture(first.target, pending, font)
                    current = first
                } else {
                    renderer.drawUiToTexture(source, pending, font)
                    val output = alternate()
                    renderer.compositeUiTargets(previous.target, source, output.target, UiTargetCompositeMode.SourceOver)
                    current = output
                }
                pending.clear()
            }

            for (primitive in primitives) {
                when (primitive) {
                    is UiDrawPrimitive.ClipPush, is UiDrawPrimitive.ClipPathPush -> {
                        pending += primitive
                        activeClips += primitive
                    }
                    is UiDrawPrimitive.ClipPop -> {
                        pending += primitive
                        activeClips.removeLastOrNull()
                    }
                    is UiDrawPrimitive.Texture -> if (primitive.blendMode.requiresDestinationSampling) {
                        flushPending()
                        if (current == null) {
                            renderer.drawUiToTexture(first.target, emptyList(), font)
                            current = first
                        }
                        renderer.drawUiToTexture(source, activeClips + primitive, font)
                        val output = alternate()
                        renderer.compositeUiTargets(
                            requireNotNull(current).target,
                            source,
                            output.target,
                            when (primitive.blendMode) {
                                BlendMode.Screen -> UiTargetCompositeMode.Screen
                                BlendMode.Overlay -> UiTargetCompositeMode.Overlay
                                BlendMode.SourceOver, BlendMode.Plus -> error("${primitive.blendMode} does not require target compositing.")
                            },
                        )
                        current = output
                        pending += activeClips
                    } else {
                        pending += primitive
                    }
                    else -> pending += primitive
                }
            }
            flushPending()
            val result = requireNotNull(current) { "A destination-composite frame must produce a target." }
            return listOf(
                UiDrawPrimitive.Texture(
                    x = 0f,
                    y = 0f,
                    w = width.toFloat(),
                    h = height.toFloat(),
                    material = result.material,
                    premultiplied = true,
                ),
            )
        }

        fun destroy() {
            first.destroy()
            second.destroy()
            source.destroy()
        }
    }
}

/**
 * This layer's pictures drawn straight into the parent with [composite]'s rotation, scale and
 * offset, the way Compose draws a transform-only `graphicsLayer` without an offscreen buffer; null
 * when only an offscreen pass draws it the same. That holds while the layer has no effect and only
 * [drawsAsIs] textures, and, under a layer alpha, only one, since alpha applied per texture differs
 * from alpha applied once where two overlap.
 */
private fun GraphicsLayerFrame.drawnDirectly(composite: UiDrawPrimitive.Texture): List<UiDrawPrimitive>? {
    val noEffect = maxOf(blurRadiusX, blurRadiusY) == 0f && effectInsetX == 0 && effectInsetY == 0
    val plainComposite = composite.blendMode == BlendMode.SourceOver && composite.region == TextureRegion.Whole && composite.tint == Color.White
    // An element's transparent background quad paints nothing.
    val pictures = primitives.filterNot { it is UiDrawPrimitive.Quad && it.color.a == 0f && it.transform == null }
    val oneAlpha = composite.alpha >= 1f || pictures.size <= 1
    val direct = width > 0 && height > 0 && noEffect && plainComposite && oneAlpha && pictures.all { drawsAsIs(it) }
    if (!direct) return null
    val scaleX = composite.w / width
    val scaleY = composite.h / height
    val centerX = composite.x + composite.w / 2f
    val centerY = composite.y + composite.h / 2f
    val radians = composite.rotationDegrees * PI.toFloat() / 180f
    val cosine = cos(radians)
    val sine = sin(radians)
    return pictures.map { primitive ->
        val picture = primitive as UiDrawPrimitive.Texture
        val w = picture.w * scaleX
        val h = picture.h * scaleY
        // The picture's centre, turned clockwise about the layer's centre as the composite would turn it.
        val dx = composite.x + (picture.x + picture.w / 2f) * scaleX - centerX
        val dy = composite.y + (picture.y + picture.h / 2f) * scaleY - centerY
        picture.copy(
            x = centerX + dx * cosine - dy * sine - w / 2f,
            y = centerY + dx * sine + dy * cosine - h / 2f,
            w = w,
            h = h,
            alpha = picture.alpha * composite.alpha,
            rotationDegrees = composite.rotationDegrees,
            transform = composite.transform,
        )
    }
}

/**
 * Whether [primitive] is a texture this layer would draw unchanged: unturned, untransformed,
 * blended over and wholly inside the layer, which would otherwise clip it. A nested layer keeps its
 * offscreen pass.
 */
private fun GraphicsLayerFrame.drawsAsIs(primitive: UiDrawPrimitive): Boolean {
    if (primitive !is UiDrawPrimitive.Texture || primitive.material is GraphicsLayerPlaceholder) return false
    val plain = primitive.blendMode == BlendMode.SourceOver && primitive.rotationDegrees == 0f && primitive.transform == null
    val inside = minOf(primitive.x, primitive.y) >= 0f && primitive.x + primitive.w <= width && primitive.y + primitive.h <= height
    return plain && inside
}
