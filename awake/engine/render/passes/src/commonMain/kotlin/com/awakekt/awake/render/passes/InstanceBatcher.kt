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
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.pipeline.CullMode
import kotlin.math.max
import kotlin.math.min

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
internal class InstanceBatcher(private val rule: FoldRule = SceneInstanceFold) {
    private val groups = ArrayList<InstanceGroup>()
    private var groupCount = 0
    private var table = IntArray(INITIAL_TABLE)
    private val kept = ArrayList<RenderDrawCommand>()
    private val others = ArrayList<RenderDrawCommand>()
    internal val order = InstanceGroupOrder()
    private var preparer: GpuDrawPreparer? = null
    private var canInstance: (VertexFormat, CullMode) -> Boolean = { _, _ -> false }
    private var tintPreparer: GpuDrawPreparer? = null
    private var canTint: (VertexFormat, CullMode) -> Boolean = NO_TINTS
    private var skinnedPreparer: GpuDrawPreparer? = null
    private var canInstanceSkinned: (VertexFormat, CullMode) -> Boolean = NO_SKINNED

    /** [preparer]'s answer to whether a pipeline tints instances, as a function built once per preparer. */
    fun canTintWith(preparer: GpuDrawPreparer): (VertexFormat, CullMode) -> Boolean {
        if (preparer !== tintPreparer) {
            tintPreparer = preparer
            canTint = { format, cullMode -> preparer.canTintInstances(format, cullMode) }
        }
        return canTint
    }

    /** [preparer]'s answer as a function, built once per preparer rather than once a frame. */
    fun canInstanceWith(preparer: GpuDrawPreparer): (VertexFormat, CullMode) -> Boolean {
        if (preparer !== this.preparer) {
            this.preparer = preparer
            canInstance = { format, cullMode -> preparer.canInstance(format, cullMode) }
        }
        return canInstance
    }

    /** [preparer]'s answer to whether posed single skinned draws can batch, built once per preparer. */
    fun canInstanceSkinnedWith(preparer: GpuDrawPreparer): (VertexFormat, CullMode) -> Boolean {
        if (preparer !== skinnedPreparer) {
            skinnedPreparer = preparer
            canInstanceSkinned = { format, cullMode -> preparer.canInstanceSkinned(format, cullMode) }
        }
        return canInstanceSkinned
    }

    /**
     * Folds [drawCalls] as [canInstance] and [canInstanceSkinned] allow; where [canTint] says a
     * draw's pipeline reads a tint per instance, copies that differ only in tint and fade fold too.
     */
    fun batch(
        drawCalls: List<RenderDrawCommand>,
        canInstance: (VertexFormat, CullMode) -> Boolean,
        canTint: (VertexFormat, CullMode) -> Boolean = NO_TINTS,
        canInstanceSkinned: (VertexFormat, CullMode) -> Boolean = NO_SKINNED,
    ): List<RenderDrawCommand> {
        groupCount = 0
        table.fill(0)
        kept.clear().also { others.clear() }
        // Entities spawned together iterate together, so a draw usually joins the previous draw's group.
        var last: InstanceGroup? = null
        var askedFormat: VertexFormat? = null
        var askedCull = CullMode.None
        var instances = false
        var tinted = false
        var skinnedInstances = false
        for (index in 0 until drawCalls.size) {
            val draw = drawCalls[index]
            val format = draw.mesh.format
            if (format !== askedFormat || draw.cullMode != askedCull) {
                askedFormat = format
                askedCull = draw.cullMode
                instances = canInstance(format, askedCull)
                tinted = canTint(format, askedCull)
                skinnedInstances = canInstanceSkinned(format, askedCull)
            }
            val foldsPlain = rule.folds(draw, instances)
            val foldsSkinned = !foldsPlain && rule.foldsSkinned(draw, skinnedInstances)
            if (!foldsPlain && !foldsSkinned) {
                if (rule.keepsOthers) others.addSplitToCapacity(draw)
                continue
            }
            val tints = if (foldsSkinned) false else tinted
            val group = resolveGroup(last, draw, tints, foldsSkinned)
            group.add(draw, index)
            last = group
        }
        order.emit(groups, groupCount) { it.emitInto(kept, rule.keepsOthers) }
        for (index in 0 until others.size) kept += others[index]
        return kept
    }

    private fun resolveGroup(
        last: InstanceGroup?,
        draw: RenderDrawCommand,
        tints: Boolean,
        skinned: Boolean,
    ): InstanceGroup {
        val reuse = last != null &&
            last.skinned == skinned &&
            last.tints == tints &&
            rule.matches(last.members[last.members.size - 1], draw, tints, skinned)
        return if (reuse) last else groupOf(draw, tints, skinned)
    }

    /** Calls [action] with the index, in the last [batch]'s input, of every draw folded with another. */
    inline fun forEachFolded(action: (Int) -> Unit) {
        for (index in 0 until groupCount) groups[index].forEachFoldedSource(action)
    }

    private fun groupOf(draw: RenderDrawCommand, tints: Boolean, skinned: Boolean): InstanceGroup {
        if (groupCount * 2 >= table.size) growTable()
        val mask = table.size - 1
        var slot = rule.hashFor(draw, skinned) and mask
        while (true) {
            val stored = table[slot]
            if (stored == 0) break
            val group = groups[stored - 1]
            if (group.skinned == skinned && group.tints == tints && rule.matches(group.members[0], draw, tints, skinned)) {
                return group
            }
            slot = (slot + 1) and mask
        }
        if (groupCount == groups.size) groups += InstanceGroup()
        val group = groups[groupCount++]
        group.clear()
        group.tints = tints
        group.skinned = skinned
        table[slot] = groupCount
        return group
    }

