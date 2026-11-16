/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.passes.InstancePacker
import com.awakekt.awake.render.passes.InstanceWriter
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.writeBufferData
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage
import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * What a prepared draw allocates in wasm, by part (#1621). [MovingCameraStageHeapBrowserTest] puts
 * the prepare stage at about 3 KB a draw, some 30 times the JVM's. Here a moving frame is counted --
 * its draws, their instances, and how many instance buffers it uploads -- and then each part an
 * instanced draw's prepare runs is measured alone, in the same browser, at that frame's instance
 * count:
 *
 * - **upload copy**: `fastArrayBufferOf`, the JS `ArrayBuffer` an instance upload is copied into;
 * - **write**: that copy written with `queue.writeBuffer`, wrappers and all;
 * - **pack, count changed**: `InstancePacker.pack` when the count differs from last time, as it does
 *   when culling changes what a draw shows; **pack, same count** when it does not;
 * - **resident copy**: the copy an instance buffer keeps of what it last uploaded.
 *
 * Each is the median, over [WINDOWS] windows of [CALLS] calls, of the JS heap's growth per call from
 * a collected heap.
 */
class PrepareHeapBreakdownBrowserTest {

    /** Counts the draws and instances it is asked to prepare; it allocates nothing itself. */
    private class Counting(private val delegate: GpuDrawPreparer) : GpuDrawPreparer by delegate {
        var draws = 0
        var instances = 0

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw? {
            draws++
            instances += request.instanceModels?.size ?: 1
            return delegate.prepare(request, sourceIndex, context)
        }
    }

    @Test
    fun aPreparedDrawsHeapIsNamedPartByPart() = runTest(timeout = 4.minutes) {
        assertTrue(heapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val engine = BundleBrowserEngine(1)
        try {
            val renderer = engine.boot()
            val scene = MovingField(renderer)
            val preparer = Counting(requireNotNull(scene.defaultPreparer))
            repeat(MovingField.WARM_UP) { scene.draw(scene.compile(it, preparer)) }
            preparer.draws = 0
            preparer.instances = 0
            val uploadsBefore = renderer.bufferPools.instanceUploads
            repeat(MovingField.MEASURED) { scene.draw(scene.compile(MovingField.WARM_UP + it, preparer)) }
            val draws = preparer.draws / MovingField.MEASURED
            val uploads = (renderer.bufferPools.instanceUploads - uploadsBefore) / MovingField.MEASURED
            val count = maxOf(1, preparer.instances / maxOf(1, preparer.draws))

            val floats = FloatArray(count * MAT4_FLOATS) { it.toFloat() }
            val models = List(count) { Mat4().translate(it.toFloat(), 0f, 0f) }
            val oneMore = models + Mat4()
            val packer = InstancePacker<Mat4>(GpuDataShape.Mat4, "probe", write = InstanceWriter { out, offset, model -> model.data.copyInto(out, offset) })
            val device = renderer.graphicsDevice.wgpuContext.device
            val buffer = device.createBuffer(
                BufferDescriptor(size = ((count + 1) * MAT4_FLOATS * Float.SIZE_BYTES).toULong(), usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst),
            )
            var flip = false
            val copy = perCall { fastArrayBufferOf(floats) }
            val write = perCall { device.queue.writeBufferData(buffer, 0uL, fastArrayBufferOf(floats)) }
            val packChanged = perCall {
                flip = !flip
                packer.pack(if (flip) models else oneMore, MAX_INSTANCES)
            }
            val packSame = perCall { packer.pack(models, MAX_INSTANCES) }
            // The same-count pack, split (#1621): with a writer that writes nothing, the instance
            // loop alone; with a hand-written float loop, the writer without `copyInto`; and
            // `copyInto` called directly, without the packer.
            val silent = InstancePacker<Mat4>(GpuDataShape.Mat4, "probe", write = InstanceWriter { _, _, _ -> })
            val byHand = InstancePacker<Mat4>(GpuDataShape.Mat4, "probe", write = InstanceWriter { out, offset, model -> model.data.writeTo(out, offset) })
            val packSilent = perCall { silent.pack(models, MAX_INSTANCES) }
            val packByHand = perCall { byHand.pack(models, MAX_INSTANCES) }
            val copyIntoAlone = perCall { for (index in models.indices) models[index].data.copyInto(floats, index * MAT4_FLOATS) }
            val resident = perCall { floats.copyOf() }
            buffer.close()
            println(
                "PrepareHeapBreakdown: $draws draws a frame, $count instances a draw, $uploads instance uploads a frame; " +
                    "upload copy $copy B, write $write B, pack (count changed) $packChanged B, pack (same count) $packSame B, " +
                    "resident copy $resident B, a call; same-count pack split: no-op writer $packSilent B, hand-copied writer $packByHand B, " +
                    "copyInto alone $copyIntoAlone B",
            )
            assertTrue(draws > 0, "the preparer is asked for the frame's draws")
            assertTrue(engine.errors.isEmpty(), "WebGPU validation errors: ${engine.errors}")
        } finally {
            engine.destroy()
        }
    }

    /** The median heap growth a call of [block], over [WINDOWS] windows of [CALLS] calls. */
    private inline fun perCall(block: () -> Unit): Int {
        repeat(CALLS) { block() }
        val windows = DoubleArray(WINDOWS) {
            val before = collectedHeap()
            repeat(CALLS) { block() }
            (heapNow() - before) / CALLS
        }
        return windows.sortedArray()[WINDOWS / 2].toInt()
    }

    private fun FloatArray.writeTo(out: FloatArray, offset: Int) {
        var index = 0
        while (index < size) {
            out[offset + index] = this[index]
            index += 1
        }
    }

    private companion object {
        const val MAT4_FLOATS = 16
        const val MAX_INSTANCES = 4096
        const val CALLS = 200
        const val WINDOWS = 7
    }
}
