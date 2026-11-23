/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline.bundles

import com.awakekt.awake.webgpu.mesh.meshIndexFormat
import com.awakekt.awake.webgpu.pipeline.PassBindings
import com.awakekt.awake.webgpu.pipeline.bindGroup
import com.awakekt.awake.webgpu.writeBufferInts
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPURenderBundle
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.RenderBundleDescriptor
import io.ygdrasil.webgpu.RenderBundleEncoderDescriptor

/** What a pass's bundles must match: its attachments' formats and sample count. */
internal data class BundleLayout(val colorFormats: List<GPUTextureFormat>, val depthFormat: GPUTextureFormat?, val sampleCount: Int)

/** Bundles encoded and argument buffers rewritten, over a store's life: for tests and probes. */
internal class BundleCounts {
    var encoded = 0L
    var argumentWrites = 0L

    /** Argument buffers created: once a pass's pool is warm, re-recording a cell creates none. */
    var argsBuffersCreated = 0L
}

/** An indirect-arguments buffer and the words staged for it, room for [draws] draws; pooled by that size. */
internal class ArgsBuffer(val buffer: GPUBuffer, val words: IntArray, val draws: Int)

/** A cell's render bundle and the indirect arguments its draws read, five words a draw. */
internal class RetainedBundle(val bundle: GPURenderBundle, val args: ArgsBuffer)

/**
 * The retained regions of one pass, by the key the shared feature opens each with, kept across
 * frames. A cell's draws are recorded as `drawIndexedIndirect`/`drawIndirect` reading its own
 * arguments buffer, so a frame that changes only instance counts writes that buffer once and
 * reuses the bundle. `firstInstance` is always 0: each draw keeps its own instance buffers, so
 * nothing needs the optional `indirect-first-instance` feature.
 */
internal class RetainedPassBundles(
    private val device: GPUDevice,
    val layout: BundleLayout,
    private val counts: BundleCounts = BundleCounts(),
) {
    private val regions = HashMap<Any, RetainedCells<RetainedBundle>>()

    private val executing = ArrayList<GPURenderBundle>()

    /**
     * Argument buffers of released bundles, by draw capacity, for the next cell recorded. A moving
     * camera re-records cells every frame, and each new buffer was a new wgpu4k `Buffer`, whose
     * web wrapper builds a logger by capturing and parsing a stack trace: thousands a second on the
     * consumer's frame (#1555). A released bundle's buffer was last read by work already submitted,
     * and `writeBuffer` is ordered after it, so reusing it is safe.
     */
    private val freeArgs = HashMap<Int, ArrayDeque<ArgsBuffer>>()

    /** When this pass last ran, in [RetainedBundleStore]'s count, so a pass no longer drawn is released. */
    var lastUse = 0L

    fun region(key: Any): RetainedCells<RetainedBundle> = regions.getOrPut(key) { RetainedCells(::record, ::writeCounts, ::release) }

    /** Settles [region]'s cells for this frame; the bundles to execute, in a list reused every frame. */
    fun settle(region: RetainedCells<RetainedBundle>): List<GPURenderBundle> {
        executing.clear()
        region.end().forEach { executing += it.bundle }
        return executing
    }

    fun destroy() {
        regions.values.forEach(RetainedCells<RetainedBundle>::clear)
        regions.clear()
        // Clearing released every bundle's buffer into the pool: close them all.
        freeArgs.values.forEach { pooled -> pooled.forEach { it.buffer.close() } }
        freeArgs.clear()
    }

    private fun record(stream: CellStream): RetainedBundle {
        val args = argsFor(maxOf(stream.drawCount, 1))
        val bundle = RetainedBundle(encode(stream, args.buffer), args)
        counts.encoded += 1
        writeArguments(bundle, stream)
        return bundle
    }

    /** A pooled buffer with room for [draws] draws, rounded up to a power of two, or a new one. */
    private fun argsFor(draws: Int): ArgsBuffer {
        val capacity = if (draws <= 1) 1 else (draws - 1).takeHighestOneBit() shl 1
        freeArgs[capacity]?.removeLastOrNull()?.let { return it }
        counts.argsBuffersCreated += 1
        val words = IntArray(capacity * ARGS_WORDS)
        val buffer = device.createBuffer(
            BufferDescriptor(size = (words.size * Int.SIZE_BYTES).toULong(), usage = GPUBufferUsage.Indirect or GPUBufferUsage.CopyDst),
        )
        return ArgsBuffer(buffer, words, capacity)
    }

    private fun encode(stream: CellStream, args: GPUBuffer): GPURenderBundle {
        val encoder = device.createRenderBundleEncoder(
            RenderBundleEncoderDescriptor(colorFormats = layout.colorFormats, depthStencilFormat = layout.depthFormat, sampleCount = layout.sampleCount.toUInt()),
        )
        val bound = PassBindings()
        for (index in 0 until stream.size) {
            val slot = stream.slots[index]
            val ref = stream.refs[index]
            when (stream.ops[index]) {
                CellStream.PIPELINE -> encoder.setPipeline(ref as GPURenderPipeline).also { bound.forget() }
                CellStream.GROUP -> if (bound.bindGroup(slot, ref!!)) encoder.bindGroup(slot.toUInt(), ref as GPUBindGroup)
                CellStream.VERTEX -> if (bound.bindVertexBuffer(slot, ref!!)) encoder.setVertexBuffer(slot.toUInt(), ref as GPUBuffer)
                CellStream.INDEX -> if (bound.bindIndexBuffer(ref!!)) encoder.setIndexBuffer(ref as GPUBuffer, meshIndexFormat)
                CellStream.DRAW_INDEXED -> encoder.drawIndexedIndirect(args, (slot * ARGS_BYTES).toULong())
                CellStream.DRAW -> encoder.drawIndirect(args, (slot * ARGS_BYTES).toULong())
            }
        }
        return encoder.use { it.finish(RenderBundleDescriptor()) }
    }

    /** A kept bundle whose draws' counts alone changed. */
    private fun writeCounts(bundle: RetainedBundle, stream: CellStream) {
        counts.argumentWrites += 1
        writeArguments(bundle, stream)
    }

    /** One `writeBuffer` of the cell's arguments: each draw's element and instance count, the rest 0. */
    private fun writeArguments(bundle: RetainedBundle, stream: CellStream) {
        val words = bundle.args.words
        // A pooled buffer may hold a larger cell's draws past this one's: those are never drawn.
        for (draw in 0 until stream.drawCount) {
            words[draw * ARGS_WORDS] = stream.counts[draw * 2]
            words[draw * ARGS_WORDS + 1] = stream.counts[draw * 2 + 1]
        }
        device.queue.writeBufferInts(bundle.args.buffer, 0uL, words)
    }

    private fun release(bundle: RetainedBundle) {
        freeArgs.getOrPut(bundle.args.draws) { ArrayDeque() }.addLast(bundle.args)
        bundle.bundle.release()
    }

    private companion object {
        /** `drawIndexedIndirect`'s five words; `drawIndirect` reads the first four of the same stride. */
        const val ARGS_WORDS = 5
        const val ARGS_BYTES = ARGS_WORDS * Int.SIZE_BYTES
    }
}
