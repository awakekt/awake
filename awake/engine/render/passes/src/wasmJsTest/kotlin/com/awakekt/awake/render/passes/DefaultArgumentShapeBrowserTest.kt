/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.CullMode
import kotlin.test.Test

/**
 * Whether Kotlin/Wasm allocates for a call that leaves a parameter to its default in general, or only
 * for some shapes of parameter list (#1621): a 20-parameter `set` that left two to their defaults cost
 * 140 B a call, and 0 with every argument passed.
 */
class DefaultArgumentShapeBrowserTest {
    private val sink = IntArray(1)
    private val mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }
    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
    private val model = Mat4()
    private val vertexAnimation = Vec3f(0f, 0f, 0f)

    @Test
    fun defaultArgumentShapes() {
        if (!heapMeasurable()) return
        val figures = linkedMapOf(
            "twoInts" to medianGrowth(CALLS) { repeat(CALLS) { twoInts(it) } },
            "twoIntsAllPassed" to medianGrowth(CALLS) { repeat(CALLS) { twoInts(it, 1) } },
            "objectDefault" to medianGrowth(CALLS) { repeat(CALLS) { objectDefault(it) } },
            "lambdaDefault" to medianGrowth(CALLS) { repeat(CALLS) { lambdaDefault(it) } },
            "threeParams" to medianGrowth(CALLS) { repeat(CALLS) { three(it, it) } },
            "fourParams" to medianGrowth(CALLS) { repeat(CALLS) { four(it, it) } },
            "sixParams" to medianGrowth(CALLS) { repeat(CALLS) { six(it, it, it, it) } },
            "eightParams" to medianGrowth(CALLS) { repeat(CALLS) { eight(it, it, it, it, it, it) } },
            "eightParamsOneDefault" to medianGrowth(CALLS) { repeat(CALLS) { eight(it, it, it, it, it, it, it) } },
            "manyParams" to medianGrowth(CALLS) { repeat(CALLS) { many(it, it, it, it, it, it, it, it, it, it) } },
            "manyParamsAllPassed" to medianGrowth(CALLS) { repeat(CALLS) { many(it, it, it, it, it, it, it, it, it, it, 0, 0) } },
            "memberMany" to medianGrowth(CALLS) { repeat(CALLS) { Holder.many(it, it, it, it, it, it, it, it, it, it) } },
            "requestSetDefaults" to medianGrowth(CALLS) { repeat(CALLS) { setDefaults() } },
            "requestSetAllPassed" to medianGrowth(CALLS) { repeat(CALLS) { setAllPassed() } },
        )
        println("DefaultArgumentShape: bytes a call: " + figures.entries.joinToString { (name, b) -> "$name=${(b * 10).toInt() / 10.0}" })
    }

    private val request = RenderDrawCommand(mesh, material, model = model)

    /** The shape of the call that measured 140 B: the instance lists, blending and sort order left to their defaults. */
    private fun setDefaults() = request.set(
        mesh = mesh, material = material, model = model, vertexAnimation = vertexAnimation,
        cullMode = CullMode.Back, transparent = false, worldBounds = null, maskLayer = 0, castsShadows = true,
    )

    private fun setAllPassed() = request.set(
        mesh, material, model, EMPTY_FLOATS, vertexAnimation, 0f, null, null, null, null, CullMode.Back, AlphaMode.Opaque, 0.5f,
        false, false, null, false, 0, 0, 0, true, false,
    )

    private fun three(a: Int, b: Int, c: Int = 0) { sink[0] += a + b + c }

    private fun four(a: Int, b: Int, c: Int = 0, d: Int = 0) { sink[0] += a + b + c + d }

    private fun six(a: Int, b: Int, c: Int, d: Int, e: Int = 0, f: Int = 0) { sink[0] += a + b + c + d + e + f }

    @Suppress("LongParameterList")
    private fun eight(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int = 0, h: Int = 0) {
        sink[0] += a + b + c + d + e + f + g + h
    }

    private fun twoInts(a: Int, b: Int = 0) { sink[0] += a + b }

    private fun objectDefault(a: Int, b: Any = Unit) { sink[0] += a + b.hashCode() }

    private fun lambdaDefault(a: Int, f: (Int) -> Int = { it }) { sink[0] += f(a) }

    @Suppress("LongParameterList")
    private fun many(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int, i: Int, j: Int, k: Int = 0, l: Int = 0) {
        sink[0] += a + b + c + d + e + f + g + h + i + j + k + l
    }

    private object Holder {
        private var total = 0

        @Suppress("LongParameterList")
        fun many(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int, i: Int, j: Int, k: Int = 0, l: Int = 0) {
            total += a + b + c + d + e + f + g + h + i + j + k + l
        }
    }

    private companion object {
        const val CALLS = 100_000
        val EMPTY_FLOATS = FloatArray(0)
    }
}
