/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.COVERING_EXEMPT_MASK_LAYER
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.MASK_LAYER_COUNT
import com.awakekt.awake.render.command.MASK_TARGET_LAYER_COUNT
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.TransparentOrder
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import kotlin.math.max
import kotlin.math.min

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
        sorter.sort(transparent, TransparentOrder)
        for (groupIndex in 0 until pipelineCount) {
            val group = pipelineGroups[groupIndex].casters
            sorter.sort(group, CasterBatchKeyOrder)
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

    fun maskSubPasses(viewProjection: Mat4): List<GpuSubPass> = masked.subPasses(viewProjection)

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

private val CasterBatchKeyOrder: Comparator<Caster> = Comparator { a, b ->
    a.draw.batchKey.compareTo(b.draw.batchKey)
}

/** The draws each mask layer names, gathered into kept per-layer lists as they are prepared. */
private class MaskedDraws {
    private var layers: List<FloatArray> = emptyList()
    private val draws = arrayOfNulls<ArrayList<GpuResolvedDraw>>(MASK_TARGET_LAYER_COUNT)
    private val passes = ArrayList<GpuSubPass>()
    private var anyMasked = false

    fun reset(maskLayers: List<FloatArray>) {
        layers = maskLayers
        if (anyMasked) {
            for (layer in draws.indices) draws[layer]?.clear()
            passes.clear()
            anyMasked = false
        }
    }

    fun add(request: RenderDrawCommand, draw: GpuResolvedDraw) {
        if (request.maskLayer in layers.indices && request.maskLayer < MASK_LAYER_COUNT) add(request.maskLayer, draw)
        if (request.exemptFromCoverings && !draw.transparent) add(COVERING_EXEMPT_MASK_LAYER, draw)
    }

    private fun add(layer: Int, draw: GpuResolvedDraw) {
        val list = draws[layer] ?: ArrayList<GpuResolvedDraw>().also { draws[layer] = it }
        list += draw
        anyMasked = true
    }

    fun subPasses(viewProjection: Mat4): List<GpuSubPass> {
        if (!anyMasked) return emptyList()
        for (layer in draws.indices) {
            val drawn = draws[layer]
            if (drawn != null && drawn.isNotEmpty()) {
                val uniforms = layers.getOrElse(layer) { NO_LAYER_UNIFORMS }
                passes += GpuSubPass(target = null, targetLayer = layer, viewProjection = viewProjection, resolvedDraws = drawn, passUniforms = uniforms)
            }
        }
        return passes
    }
}

/** [COVERING_EXEMPT_MASK_LAYER]'s sub-pass uniforms: nothing reads a style from it. */
private val NO_LAYER_UNIFORMS = FloatArray(0)

/**
 * Sorts an [ArrayList] in place, stably, using one kept scratch array so neither the JVM (with
 * escape analysis off) nor Kotlin/Wasm allocates a merge buffer or copy per frame (#1555).
 */
internal class StableListSort {
    private var scratch = arrayOfNulls<Any>(INITIAL_SCRATCH)

    fun <T : Any> sort(list: ArrayList<T>, comparator: Comparator<in T>) {
        val size = list.size
        if (size <= 1) return
        var start = 0
        while (start < size) {
            insertionSort(list, start, min(start + RUN, size), comparator)
            start += RUN
        }
        if (size <= RUN) return
        var width = RUN
        var used = 0
        while (width < size) {
            var left = 0
            while (left + width < size) {
                val mid = left + width
                val right = min(mid + width, size)
                if (comparator.compare(list[mid - 1], list[mid]) > 0) {
                    used = max(used, mid - left)
                    merge(list, left, mid, right, comparator)
                }
                left = right
            }
            width *= 2
        }
        if (used > 0) scratch.fill(null, 0, used)
    }

    private fun <T : Any> insertionSort(list: ArrayList<T>, start: Int, end: Int, comparator: Comparator<in T>) {
        for (index in start + 1 until end) {
            val moving = list[index]
            var at = index - 1
            while (at >= start && comparator.compare(list[at], moving) > 0) {
                list[at + 1] = list[at]
                at--
            }
            list[at + 1] = moving
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> merge(list: ArrayList<T>, left: Int, mid: Int, right: Int, comparator: Comparator<in T>) {
        val leftSize = mid - left
        if (scratch.size < leftSize) scratch = arrayOfNulls(max(leftSize, scratch.size * 2))
        for (index in 0 until leftSize) scratch[index] = list[left + index]
        var i = 0
        var j = mid
        var out = left
        while (i < leftSize && j < right) {
            val fromLeft = scratch[i] as T
            val fromRight = list[j]
            if (comparator.compare(fromLeft, fromRight) <= 0) {
                list[out++] = fromLeft
                i++
            } else {
                list[out++] = fromRight
                j++
            }
        }
        while (i < leftSize) {
            list[out++] = scratch[i++] as T
        }
    }

    private companion object {
        const val RUN = 16
        const val INITIAL_SCRATCH = 128
    }
}
