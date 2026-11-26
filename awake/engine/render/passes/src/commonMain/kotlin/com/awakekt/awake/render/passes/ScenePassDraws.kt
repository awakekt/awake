/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.COVERING_EXEMPT_MASK_LAYER
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.MASK_LAYER_COUNT
import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.compareTransparent
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight

/**
 * Working draw lists [ScenePassScratch] keeps from frame to frame (#1555).
 *
 * Preparing a frame used to wrap every visible draw in a fresh `Caster`, partition the list,
 * group opaque casters into a new map of lists, sort each into another list, flatten the map back
 * out, unwrap `Caster.draw` into two more lists, filter and concatenate the casting subset, and
 * allocate a plane array and a fresh list per shadow cascade. Kept here, those buffers grow to the
 * scene's high-water mark once and are cleared and refilled in place on every compiled frame.
 */
internal class ScenePassDraws {
    val opaque = ArrayList<GpuResolvedDraw>()
    val transparent = ArrayList<GpuResolvedDraw>()
    val prePasses = ArrayList<GpuSubPass>()
    private val casting = ArrayList<Caster>()
    private val shadowOnly = ArrayList<Caster>()
    private val casterPool = ArrayList<Caster>()
    private var casterCount = 0
    private val pipelineGroups = ArrayList<PipelineGroup>()
    private var pipelineCount = 0
    private var pipelineTable = IntArray(INITIAL_PIPELINES)
    private val sorter = StableListSort()
    private val masked = MaskedDraws()
    private val castingPlanes = FloatArray(CASTING_PLANE_FLOATS)
    private val shadowDrawLists = ArrayList<ArrayList<GpuResolvedDraw>>()
    private var shadowPassCount = 0

    fun reset(maskLayers: List<FloatArray>) {
        opaque.clear()
        transparent.clear()
        prePasses.clear()
        casting.clear()
        shadowOnly.clear()
        casterCount = 0
        for (index in 0 until pipelineCount) {
            pipelineGroups[index].pipeline = null
            pipelineGroups[index].casters.clear()
        }
        pipelineCount = 0
        pipelineTable.fill(0)
        shadowPassCount = 0
        masked.reset(maskLayers)
    }

    /**
     * Prepares [requests] in source order, then [casters]' folded shadow-only draws, and orders
     * [opaque], [transparent] and [casting] for recording without allocating per draw or per group.
     */
    fun prepare(
        requests: List<RenderDrawCommand>,
        preparer: GpuDrawPreparer,
        context: GpuDrawPreparationContext,
        casters: ShadowCasterFolding,
    ) {
        var lastGroup: PipelineGroup? = null
        for (index in 0 until requests.size) {
            val request = requests[index]
            val draw = preparer.prepare(request, index, context) ?: continue
            val casts = request.castsShadows && casters.castsAlone(index)
            if (request.shadowsOnly) {
                if (!draw.transparent && casts) shadowOnly += caster(draw, request.worldBounds, true)
            } else {
                masked.add(request, draw)
                if (draw.transparent) {
                    transparent += draw
                } else {
                    val pipeline = draw.pipeline
                    val group = lastGroup?.takeIf { it.pipeline == pipeline } ?: pipelineOf(pipeline)
                    group.casters += caster(draw, request.worldBounds, casts)
                    lastGroup = group
                }
            }
        }
        casters.prepare(preparer, requests.size, context) { draw, bounds ->
            shadowOnly += caster(draw, bounds, true)
        }
        // Comparisons inlined: on Kotlin/Wasm each call to a Comparator allocated (#1621).
        sorter.sortWith(transparent) { a, b -> compareTransparent(a, b) }
        for (groupIndex in 0 until pipelineCount) {
            val group = pipelineGroups[groupIndex].casters
            sorter.sortWith(group) { a, b -> a.draw.batchKey.compareTo(b.draw.batchKey) }
            for (index in 0 until group.size) {
                val item = group[index]
                opaque += item.draw
                if (item.casts) casting += item
            }
        }
        for (index in 0 until shadowOnly.size) {
            casting += shadowOnly[index]
        }
    }

    /**
     * A depth sub-pass per shadow cascade in [cascades], then one per point-shadow face, reusing
     * each pass's draw list from the last frame.
     */
    fun shadowPrePasses(
        cascades: List<Mat4>,
        resolved: Boolean,
        light: SceneLight?,
        environment: EnvironmentUniforms,
    ): List<GpuSubPass> {
        for (layer in 0 until cascades.size) {
            val cascadeVp = cascades[layer]
            val draws = if (resolved) seenBy(cascadeVp) else emptyList()
            prePasses += GpuSubPass(target = null, targetLayer = layer, viewProjection = cascadeVp, resolvedDraws = draws)
        }
        if (resolved && light != null && environment.shadowsEnabled) {
            val points = light.pointShadows
            for (pointIndex in 0 until points.size) {
                val pointShadow = points[pointIndex]
                val faces = pointShadow.viewProjections
                for (face in 0 until faces.size) {
                    val faceVp = faces[face]
                    prePasses += GpuSubPass(
                        target = null,
                        targetLayer = pointShadow.baseLayer + face,
                        viewProjection = faceVp,
                        resolvedDraws = seenBy(faceVp),
                    )
                }
            }
        }
        return prePasses
    }

