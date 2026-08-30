/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.engine.compose

import io.github.awakelab.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import io.github.awakelab.awake.compose.ui.graphics.drawscope.GraphicsLayerPlaceholder
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.core.graphics2d.BlendMode
import io.github.awakelab.awake.core.graphics2d.requiresDestinationSampling
import io.github.awakelab.awake.core.graphics2d.translatedBy
import io.github.awakelab.awake.core.text.font.UiFont
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.renderer.Renderer
import io.github.awakelab.awake.render.renderer.UiTargetCompositeMode
import io.github.awakelab.awake.render.texture.RenderTarget

/** Renderer-owned resources for the renderer-neutral graphics-layer records in [FrameOutput]. */
class GraphicsLayerCompositor {
    private val slots = mutableMapOf<Int, Slot>()
    private var frameTargets: FrameTargets? = null

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
        for (layer in layers) {
            used += layer.id
            val slot = slotFor(renderer, layer)
            val childPrimitives = layer.primitives
                .resolve(materials)
                .map { it.translatedBy(layer.effectInsetX.toFloat(), layer.effectInsetY.toFloat()) }
            renderer.drawUiToTexture(slot.target, childPrimitives, font)
            materials[layer.id] = if (layer.blurRadiusX > 0f || layer.blurRadiusY > 0f) {
                slot.blur(renderer, layer.blurRadiusX, layer.blurRadiusY)
            } else {
                slot.material
            }
        }
        slots.entries.removeAll { (id, slot) ->
            if (id in used) false else {
                slot.destroy()
                true
            }
        }
        val resolved = primitives.resolve(materials)
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
    }

    private fun List<UiDrawPrimitive>.resolve(materials: Map<Int, Material>): List<UiDrawPrimitive> =
        map { primitive ->
            val placeholder = (primitive as? UiDrawPrimitive.Texture)?.material as? GraphicsLayerPlaceholder
            if (primitive is UiDrawPrimitive.Texture && placeholder != null) {
                primitive.copy(material = materials.getValue(placeholder.id))
            } else {
                primitive
            }
        }

    private data class Slot(
        val target: RenderTarget,
        val material: Material,
        var blurTarget: RenderTarget? = null,
        var blurMaterial: Material? = null,
    ) {
        fun blur(renderer: Renderer, radiusX: Float, radiusY: Float): Material {
            val target = blurTarget ?: renderer.createRenderTarget(this.target.width, this.target.height).also {
                blurTarget = it
                blurMaterial = renderer.createMaterial(renderTarget = it)
            }
            renderer.drawUiToTexture(target, blurPrimitives(radiusX, radiusY), font = null)
            return requireNotNull(blurMaterial)
        }

        private fun blurPrimitives(radiusX: Float, radiusY: Float): List<UiDrawPrimitive> {
            val offsets = listOf(
                Triple(-radiusX, -radiusY, 1f / 16f), Triple(0f, -radiusY, 2f / 16f), Triple(radiusX, -radiusY, 1f / 16f),
                Triple(-radiusX, 0f, 2f / 16f), Triple(0f, 0f, 4f / 16f), Triple(radiusX, 0f, 2f / 16f),
                Triple(-radiusX, radiusY, 1f / 16f), Triple(0f, radiusY, 2f / 16f), Triple(radiusX, radiusY, 1f / 16f),
            )
            return offsets.map { (x, y, weight) ->
                UiDrawPrimitive.Texture(
                    x = x,
                    y = y,
                    w = target.width.toFloat(),
                    h = target.height.toFloat(),
                    material = material,
                    alpha = weight,
                    blendMode = BlendMode.Plus,
                    premultiplied = true,
                )
            }
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
                    } else pending += primitive
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
