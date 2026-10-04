/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.asset.gltf.GltfAlphaMode
import com.awakekt.awake.asset.gltf.GltfMesh
import com.awakekt.awake.asset.gltf.LoadedPrimitive
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial

/*
 * A glTF material's factors, as the resolver keeps them: read off a static primitive or a skinned
 * part, and turned into the PbrMaterial an entity draws with when it has none of its own.
 */

internal fun LoadedPrimitive.toMaterialParameters() =
    materialParameters(metallicFactor, roughnessFactor, baseColorFactor, emissiveFactor, alphaMode)

internal fun GltfMesh.toMaterialParameters() =
    materialParameters(metallicFactor, roughnessFactor, baseColorFactor, emissiveFactor, alphaMode)

private fun materialParameters(
    metallic: Float,
    roughness: Float,
    baseColor: FloatArray,
    emissive: FloatArray,
    alphaMode: GltfAlphaMode,
) = GltfMaterialParameters(
    metallic = metallic,
    roughness = roughness,
    baseColorFactor = baseColor.toColor(),
    emissiveFactor = emissive.toColor(alpha = 0f),
    alphaMode = alphaMode.toAlphaMode(),
)

internal fun GltfMaterialParameters.toPbrMaterial() = PbrMaterial(
    metallic = metallic,
    roughness = roughness,
    baseColorFactor = baseColorFactor,
    emissiveFactor = emissiveFactor,
    alphaMode = alphaMode,
)

private fun FloatArray.toColor(alpha: Float = getOrElse(3) { 1f }): Color = Color(
    getOrElse(0) { 1f },
    getOrElse(1) { 1f },
    getOrElse(2) { 1f },
    alpha,
)

private fun GltfAlphaMode.toAlphaMode(): AlphaMode = when (this) {
    GltfAlphaMode.MASK -> AlphaMode.Masked
    GltfAlphaMode.OPAQUE,
    GltfAlphaMode.BLEND,
    -> AlphaMode.Opaque
}
