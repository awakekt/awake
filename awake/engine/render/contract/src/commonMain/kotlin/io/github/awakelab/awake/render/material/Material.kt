/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.material

/**
 * Module restructuring slice 1 (see docs/mvp-plan.md): narrow interface, see
 * [io.github.awakelab.awake.render.mesh.Mesh]'s doc comment for the rationale.
 * `createResources(texture)` is deliberately excluded -- it's only ever called from
 * backend-specific app-bootstrap code (`VulkanApplication.setupVulkan()`) on the concrete
 * type directly, never through this interface.
 */
interface Material {
    /** Writes this material's whole uniform block -- every field its shader's `Uniforms`
     * struct declares, concatenated in layout order, not just the MVP the parameter was
     * once named for. See `MaterialUniformLayouts` for the layouts callers pack against. */
    fun updateUniformBuffer(uniformFloats: FloatArray)
    // No bind here, for the same reason Mesh has no bind/draw: it took a raw VkCommandBuffer
    // and VkPipelineLayout as Longs, which WebGPU could only answer with TODO(). It had zero
    // callers.

    fun destroy()
}
