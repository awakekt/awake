/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.renderer.UniformFields

/**
 * Per-instance tint and fade for copies a tinting pipeline (`PipelineVariant.InstancedTinted`)
 * draws as one: what of a draw's extras is its tint, and the colours and shared extras a group of
 * differently tinted copies is drawn with.
 *
 * A draw's tint is its base colour factor's rgb; its alpha stays in the shared block, so copies
 * that fold must agree on it. Its fade is [RenderDrawCommand.fadeOpacity]. A group whose copies
 * all agree draws exactly as before, carrying no colours.
 */
internal object InstanceTints {
    private val layout = MaterialUniformLayouts.PbrTexturedMaterial
    private val tintAt = layout.offsetOf(UniformFields.BaseColorFactor)

    /** Whether [extras] carries a base colour factor where the textured material layout puts one. */
    private fun hasTint(extras: FloatArray): Boolean = extras.size >= layout.total

    private fun isTintLane(extras: FloatArray, index: Int): Boolean = hasTint(extras) && index in tintAt until tintAt + RGB

    /** [first] and [second] equal in every lane but the tint's rgb. */
    fun sameBesidesTint(first: FloatArray, second: FloatArray): Boolean {
        var same = first.size == second.size
        var index = 0
        while (same && index < first.size) {
            same = isTintLane(first, index) || first[index].toRawBits() == second[index].toRawBits()
            index += 1
        }
        return same
    }

    /** A hash of [extras] that leaves the tint's rgb out, so copies differing only there hash alike. */
    fun hashBesidesTint(extras: FloatArray): Int {
        var hash = extras.size
        for (index in extras.indices) if (!isTintLane(extras, index)) hash = hash * HASH_STEP + extras[index].toRawBits()
        return hash
    }

    /** Whether every one of [members] has the first's tint and fade. */
    fun allAlike(members: List<RenderDrawCommand>): Boolean {
        val first = members[0]
        for (index in 1 until members.size) {
            val draw = members[index]
            if (draw.fadeOpacity != first.fadeOpacity || !sameTint(first.extraUniformFloats, draw.extraUniformFloats)) return false
        }
        return true
    }

    private fun sameTint(first: FloatArray, second: FloatArray): Boolean =
        !hasTint(first) || (0 until RGB).all { lane -> first[tintAt + lane].toRawBits() == second[tintAt + lane].toRawBits() }

    /** [into] set to [draw]'s tint rgb and fade: white and 1 for a draw without a tint lane. */
    fun write(draw: RenderDrawCommand, into: Vec4) {
        val extras = draw.extraUniformFloats
        if (hasTint(extras)) {
            into.x = extras[tintAt]
            into.y = extras[tintAt + 1]
            into.z = extras[tintAt + 2]
        } else {
            into.x = 1f
            into.y = 1f
            into.z = 1f
        }
        into.w = draw.fadeOpacity.coerceIn(0f, 1f)
    }

    /** [into], resized to [extras], holding [extras] with a white tint: the block a tinted group shares. */
    fun whiteTinted(extras: FloatArray, into: FloatArray): FloatArray {
        val shared = if (into.size == extras.size) into else FloatArray(extras.size)
        extras.copyInto(shared)
        if (hasTint(extras)) for (lane in 0 until RGB) shared[tintAt + lane] = 1f
        return shared
    }

    private const val RGB = 3
    private const val HASH_STEP = 31
}

/** One group's per-instance colours and shared block, kept from frame to frame so tinting allocates nothing once warm. */
internal class GroupTints {
    private val colors = ArrayList<Vec4>()
    private val pool = ArrayList<Vec4>()
    private var shared = FloatArray(0)

    /** One tint and fade per member, in order: valid until the next call. */
    fun colorsOf(members: List<RenderDrawCommand>): List<Vec4> {
        colors.clear()
        for (index in members.indices) {
            if (index == pool.size) pool += Vec4()
            val color = pool[index]
            InstanceTints.write(members[index], color)
            colors += color
        }
        return colors
    }

    /** [extras] with a white tint, in a buffer kept for the next frame. */
    fun sharedExtras(extras: FloatArray): FloatArray {
        shared = InstanceTints.whiteTinted(extras, shared)
        return shared
    }
}
