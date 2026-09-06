/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.GpuDataShape

/**
 * Packs a per-instance list into one interleaved float array for a vertex-rate instance buffer.
 *
 * Both backends wrote this loop once per buffer type -- capacity check, reuse-or-resize the
 * scratch array, write N floats per item -- identical apart from the write itself. Only the
 * upload is genuinely per-backend, so only the upload stays there.
 *
 * The stride comes from [shape] rather than a hand-written `FLOATS_PER_INSTANCE`: a `Mat4`
 * instance is 16 floats because [GpuDataShape.Mat4] says so, and declaring a differently-shaped
 * buffer cannot disagree with its own stride. Instance buffers are vertex-rate, so
 * [GpuDataShape.componentCount] is the right count -- no std140 vec3 padding, unlike a uniform
 * block.
 *
 * Not thread-safe: the scratch array is reused across frames, and every caller is the render
 * thread inside one frame.
 *
 * @param T The per-instance item type.
 * @param shape One instance's GPU value shape, which fixes the stride.
 * @param label Buffer name, for the capacity-overflow message.
 * @param repeat Instances of [shape] per item -- 1 for a plain `Mat4`, `MAX_JOINTS` for a joint
 * palette. Defaults to 1.
 * @param write Writes one item's floats at `offset`. Called once per item per frame.
 */
class InstancePacker<T>(
    shape: GpuDataShape,
    private val label: String,
    repeat: Int = 1,
    private val write: (out: FloatArray, offset: Int, item: T) -> Unit,
) {
    /** Floats one instance occupies -- derived from [shape], never hand-counted. */
    val floatsPerInstance: Int = shape.componentCount * repeat

    private var packed = FloatArray(0)

    /**
     * Packs [items] into a reused array of exactly `items.size * floatsPerInstance` floats.
     *
     * @param items This frame's instances.
     * @param maxInstances Capacity the caller's GPU buffer was allocated for.
     * @return The packed floats, or `null` when [items] is empty and there is nothing to upload.
     */
    fun pack(items: List<T>, maxInstances: Int): FloatArray? {
        require(items.size <= maxInstances) {
            "Instance count (${items.size}) exceeds $label capacity ($maxInstances) -- " +
                "raise maxInstances or draw fewer instances."
        }
        if (items.isEmpty()) return null
        val needed = items.size * floatsPerInstance
        if (packed.size != needed) packed = FloatArray(needed)
        var index = 0
        while (index < items.size) {
            write(packed, index * floatsPerInstance, items[index])
            index += 1
        }
        return packed
    }
}
