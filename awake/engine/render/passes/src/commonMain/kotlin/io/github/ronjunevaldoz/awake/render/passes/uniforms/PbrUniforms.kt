// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.passes.uniforms

import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.render.renderer.DrawCall

/** No metal, half-rough -- a plain lit surface when a `DrawCall` supplies no PBR factors. */
const val DEFAULT_METALLIC = 0f
const val DEFAULT_ROUGHNESS = 0.5f

/** glTF's own defaults for `pbrMetallicRoughness`: both factors fully applied. */
const val DEFAULT_METALLIC_FACTOR = 1f
const val DEFAULT_ROUGHNESS_FACTOR = 1f

/** glTF's `baseColorFactor` default -- opaque white, i.e. the texture passes through untinted. */
val DEFAULT_BASE_COLOR_FACTOR: Color = Color.White

/** glTF's `emissiveFactor` default -- no self-illumination. Alpha is an unread pad slot. */
val DEFAULT_EMISSIVE_FACTOR: Color = Color(r = 0f, g = 0f, b = 0f, a = 0f)

/**
 * Packs `[metallic, roughness, pad, pad]` for the untextured lit path.
 *
 * @param drawCall Supplies the factors through `extraUniformFloats`, or nothing for the defaults.
 * @return Exactly [PBR_MATERIAL_FLOATS] floats.
 */
fun pbrMaterialFloats(drawCall: DrawCall): FloatArray {
    val supplied = drawCall.extraUniformFloats
    if (supplied.size >= PBR_MATERIAL_FLOATS) return supplied.copyOf(PBR_MATERIAL_FLOATS)
    return floatArrayOf(DEFAULT_METALLIC, DEFAULT_ROUGHNESS, 0f, 0f)
}

/**
 * Packs `[metallic, roughness, pad, pad] + baseColorFactor.rgba + emissiveFactor.rgba` for the
 * textured glTF PBR path.
 *
 * @param drawCall Supplies the factors through `extraUniformFloats`, or nothing for the defaults.
 * @return Exactly [PBR_TEXTURED_MATERIAL_FLOATS] floats.
 */
fun pbrTexturedMaterialFloats(drawCall: DrawCall): FloatArray {
    val supplied = drawCall.extraUniformFloats
    if (supplied.size >= PBR_TEXTURED_MATERIAL_FLOATS) return supplied.copyOf(PBR_TEXTURED_MATERIAL_FLOATS)
    return floatArrayOf(DEFAULT_METALLIC_FACTOR, DEFAULT_ROUGHNESS_FACTOR, 0f, 0f) +
        DEFAULT_BASE_COLOR_FACTOR.toFloatArray() +
        DEFAULT_EMISSIVE_FACTOR.toFloatArray()
}