    private fun growTable() {
        table = IntArray(table.size * 2)
        val mask = table.size - 1
        for (index in 0 until groupCount) {
            val group = groups[index]
            var slot = rule.hashFor(group.members[0], group.skinned) and mask
            while (table[slot] != 0) slot = (slot + 1) and mask
            table[slot] = index + 1
        }
    }

    private companion object {
        const val INITIAL_TABLE = 256
        val NO_TINTS: (VertexFormat, CullMode) -> Boolean = { _, _ -> false }
        val NO_SKINNED: (VertexFormat, CullMode) -> Boolean = { _, _ -> false }
    }
}

/** One frame's set of draws that render alike but for placement, and the instanced draws made of them. */
internal class InstanceGroup {
    val members = ArrayList<RenderDrawCommand>()

    /** Whether this frame's members may differ in tint and fade: their pipeline reads them per instance. */
    var tints = false

    /** Whether this frame's members are single posed skinned draws folded into skinned-instanced chunks. */
    var skinned = false

    var place = 0 // This frame's place in [InstanceGroupOrder].
    private val groupTints = GroupTints()
    private val groupSkinned = GroupSkinnedData()
    private var sources = IntArray(INITIAL_SOURCES)
    private val models = ArrayList<Mat4>()
    private val instanced = ArrayList<RenderDrawCommand>()
    private val bounds = ArrayList<Aabb>()

    fun clear() {
        members.clear()
        tints = false
        skinned = false
    }

    fun add(draw: RenderDrawCommand, source: Int) {
        if (members.size == sources.size) sources = sources.copyOf(sources.size * 2)
        sources[members.size] = source
        members += draw
    }

    inline fun forEachFoldedSource(action: (Int) -> Unit) {
        if (members.size > 1) for (index in 0 until members.size) action(sourceAt(index))
    }

    fun sourceAt(index: Int): Int = sources[index]

    private companion object {
        const val INITIAL_SOURCES = 16
    }

    /** One draw for the group, or several past one draw's capacity; a lone member as itself where [keepsSingles]. */
    fun emitInto(out: MutableList<RenderDrawCommand>, keepsSingles: Boolean) {
        if (members.size == 1) {
            if (keepsSingles) out += members[0]
            return
        }
        if (skinned) {
            emitSkinnedInto(out)
            return
        }
        models.clear()
        for (index in 0 until members.size) {
            val draw = members[index]
            val copies = draw.instanceModels
            if (copies == null) models += draw.model else for (copy in 0 until copies.size) models += copies[copy]
        }
        // One model per member, so a chunk's bounds are its members'; else every chunk takes the group's.
        val perMember = models.size == members.size
        // Copies that differ in tint or fade carry theirs per instance, over a block whose tint is white.
        val colors = if (tints && !InstanceTints.allAlike(members)) groupTints.colorsOf(members) else null
        var chunk = 0
        var from = 0
        while (from < models.size) {
            val to = min(from + MAX_BATCHED_INSTANCES, models.size)
            val chunkModels = if (from == 0 && to == models.size) models else models.subList(from, to)
            val draw = if (perMember) instancedChunk(chunk++, from, to, chunkModels) else instancedChunk(chunk++, 0, members.size, chunkModels)
            if (colors != null) tint(draw, colors, from, to)
            out += draw
            from = to
        }
    }

    /** Emits posed single skinned members as skinned-instanced chunks capped at [MAX_SKINNED_BATCHED_INSTANCES]. */
    private fun emitSkinnedInto(out: MutableList<RenderDrawCommand>) {
        models.clear()
        for (index in 0 until members.size) models += members[index].model
        val palettes = groupSkinned.palettesOf(members)
        val colors = groupSkinned.colorsOf(members)
        val sharedExtras = groupSkinned.sharedExtras(members[0])
        var chunk = 0
        var from = 0
        while (from < models.size) {
            val to = min(from + MAX_SKINNED_BATCHED_INSTANCES, models.size)
            val whole = from == 0 && to == models.size
            val chunkModels = if (whole) models else models.subList(from, to)
            val draw = instancedChunk(chunk++, from, to, chunkModels)
            draw.instanceJointPalettes = if (whole) palettes else palettes.subList(from, to)
            draw.instanceColors = colors?.let { if (whole) it else it.subList(from, to) }
            draw.extraUniformFloats = sharedExtras
            out += draw
            from = to
        }
    }

    /** [draw] carrying its copies' own tints and fades, from [from] until [to], over a white-tinted, unfaded block. */
    private fun tint(draw: RenderDrawCommand, colors: List<Vec4>, from: Int, to: Int) {
        draw.extraUniformFloats = groupTints.sharedExtras(members[0].extraUniformFloats)
        draw.fadeOpacity = 1f
        draw.instanceColors = if (from == 0 && to == colors.size) colors else colors.subList(from, to)
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
    surfaceDepositScale = source.surfaceDepositScale
    surfaceDepositSeed = source.surfaceDepositSeed
}
