/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.scene.rendering.animation.SkinnedPose

/** Surface parameters for a [MeshRenderer] entity drawn by the primary lit pipeline or by the
 * textured/glTF PBR pipeline (`VertexFormat.PositionNormalColorUv`). All `var` so a demo or
 * gameplay code can drive them from a slider.
 *
 * [metallic]/[roughness] mean two different things depending on which pipeline reads them: the
 * primary lit pipeline (no textures) uses them directly as the surface's only metallic/
 * roughness values; the textured pipeline multiplies them into its metallic-roughness texture
 * sample as glTF's own `metallicFactor`/`roughnessFactor` do. [baseColorFactor]/[emissiveFactor]
 * are read only by the textured pipeline, same glTF-factor role -- the primary pipeline has no
 * base-color/emissive texture to multiply them into.
 *
 * A [TextureAnimation] on the same entity moves the textured pipeline's textures.
 *
 * A textured mesh drawn additive (`MeshRenderer.additive`) adds only its base and emissive colour,
 * unlit; [litWhenAdditive] keeps it lit instead.
 *
 * On a skinned mesh (an entity with a [SkinnedPose]) [baseColorFactor] and [emissiveFactor] tint it
 * the same way; metallic, roughness and texture animation are not read there.
 *
 * @property metallic Surface metallic factor (0.0 for dielectric, 1.0 for metal).
 * @property roughness Surface roughness factor (0.0 for mirror-smooth, 1.0 for completely diffuse).
 * @property baseColorFactor Surface base color tint factor.
 * @property emissiveFactor Surface emissive color tint factor.
 * @property alphaMode Alpha blending or masking mode for surface transparency.
 * @property alphaCutoff Alpha cutoff threshold when [alphaMode] is [AlphaMode.Mask].
 * @property litWhenAdditive Keeps a textured mesh lit when it is drawn additive: it adds its lit
 * colour (sun, shadows, ambient, point lights) rather than only its own, and fades out in fog either
 * way. Off by default, for glows and fire, which should not be shaded. Read only by the textured
 * pipeline, and only for a transparent, additive draw.
 */
data class PbrMaterial(
    var metallic: Float = 0f,
    var roughness: Float = 0.5f,
    var baseColorFactor: Color = Color.White,
    var emissiveFactor: Color = Color.Transparent,
    var alphaMode: AlphaMode = AlphaMode.Opaque,
    var alphaCutoff: Float = 0.5f,
    var litWhenAdditive: Boolean = false,
) {
    private var packed: FloatArray? = null
    private var packedFrom: PbrMaterial? = null
    private var packedAnimation: TextureAnimation? = null

    /**
     * This material's uniform floats with [textureAnimation], packed again only after either changed. A scene of
     * thousands of materials would otherwise pack and allocate each one every frame. The array is
     * shared between frames, so a caller must not write to it.
     */
    fun packedFloats(textureAnimation: TextureAnimation = TextureAnimation.None): FloatArray {
        val cached = packed
        if (cached != null && packedFrom == this && packedAnimation == textureAnimation) return cached
        return pbrMaterialFloats(metallic, roughness, baseColorFactor, emissiveFactor, textureAnimation, litWhenAdditive).also {
            packed = it
            packedFrom = copy()
            packedAnimation = textureAnimation
        }
    }
}