    fun maskSubPasses(viewProjection: Mat4, clipSpace: ClipSpace): List<GpuSubPass> = masked.subPasses(viewProjection, clipSpace)

    private fun caster(draw: GpuResolvedDraw, bounds: Aabb?, casts: Boolean): Caster {
        if (casterCount == casterPool.size) {
            val created = Caster(draw, bounds, casts)
            casterPool += created
            casterCount++
            return created
        }
        return casterPool[casterCount++].also { it.set(draw, bounds, casts) }
    }

    private fun pipelineOf(pipeline: PipelineHandle): PipelineGroup {
        if (pipelineCount * 2 >= pipelineTable.size) growPipelines()
        val mask = pipelineTable.size - 1
        var slot = pipelineHash(pipeline) and mask
        while (true) {
            val stored = pipelineTable[slot]
            if (stored == 0) break
            val group = pipelineGroups[stored - 1]
            if (group.pipeline == pipeline) return group
            slot = (slot + 1) and mask
        }
        if (pipelineCount == pipelineGroups.size) pipelineGroups += PipelineGroup()
        val group = pipelineGroups[pipelineCount++]
        group.pipeline = pipeline
        group.casters.clear()
        pipelineTable[slot] = pipelineCount
        return group
    }

    private fun growPipelines() {
        pipelineTable = IntArray(pipelineTable.size * 2)
        val mask = pipelineTable.size - 1
        for (index in 0 until pipelineCount) {
            var slot = pipelineHash(pipelineGroups[index].pipeline) and mask
            while (pipelineTable[slot] != 0) slot = (slot + 1) and mask
            pipelineTable[slot] = index + 1
        }
    }

    /**
     * The casters that can write a texel of the depth map [viewProjection] renders, collected into
     * this shadow pass's kept list.
     */
    private fun seenBy(viewProjection: Mat4): List<GpuResolvedDraw> {
        if (shadowPassCount == shadowDrawLists.size) shadowDrawLists.add(ArrayList())
        val out = shadowDrawLists[shadowPassCount++]
        out.clear()
        writeCastingPlanes(viewProjection, castingPlanes, 0)
        for (index in 0 until casting.size) {
            val caster = casting[index]
            val box = caster.bounds
            if (box == null || castingPlanes.admitsBox(0, box.min.x, box.min.y, box.min.z, box.max.x, box.max.y, box.max.z)) {
                out += caster.draw
            }
        }
        return out
    }

    private companion object {
        const val INITIAL_PIPELINES = 16

        fun pipelineHash(pipeline: PipelineHandle?): Int {
            val hash = pipeline.hashCode()
            return hash xor (hash ushr 16)
        }
    }
}

/**
 * A prepared opaque draw, the world bounds its request carried and whether that request casts,
 * pooled across frames in [ScenePassDraws].
 */
internal class Caster(var draw: GpuResolvedDraw, var bounds: Aabb?, var casts: Boolean) {
    fun set(draw: GpuResolvedDraw, bounds: Aabb?, casts: Boolean) {
        this.draw = draw
        this.bounds = bounds
        this.casts = casts
    }
}

private class PipelineGroup {
    var pipeline: PipelineHandle? = null
    val casters = ArrayList<Caster>()
}

/** The draws each mask layer names, gathered into kept per-layer lists as they are prepared. */
private class MaskedDraws {
    private var layers: List<FloatArray> = emptyList()
    private val draws = arrayOfNulls<ArrayList<GpuResolvedDraw>>(MASK_TARGET_LAYER_COUNT)
    private val passes = ArrayList<GpuSubPass>()
    private var anyMasked = false

    /** Each layer's world bounds so far, min xyz then max xyz; [bounded] false once a draw brought none. */
    private val bounds = FloatArray(MASK_TARGET_LAYER_COUNT * BOUNDS_FLOATS)
    private val bounded = BooleanArray(MASK_TARGET_LAYER_COUNT)

    // Per-draw world bounds and projected screen rects for styled outline layers so distant outlined
    // targets get separate disjoint scissors instead of shading the empty gap between them.
    private val drawWorldBounds = Array(MASK_LAYER_COUNT) { FloatArray(MAX_MASK_CLUSTERS * BOUNDS_FLOATS) }
    private val drawWorldBoundCount = IntArray(MASK_LAYER_COUNT)
    private val drawScreenRects = Array(MASK_LAYER_COUNT) { FloatArray(MAX_MASK_CLUSTERS * SCREEN_RECT_FLOATS) }

