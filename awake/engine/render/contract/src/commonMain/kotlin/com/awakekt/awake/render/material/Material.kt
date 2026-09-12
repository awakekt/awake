/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.material

/**
 * Module restructuring slice 1 (see docs/mvp-plan.md): narrow interface, see
 * [com.awakekt.awake.render.mesh.Mesh]'s doc comment for the rationale.
 * `createResources(texture)` is deliberately excluded -- it's only ever called from
 * backend-specific app-bootstrap code (`VulkanApplication.setupVulkan()`) on the concrete
 * type directly, never through this interface.
 */
interface Material : GpuMaterial {
    /** Writes this material's whole uniform block. */
    override fun updateUniformBuffer(uniformFloats: FloatArray)

    override fun destroy()
}
