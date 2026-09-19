/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.asset

/**
 * Result of an asset conversion process into standard GLTF/GLB bytes.
 */
data class GltfConversionResult(
    val glbBytes: ByteArray,
    val name: String,
    val vertexCount: Int = 0,
    val triangleCount: Int = 0,
    val warnings: List<String> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GltfConversionResult) return false
        return glbBytes.contentEquals(other.glbBytes) &&
            name == other.name &&
            vertexCount == other.vertexCount &&
            triangleCount == other.triangleCount &&
            warnings == other.warnings
    }

    override fun hashCode(): Int {
        var result = glbBytes.contentHashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + vertexCount
        result = 31 * result + triangleCount
        result = 31 * result + warnings.hashCode()
        return result
    }
}

/**
 * Extension contract for custom or proprietary file format converters that produce standard glTF/GLB assets.
 *
 * 100% Kotlin Multiplatform (pure bytes in, pure GLB bytes out).
 */
interface AssetConverter {
    /** File extensions handled by this converter without leading dot (e.g. setOf("o3d", "lnd")). */
    val supportedExtensions: Set<String>

    /**
     * Converts [sourceBytes] into standard GLTF/GLB bytes.
     */
    fun convertToGltf(
        fileName: String,
        sourceBytes: ByteArray,
    ): GltfConversionResult
}
