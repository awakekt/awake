/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.handles

import kotlin.jvm.JvmInline

/**
 * Local copy of `awake-backend-vulkan`'s `handles/Handles.kt` (Module restructuring slice 2,
 * see docs/mvp-plan.md) -- these 9 tiny value classes are duplicated rather than shared
 * across a module dependency, since the whole point of physically splitting the Vulkan and
 * WebGPU backends is that neither depends on the other. [WebGpuHandles] uses these the same
 * way the Vulkan backend's real handle-owning classes do, just wrapping a table index instead
 * of a raw Vulkan handle.
 *
 * Wraps the table id of a GPU buffer (vertex, index or wireframe-index) that
 * [com.awakekt.awake.webgpu.mesh.Mesh] registered.
 *
 * @property handle The id [com.awakekt.awake.webgpu.WebGpuHandles.register] returned for the
 * buffer.
 */
@JvmInline
value class BufferHandle(val handle: Long)

/**
 * Stand-in for a Vulkan device-memory allocation. WebGPU manages buffer memory internally, so
 * [com.awakekt.awake.webgpu.mesh.Mesh] simply repeats its buffer's table id here.
 *
 * @property handle The same table id as the buffer this "memory" backs.
 */
@JvmInline
value class DeviceMemoryHandle(val handle: Long)

/**
 * Vulkan-parity placeholder for an image object. This backend never creates or reads one;
 * textures are held as `GPUTexture` objects directly.
 *
 * @property handle Unused; would be a [com.awakekt.awake.webgpu.WebGpuHandles] table id.
 */
@JvmInline
value class ImageHandle(val handle: Long)

/**
 * Vulkan-parity placeholder for an image view. This backend never creates or reads one;
 * texture views are held as `GPUTextureView` objects directly.
 *
 * @property handle Unused; would be a [com.awakekt.awake.webgpu.WebGpuHandles] table id.
 */
@JvmInline
value class ImageViewHandle(val handle: Long)

/**
 * Vulkan-parity placeholder for a sampler. This backend never creates or reads one; samplers
 * are held as `GPUSampler` objects directly.
 *
 * @property handle Unused; would be a [com.awakekt.awake.webgpu.WebGpuHandles] table id.
 */
@JvmInline
value class SamplerHandle(val handle: Long)

/**
 * Vulkan-parity placeholder for a descriptor-set layout, kept so the shared pipeline
 * constructors keep one signature. Callers pass 0: WebGPU derives or builds the bind-group
 * layout itself and the value is never read.
 *
 * @property handle Ignored; callers pass 0.
 */
@JvmInline
value class DescriptorSetLayoutHandle(val handle: Long)

/**
 * Vulkan-parity placeholder for a descriptor pool. WebGPU has no pools, and this backend never
 * creates or reads one.
 *
 * @property handle Unused.
 */
@JvmInline
value class DescriptorPoolHandle(val handle: Long)

/**
 * Vulkan-parity placeholder for a descriptor set. This backend binds `GPUBindGroup` objects
 * instead and never creates or reads one.
 *
 * @property handle Unused.
 */
@JvmInline
value class DescriptorSetHandle(val handle: Long)

/**
 * Vulkan-parity placeholder for a command pool. WebGPU has no pools, and this backend never
 * creates or reads one.
 *
 * @property handle Unused.
 */
@JvmInline
value class CommandPoolHandle(val handle: Long)
