/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(io.ygdrasil.webgpu.WGPULowLevelApi::class)

package com.awakekt.awake.webgpu.renderer

import io.ygdrasil.kffi.memoryScope
import io.ygdrasil.webgpu.CommandEncoder
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPURenderPassDescriptor
import io.ygdrasil.webgpu.GPURenderPassEncoder
import io.ygdrasil.webgpu.QuerySet
import io.ygdrasil.webgpu.RenderPassEncoder
import io.ygdrasil.webgpu.TextureView
import io.ygdrasil.wgpu.WGPUPassTimestampWrites
import io.ygdrasil.wgpu.WGPURenderPassColorAttachment
import io.ygdrasil.wgpu.WGPURenderPassDepthStencilAttachment
import io.ygdrasil.wgpu.WGPURenderPassDescriptor
import io.ygdrasil.wgpu.toUInt
import io.ygdrasil.wgpu.wgpuCommandEncoderBeginRenderPass

/**
 * Compatibility mapping for the desktop test target's pinned wgpu4k version, whose native
 * render-pass mapper omits timestampWrites. Use the real pass boundaries without extra commands.
 * Remove this mapping when the upstream native mapper supports render-pass timestamps.
 */
internal actual fun GPUCommandEncoder.beginTimestampedRenderPass(descriptor: GPURenderPassDescriptor): GPURenderPassEncoder =
    memoryScope { arena ->
        val pass = WGPURenderPassDescriptor.allocate(arena)
        if (descriptor.colorAttachments.isNotEmpty()) {
            pass.colorAttachmentCount = descriptor.colorAttachments.size.toULong()
            val colors = WGPURenderPassColorAttachment.allocateArray(arena, pass.colorAttachmentCount.toUInt()) { index, target ->
                val source = descriptor.colorAttachments[index.toInt()]
                target.view = (source.view as TextureView).handler
                target.loadOp = source.loadOp.value
                target.storeOp = source.storeOp.value
                target.depthSlice = source.depthSlice ?: UInt.MAX_VALUE
                source.resolveTarget?.let { target.resolveTarget = (it as TextureView).handler }
                source.clearValue?.let {
                    target.clearValue.r = it.r
                    target.clearValue.g = it.g
                    target.clearValue.b = it.b
                    target.clearValue.a = it.a
                }
            }
            pass.colorAttachments = WGPURenderPassColorAttachment(colors.handler)
        }
        descriptor.depthStencilAttachment?.let { source ->
            pass.depthStencilAttachment = WGPURenderPassDepthStencilAttachment.allocate(arena).also { target ->
                target.view = (source.view as TextureView).handler
                source.depthClearValue?.let { target.depthClearValue = it }
                source.depthLoadOp?.let { target.depthLoadOp = it.value }
                source.depthStoreOp?.let { target.depthStoreOp = it.value }
                target.depthReadOnly = source.depthReadOnly.toUInt()
                target.stencilClearValue = source.stencilClearValue
                source.stencilLoadOp?.let { target.stencilLoadOp = it.value }
                source.stencilStoreOp?.let { target.stencilStoreOp = it.value }
                target.stencilReadOnly = source.stencilReadOnly.toUInt()
            }
        }
        descriptor.occlusionQuerySet?.let { pass.occlusionQuerySet = (it as QuerySet).handler }
        val writes = checkNotNull(descriptor.timestampWrites)
        pass.timestampWrites = WGPUPassTimestampWrites.allocate(arena).also {
            it.querySet = (writes.querySet as QuerySet).handler
            it.beginningOfPassWriteIndex = checkNotNull(writes.beginningOfPassWriteIndex)
            it.endOfPassWriteIndex = checkNotNull(writes.endOfPassWriteIndex)
        }
        RenderPassEncoder(checkNotNull(wgpuCommandEncoderBeginRenderPass((this as CommandEncoder).handler, pass)), descriptor.label)
    }
