// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.material

/**
 * Module restructuring slice 1 (see docs/mvp-plan.md): narrow interface, see
 * [io.github.ronjunevaldoz.awake.render.mesh.Mesh]'s doc comment for the rationale.
 * `createResources(texture)` is deliberately excluded -- it's only ever called from
 * backend-specific app-bootstrap code (`VulkanApplication.setupVulkan()`) on the concrete
 * type directly, never through this interface.
 */
interface Material {
    /** Writes this material's whole uniform block -- every field its shader's `Uniforms`
     * struct declares, concatenated in layout order, not just the MVP the parameter was
     * once named for. See `MaterialUniformLayouts` for the layouts callers pack against. */
    fun updateUniformBuffer(uniformFloats: FloatArray)
    fun bind(commandBuffer: Long, pipelineLayout: Long)
    fun destroy()
}
