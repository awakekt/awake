/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.material

/**
 * An allocated GPU Uniform Buffer Object (UBO) and descriptor set / bind group handle.
 *
 * This represents the pure hardware resource handle used by GPU command recording.
 * Holds zero scene or shader-specific material authoring properties.
 */
interface GpuMaterial {
    /** Writes this material's uniform block buffer bytes. */
    fun updateUniformBuffer(uniformFloats: FloatArray)

    /** Destroys the underlying GPU resources. */
    fun destroy()
}
