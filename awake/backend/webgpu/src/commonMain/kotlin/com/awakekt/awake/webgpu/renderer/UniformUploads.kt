/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUQueue

/**
 * A frame's per-draw uniform blocks, each as the shared writer packed it, staged in one array and
 * written to their buffers together before the frame records.
 *
 * On the web a block written straight from a Kotlin array crossed into JS once per float; staged,
 * the whole frame is one bulk copy and then one write per buffer. A staged block still lands
 * before the next submit, as the direct write did, and the arrays are reused frame to frame.
 */
internal class UniformUploads {
    /** The staged blocks back to back; only the first [floatCount] are this frame's. */
    internal var floats = FloatArray(INITIAL_FLOATS)
        private set
    internal var floatCount = 0
        private set

    /** Block `i` is `floats[starts[i] until starts[i + 1]]`, written to `buffers[i]` at offset 0. */
    internal var starts = IntArray(INITIAL_BLOCKS + 1)
        private set
    internal val buffers = ArrayList<GPUBuffer>(INITIAL_BLOCKS)

    /** Native bytes the JVM copies [floats] into before writing; the web writes from linear memory instead. */
    internal var hostBytes: ArrayBuffer? = null

    /** Stages [block] to be written to the start of [buffer] at the next [flush]. */
    fun stage(buffer: GPUBuffer, block: FloatArray) {
        val end = floatCount + block.size
        if (end > floats.size) floats = floats.copyOf(maxOf(end, floats.size * 2))
        if (buffers.size + 2 > starts.size) starts = starts.copyOf(starts.size * 2)
        block.copyInto(floats, floatCount)
        buffers += buffer
        floatCount = end
        starts[buffers.size] = end
    }

    /** Writes every staged block to its buffer and empties the stage. */
    fun flush(queue: GPUQueue) {
        if (buffers.isEmpty()) return
        queue.writeStaged(this)
        buffers.clear()
        floatCount = 0
    }

    private companion object {
        const val INITIAL_BLOCKS = 256
        const val INITIAL_FLOATS = INITIAL_BLOCKS * 64
    }
}

/** Writes the draws staged so far. Called before anything records them, and before a new batch restages slots. */
internal fun Renderer.flushUniformUploads() = uniformUploads.flush(graphicsDevice.wgpuContext.device.queue)

/** Copies [uploads]' staged floats out of Kotlin in one pass, then writes each block to its buffer. */
internal expect fun GPUQueue.writeStaged(uploads: UniformUploads)
