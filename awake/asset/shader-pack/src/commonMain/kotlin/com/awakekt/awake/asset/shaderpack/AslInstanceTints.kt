/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderBuilder
import com.awakekt.awake.asset.shaderdsl.AslStorageArrays
import com.awakekt.awake.asset.shaderdsl.AslVertexBuilder
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.storageArrayOfArrays
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.SkinnedInstanceLayout

/** One vec4 per instance, alongside the unchanged palette binding used by all depth variants. */
internal fun AslShaderBuilder.skinnedInstanceTints(): AslStorageArrays = storageArrayOfArrays(
    structName = "InstanceTint",
    varName = "instanceTints",
    group = BindingLayout.Standard.slot(BindingSemantic.JointPalette),
    binding = SkinnedInstanceLayout.TINT_BINDING,
    fieldName = "rgba",
    elementShape = GpuDataShape.Vec4,
    elementCount = 1,
)

/** Both visible shader families apply the same RGB multiplier and preserve tint alpha. */
internal fun AslVertexBuilder.instanceTintedColor(color: AslExpr, tints: AslStorageArrays?, instance: AslExpr?): AslExpr {
    if (tints == null) return color
    val tint = let("instanceTint", tints.element(requireNotNull(instance), 0.lit))
    return vec4(color * tint.xyz, tint.w)
}