    fun reset(maskLayers: List<FloatArray>) {
        layers = maskLayers
        if (anyMasked) {
            for (layer in draws.indices) draws[layer]?.clear()
            drawWorldBoundCount.fill(0)
            passes.clear()
            anyMasked = false
        }
    }

    fun add(request: RenderDrawCommand, draw: GpuResolvedDraw) {
        if (request.maskLayer in layers.indices && request.maskLayer < MASK_LAYER_COUNT) add(request.maskLayer, draw, request.worldBounds)
        if (request.exemptFromCoverings && !draw.transparent) add(COVERING_EXEMPT_MASK_LAYER, draw, request.worldBounds)
    }

    private fun add(layer: Int, draw: GpuResolvedDraw, worldBounds: Aabb?) {
        val list = draws[layer] ?: ArrayList<GpuResolvedDraw>().also { draws[layer] = it }
        val first = list.isEmpty()
        list += draw
        anyMasked = true
        if (first) bounded[layer] = worldBounds != null
        if (worldBounds == null) {
            bounded[layer] = false
        } else if (bounded[layer]) {
            grow(layer, worldBounds, first)
            recordDrawBounds(layer, worldBounds)
        }
    }

    private fun recordDrawBounds(layer: Int, box: Aabb) {
        if (layer !in 0 until MASK_LAYER_COUNT) return
        val count = drawWorldBoundCount[layer]
        if (count in 0 until MAX_MASK_CLUSTERS) {
            val at = count * BOUNDS_FLOATS
            val target = drawWorldBounds[layer]
            target[at] = box.min.x
            target[at + 1] = box.min.y
            target[at + 2] = box.min.z
            target[at + 3] = box.max.x
            target[at + 4] = box.max.y
            target[at + 5] = box.max.z
            drawWorldBoundCount[layer] = count + 1
        } else if (count >= MAX_MASK_CLUSTERS) {
            drawWorldBoundCount[layer] = CLUSTER_OVERFLOW
        }
    }

    private fun grow(layer: Int, box: Aabb, first: Boolean) {
        val at = layer * BOUNDS_FLOATS
        growAxis(at, box.min.x, box.max.x, first)
        growAxis(at + 1, box.min.y, box.max.y, first)
        growAxis(at + 2, box.min.z, box.max.z, first)
    }

    private fun growAxis(at: Int, low: Float, high: Float, first: Boolean) {
        bounds[at] = if (first) low else minOf(bounds[at], low)
        bounds[at + AXES] = if (first) high else maxOf(bounds[at + AXES], high)
    }

    fun subPasses(viewProjection: Mat4, clipSpace: ClipSpace): List<GpuSubPass> {
        if (!anyMasked) return emptyList()
        for (layer in draws.indices) {
            val drawn = draws[layer]
            if (drawn != null && drawn.isNotEmpty()) {
                val uniforms = layers.getOrElse(layer) { NO_LAYER_UNIFORMS }
                val pass = GpuSubPass(target = null, targetLayer = layer, viewProjection = viewProjection, resolvedDraws = drawn, passUniforms = uniforms)
                if (bounded[layer]) {
                    pass.screenBounds = screenBounds(bounds, layer * BOUNDS_FLOATS, viewProjection, clipSpace)
                    populateClusterRects(layer, pass, viewProjection, clipSpace)
                }
                passes += pass
            }
        }
        return passes
    }

    private fun populateClusterRects(layer: Int, pass: GpuSubPass, viewProjection: Mat4, clipSpace: ClipSpace) {
        val count = if (layer in 0 until MASK_LAYER_COUNT && pass.screenBounds != null) drawWorldBoundCount[layer] else 0
        if (count < 2) return
        val src = drawWorldBounds[layer]
        val dst = drawScreenRects[layer]
        for (i in 0 until count) {
            if (!writeScreenBounds(src, i * BOUNDS_FLOATS, viewProjection, clipSpace, dst, i * SCREEN_RECT_FLOATS)) {
                pass.screenBounds = null
                return
            }
        }
        pass.screenBoundRects = dst
        pass.screenBoundRectCount = count
    }
}

private const val AXES = 3
private const val BOUNDS_FLOATS = 2 * AXES
private const val SCREEN_RECT_FLOATS = 4
private const val MAX_MASK_CLUSTERS = 16
private const val CLUSTER_OVERFLOW = -1

/** [COVERING_EXEMPT_MASK_LAYER]'s sub-pass uniforms: nothing reads a style from it. */
private val NO_LAYER_UNIFORMS = FloatArray(0)
