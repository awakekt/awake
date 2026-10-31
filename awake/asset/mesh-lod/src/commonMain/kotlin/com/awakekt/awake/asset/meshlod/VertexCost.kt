/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

/** What replacing one wedge's attributes with another's costs: weighted squared attribute and skin differences. */
internal class VertexCost(
    private val attributes: List<MeshAttribute>,
    private val skin: SkinInfluences?,
    private val importance: FloatArray?,
) {
    /** The cost multiplier for moving wedge [wedge]: one plus its importance. */
    fun scale(wedge: Int): Double = 1.0 + (importance?.get(wedge) ?: 0f)

    /** The cost of wedge [from] taking wedge [to]'s attributes. */
    fun between(from: Int, to: Int): Double {
        if (from == to) return 0.0
        var cost = 0.0
        for (attribute in attributes) {
            if (attribute.weight == 0f) continue
            val n = attribute.components
            var sum = 0.0
            for (c in 0 until n) {
                val delta = (attribute.values[from * n + c] - attribute.values[to * n + c]).toDouble()
                sum += delta * delta
            }
            cost += sum * attribute.weight
        }
        if (skin != null && skin.weight != 0f) cost += skinDistance(skin, from, to) * skin.weight
        return cost
    }

    /** The squared difference of two vertices' weights, joint by joint, whatever slot each joint is in. */
    private fun skinDistance(skin: SkinInfluences, a: Int, b: Int): Double {
        val n = skin.influences
        var sum = 0.0
        for (i in 0 until n) {
            val weight = skin.weights[a * n + i]
            if (weight == 0f) continue
            val delta = weight - weightOf(skin, b, skin.joints[a * n + i])
            sum += delta * delta
        }
        for (i in 0 until n) {
            val weight = skin.weights[b * n + i]
            if (weight == 0f || weightOf(skin, a, skin.joints[b * n + i]) != 0f) continue
            sum += weight * weight
        }
        return sum
    }

    private fun weightOf(skin: SkinInfluences, vertex: Int, joint: Int): Float {
        val n = skin.influences
        var total = 0f
        for (i in 0 until n) if (skin.joints[vertex * n + i] == joint) total += skin.weights[vertex * n + i]
        return total
    }
}
