/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.webgpu.material.Material
import com.awakekt.awake.webgpu.mesh.Mesh
import com.awakekt.awake.webgpu.mesh.WebGpuDynamicMesh

internal fun Renderer.trackMesh(mesh: Mesh): Mesh {
    liveMeshCount += 1
    liveMeshBytes += mesh.sizeBytes
    mesh.onDestroy = {
        liveMeshCount -= 1
        liveMeshBytes -= mesh.sizeBytes
    }
    return mesh
}

internal fun Renderer.trackDynamicMesh(mesh: WebGpuDynamicMesh): WebGpuDynamicMesh {
    liveMeshCount += 1
    liveMeshBytes += mesh.sizeBytes
    mesh.onDestroy = {
        liveMeshCount -= 1
        liveMeshBytes -= mesh.sizeBytes
    }
    return mesh
}

internal fun Renderer.trackMaterial(material: Material, releaseTextures: (() -> Unit)? = null): Material {
    liveMaterialCount += 1
    material.onDestroy = {
        liveMaterialCount -= 1
        releaseTextures?.invoke()
    }
    return material
}
