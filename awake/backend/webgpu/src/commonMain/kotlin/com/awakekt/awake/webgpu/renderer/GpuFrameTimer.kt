/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.renderer.GpuFrameTimeCounter
import com.awakekt.awake.webgpu.device.timestampPeriodNs
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.ComputePassTimestampWrites
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUCommandBufferDescriptor
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPUComputePassDescriptor
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFeatureName
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUQueryType
import io.ygdrasil.webgpu.GPURenderPassDescriptor
import io.ygdrasil.webgpu.GPURenderPassEncoder
import io.ygdrasil.webgpu.QuerySetDescriptor
import io.ygdrasil.webgpu.RenderPassTimestampWrites
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel

/**
 * Times every render/compute pass in a submission, including pre-passes and offscreen UI work.
 * Mapping is asynchronous. Only completed mappings are collected on the render thread; a busy
 * driver never stalls rendering, and a bounded readback pool prevents profiler memory growth.
 */
internal class GpuFrameTimer private constructor(private val device: GPUDevice) {
    private val counter = GpuFrameTimeCounter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val resources = ArrayList<Resources>()
    private val available = ArrayDeque<Resources>()
    private val pending = ArrayList<Readback>()
    private val nanosPerTick = device.timestampPeriodNs()

    val lastMs: Float? get() = counter.latestMs

    fun createEncoder(): GPUCommandEncoder {
        collectCompleted()
        val buffers = when {
            available.isNotEmpty() -> available.removeFirst()
            resources.size < MAX_READBACKS -> Resources(device).also { resources += it }
            else -> null
        }
        return TimedEncoder(device.createCommandEncoder(), buffers, counter.beginSubmission())
    }

    fun submitted(encoder: GPUCommandEncoder) {
        val timed = encoder as TimedEncoder
        val buffers = timed.resources
        if (buffers == null || timed.queryCount == 0) {
            counter.completeSubmission(timed.ticket, if (buffers != null) 0.0 else null)
            buffers?.let { available += it }
            return
        }
        val result = scope.async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { buffers.readback.mapAsync(GPUMapMode.Read).getOrThrow() }
        }
        pending += Readback(timed.ticket, buffers, result, timed.queryCount, timed.overflow)
    }

    fun publish() {
        collectCompleted()
        counter.publish()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun collectCompleted() {
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val readback = iterator.next()
            if (!readback.result.isCompleted) continue
            counter.completeSubmission(readback.ticket, readback.milliseconds())
            available += readback.resources
            iterator.remove()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Readback.milliseconds(): Double? {
        if (!result.getCompleted().isSuccess) return null
        // Read and unmap on the render thread, so destroy cannot race mapped-memory access.
        return runCatching {
            try {
                val bytes = resources.readback.getMappedRange().toByteArray()
                if (overflow) null else durationMs(bytes, queryCount, nanosPerTick)
            } finally {
                resources.readback.unmap()
            }
        }.getOrNull()
    }

    fun destroy() {
        scope.cancel()
        resources.forEach(Resources::close)
        resources.clear()
        available.clear()
        pending.clear()
    }

    private class Readback(
        val ticket: Long,
        val resources: Resources,
        val result: Deferred<Result<Unit>>,
        val queryCount: Int,
        val overflow: Boolean,
    )

    private class Resources(device: GPUDevice) {
        val queries = device.createQuerySet(QuerySetDescriptor(type = GPUQueryType.Timestamp, count = MAX_QUERIES.toUInt()))
        val resolve = device.createBuffer(
            BufferDescriptor(size = BUFFER_BYTES, usage = GPUBufferUsage.QueryResolve or GPUBufferUsage.CopySrc),
        )
        val readback = device.createBuffer(
            BufferDescriptor(size = BUFFER_BYTES, usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst),
        )

        fun close() {
            readback.close()
            resolve.close()
            queries.close()
        }
    }

    private class TimedEncoder(
        private val encoder: GPUCommandEncoder,
        val resources: Resources?,
        val ticket: Long,
    ) : GPUCommandEncoder by encoder {
        var queryCount = 0
            private set
        var overflow = false
            private set

        private fun nextPair(): Int? = when {
            resources == null -> null
            queryCount == MAX_QUERIES -> {
                overflow = true
                null
            }
            else -> queryCount.also { queryCount += 2 }
        }

        override fun beginRenderPass(descriptor: GPURenderPassDescriptor) = nextPair()?.let { first ->
            encoder.beginTimestampedRenderPass(object : GPURenderPassDescriptor by descriptor {
                override val timestampWrites = RenderPassTimestampWrites(resources!!.queries, first.toUInt(), (first + 1).toUInt())
            })
        } ?: encoder.beginRenderPass(descriptor)

        override fun beginComputePass(descriptor: GPUComputePassDescriptor?) = nextPair()?.let { first ->
            val base = descriptor ?: io.ygdrasil.webgpu.ComputePassDescriptor()
            encoder.beginComputePass(object : GPUComputePassDescriptor by base {
                override val timestampWrites = ComputePassTimestampWrites(resources!!.queries, first.toUInt(), (first + 1).toUInt())
            })
        } ?: encoder.beginComputePass(descriptor)

        override fun finish(descriptor: GPUCommandBufferDescriptor?) = run {
            if (resources != null && queryCount > 0) {
                encoder.resolveQuerySet(resources.queries, 0u, queryCount.toUInt(), resources.resolve, 0uL)
                encoder.copyBufferToBuffer(resources.resolve, 0uL, resources.readback, 0uL, (queryCount * Long.SIZE_BYTES).toULong())
            }
            encoder.finish(descriptor)
        }
    }

    companion object {
        fun createOrNull(device: GPUDevice): GpuFrameTimer? =
            if (GPUFeatureName.TimestampQuery in device.features) GpuFrameTimer(device) else null

        private const val MAX_READBACKS = 32
        private const val MAX_QUERIES = 256
        private val BUFFER_BYTES = (MAX_QUERIES * Long.SIZE_BYTES).toULong()

        internal fun durationMs(bytes: ByteArray, queryCount: Int, nanosPerTick: Float): Double {
            fun timestamp(offset: Int): ULong {
                var value = 0uL
                repeat(Long.SIZE_BYTES) { byte -> value = value or ((bytes[offset + byte].toULong() and 0xFFuL) shl (byte * 8)) }
                return value
            }
            var nanoseconds = 0.0
            for (query in 0 until queryCount step 2) {
                val start = timestamp(query * Long.SIZE_BYTES)
                val end = timestamp((query + 1) * Long.SIZE_BYTES)
                // Unsigned subtraction handles timestamp wrap without converting an absolute clock to Float.
                nanoseconds += (end - start).toDouble() * nanosPerTick
            }
            return nanoseconds / 1_000_000.0
        }
    }
}

/** Native wgpu4k currently omits render-pass timestamp writes from its descriptor mapping. */
internal expect fun GPUCommandEncoder.beginTimestampedRenderPass(descriptor: GPURenderPassDescriptor): GPURenderPassEncoder

internal fun Renderer.createRenderEncoder(): GPUCommandEncoder =
    gpuFrameTimer?.createEncoder() ?: graphicsDevice.wgpuContext.device.createCommandEncoder()

internal fun Renderer.submitRenderCommands(encoder: GPUCommandEncoder) {
    graphicsDevice.wgpuContext.device.queue.submit(listOf(encoder.finish()))
    gpuFrameTimer?.submitted(encoder)
}
