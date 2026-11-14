/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.pipeline.CullMode
import kotlin.math.max
import kotlin.math.min

/**
 * Working memory [ScenePassCompiler.compile] keeps from one frame to the next. A caller that
 * compiles every frame, such as a scene's planner, holds one and passes it each time; what a
 * compile returns stays valid until that scratch's next compile.
 */
class ScenePassScratch {
    internal val batcher = InstanceBatcher()
}

/**
 * [batchInstances] with its working memory kept from frame to frame, so a frame that folds the
 * same groups as the last one allocates nothing: no key per draw, no map entry, list or copied
 * request per group.
 *
 * Groups are found through an open-addressed table of group indices, cleared each frame, and
 * compared with [instancesWith] against the group's first draw, which stays valid for the frame.
 * Each group keeps its member list, its models list and the instanced request it fills, so their
 * capacity is grown once, the first frame a scene reaches its size.
 *
 * What [batch] returns, and the instanced requests in it, are valid until the next [batch]:
 * a backend reads them while it prepares the frame, copying the models into its own buffer.
 */
internal class InstanceBatcher {
    private val groups = ArrayList<InstanceGroup>()
    private var groupCount = 0
    private var table = IntArray(INITIAL_TABLE)
    private val kept = ArrayList<RenderDrawCommand>()
    private var preparer: GpuDrawPreparer? = null
    private var canInstance: (VertexFormat, CullMode) -> Boolean = { _, _ -> false }

    /** [preparer]'s answer as a function, built once per preparer rather than once a frame. */
    fun canInstanceWith(preparer: GpuDrawPreparer): (VertexFormat, CullMode) -> Boolean {
        if (preparer !== this.preparer) {
            this.preparer = preparer
            canInstance = { format, cullMode -> preparer.canInstance(format, cullMode) }
        }
        return canInstance
    }

    fun batch(drawCalls: List<RenderDrawCommand>, canInstance: (VertexFormat, CullMode) -> Boolean): List<RenderDrawCommand> {
        groupCount = 0
        table.fill(0)
        kept.clear()
        // Entities spawned together iterate together, so a draw usually joins the previous draw's group.
        var last: InstanceGroup? = null
        for (index in drawCalls.indices) {
            val draw = drawCalls[index]
            if (!draw.foldsIntoInstances(canInstance)) {
                kept.addSplitToCapacity(draw)
                continue
            }
            val group = last?.takeIf { it.members[it.members.size - 1].instancesWith(draw) } ?: groupOf(draw)
            group.members += draw
            last = group
        }
        for (index in 0 until groupCount) groups[index].emitInto(kept)
        return kept
    }

    private fun groupOf(draw: RenderDrawCommand): InstanceGroup {
        if (groupCount * 2 >= table.size) growTable()
        val mask = table.size - 1
        var slot = draw.instanceHash() and mask
        while (true) {
            val stored = table[slot]
            if (stored == 0) break
            val group = groups[stored - 1]
            if (group.members[0].instancesWith(draw)) return group
            slot = (slot + 1) and mask
        }
        if (groupCount == groups.size) groups += InstanceGroup()
        val group = groups[groupCount++]
        group.members.clear()
        table[slot] = groupCount
        return group
    }

    private fun growTable() {
        table = IntArray(table.size * 2)
        val mask = table.size - 1
        for (index in 0 until groupCount) {
            var slot = groups[index].members[0].instanceHash() and mask
            while (table[slot] != 0) slot = (slot + 1) and mask
            table[slot] = index + 1
        }
    }

    private companion object {
        const val INITIAL_TABLE = 256
    }
}

/** One frame's set of draws that render alike but for placement, and the instanced draws made of them. */
private class InstanceGroup {
    val members = ArrayList<RenderDrawCommand>()
    private val models = ArrayList<Mat4>()
    private val instanced = ArrayList<RenderDrawCommand>()
    private val bounds = ArrayList<Aabb>()

    fun emitInto(out: MutableList<RenderDrawCommand>) {
        if (members.size == 1) {
            out += members[0]
            return
        }
        models.clear()
        for (index in members.indices) models += members[index].model
        var chunk = 0
        for (from in members.indices step MAX_BATCHED_INSTANCES) {
            val to = min(from + MAX_BATCHED_INSTANCES, members.size)
            val chunkModels = if (from == 0 && to == members.size) models else models.subList(from, to)
            out += instancedChunk(chunk++, from, to, chunkModels)
        }
    }

    private fun instancedChunk(chunk: Int, from: Int, to: Int, chunkModels: List<Mat4>): RenderDrawCommand {
        if (chunk == instanced.size) {
            instanced += RenderDrawCommand(members[0].mesh, members[0].material)
            bounds += Aabb(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f))
        }
        val draw = instanced[chunk]
        draw.copyFrom(members[0])
        draw.model.identity()
        draw.instanceModels = chunkModels
        draw.worldBounds = enclose(from, to, bounds[chunk])
        return draw
    }

    /** [into] set around every member's bounds in [from] until [to], or null when any is unbounded. */
    private fun enclose(from: Int, to: Int, into: Aabb): Aabb? {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        for (index in from until to) {
            val box = members[index].worldBounds ?: return null
            minX = min(minX, box.min.x)
            minY = min(minY, box.min.y)
            minZ = min(minZ, box.min.z)
            maxX = max(maxX, box.max.x)
            maxY = max(maxY, box.max.y)
            maxZ = max(maxZ, box.max.z)
        }
        into.min.x = minX
        into.min.y = minY
        into.min.z = minZ
        into.max.x = maxX
        into.max.y = maxY
        into.max.z = maxZ
        return into
    }
}

/** Every field of [source] but the model, which the caller sets, into this pooled request. */
private fun RenderDrawCommand.copyFrom(source: RenderDrawCommand) {
    set(
        mesh = source.mesh, material = source.material, model = model, extraUniformFloats = source.extraUniformFloats,
        vertexAnimation = source.vertexAnimation, timeSeconds = source.timeSeconds, instanceModels = source.instanceModels,
        instanceJointPalettes = source.instanceJointPalettes, instanceColors = source.instanceColors,
        instanceFrames = source.instanceFrames, cullMode = source.cullMode, alphaMode = source.alphaMode,
        alphaCutoff = source.alphaCutoff, transparent = source.transparent, shadowsOnly = source.shadowsOnly,
        worldBounds = source.worldBounds, additive = source.additive, sortOrder = source.sortOrder,
        maskLayer = source.maskLayer, sortLayer = source.sortLayer, castsShadows = source.castsShadows,
        exemptFromCoverings = source.exemptFromCoverings,
    )
    fadeOpacity = source.fadeOpacity
    depthWrite = source.depthWrite
}
