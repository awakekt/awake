/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdsl

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.ResourceBinding
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.SamplerType
import com.awakekt.awake.render.pipeline.ShaderStage
import com.awakekt.awake.render.pipeline.TextureSampleType
import com.awakekt.awake.render.renderer.uniformFloats

/**
 * What this shader declares in bind [group], as the render contract's own description -- so a
 * pipeline layout can be derived from the shader instead of restated beside it.
 *
 * Stages are read out of the statement trees rather than declared: a binding belongs to a stage
 * if that stage references its name, directly or through a helper function it calls. That is
 * the fact Vulkan needs and the one an author would otherwise have to keep in sync by hand
 * (`VUID-VkGraphicsPipelineCreateInfo-layout-07988` is what a wrong answer costs).
 *
 * @return null when [group] holds no bindings, matching `PipelineSpec.materialBindings`' own
 * "absent, not empty" convention.
 */
fun AslShaderDefinition.bindingsForGroup(group: Int): GroupBindings? {
    val vertexNames = namesReferencedBy(vertexStage.statements + listOfNotNull(vertexStage.positionOnly?.asStatement()))
    val fragmentNames = namesReferencedBy(
        fragmentStage.statements + listOfNotNull(fragmentStage.returnValue?.asStatement()),
    )

    fun stagesFor(matches: (String) -> Boolean): Set<ShaderStage> = buildSet {
        if (vertexNames.any(matches)) add(ShaderStage.Vertex)
        if (fragmentNames.any(matches)) add(ShaderStage.Fragment)
    }

    val entries = buildList {
        uniformBlocks.filter { it.group == group }.forEach { block ->
            val prefix = "${block.instanceName}."
            val stages = stagesFor { it == block.instanceName || it.startsWith(prefix) }
            if (stages.isNotEmpty()) {
                add(
                    ResourceBinding(
                        binding = block.binding,
                        kind = ResourceKind.UniformBuffer,
                        stages = stages,
                        minBindingSize = block.totalByteSize(),
                    ),
                )
            }
        }
        textures.filter { it.group == group }.forEach { texture ->
            val stages = stagesFor { it == texture.name }
            if (stages.isNotEmpty()) {
                add(
                    ResourceBinding(
                        texture.binding,
                        texture.type.toResourceKind(),
                        stages,
                        arrayed = texture.type == AslType.Texture2dArrayF32 ||
                            texture.type == AslType.TextureDepth2dArray,
                        textureSampleType = texture.type.toTextureSampleType(),
                        samplerType = texture.type.toSamplerType(),
                    ),
                )
            }
        }
        storageBindings.filter { it.group == group }.forEach { storage ->
            val stages = stagesFor { it == storage.varName }
            if (stages.isNotEmpty()) {
                add(ResourceBinding(storage.binding, ResourceKind.StorageBuffer, stages))
            }
        }
    }
    return if (entries.isEmpty()) null else GroupBindings(entries.sortedBy { it.binding })
}

/** All statically used resource groups in this definition, keyed by their WebGPU/Vulkan index. */
fun AslShaderDefinition.bindingsByGroup(): Map<Int, GroupBindings> {
    val groups = buildSet {
        uniformBlocks.forEach { add(it.group) }
        textures.forEach { add(it.group) }
        storageBindings.forEach { add(it.group) }
    }
    return groups.mapNotNull { group ->
        bindingsForGroup(group)?.let { group to it }
    }.toMap()
}

private fun AslType.toResourceKind(): ResourceKind = when (this) {
    AslType.Sampler, AslType.SamplerNonFiltering, AslType.SamplerComparison -> ResourceKind.Sampler
    else -> ResourceKind.SampledTexture
}

private fun AslType.toTextureSampleType(): TextureSampleType = when (this) {
    AslType.TextureDepth2d,
    AslType.TextureDepth2dArray,
    AslType.TextureDepthCube,
    -> TextureSampleType.Depth
    else -> TextureSampleType.Float
}

private fun AslType.toSamplerType(): SamplerType = when (this) {
    AslType.SamplerComparison -> SamplerType.Comparison
    AslType.SamplerNonFiltering -> SamplerType.NonFiltering
    else -> SamplerType.Filtering
}

/** Wraps a bare expression so the statement walker can reach it -- a stage's `positionOnly` and
 * `returnValue` are expressions with no enclosing statement of their own. */
private fun AslExpr.asStatement(): AslStatement = AslReturn(this)

/**
 * Every name [statements] reference, following [AslCall]s into this shader's helper functions.
 *
 * A binding used only inside a helper still belongs to whichever stage calls that helper, so
 * stopping at the stage body would under-report and produce a layout missing a stage bit.
 */
private fun AslShaderDefinition.namesReferencedBy(statements: List<AslStatement>): Set<String> {
    val direct = mutableSetOf<String>()
    statements.forEach { collectNames(it, direct) }

    val byFunction = functions.associate { function ->
        function.name to mutableSetOf<String>().also { names ->
            function.body.forEach { collectNames(it, names) }
        }
    }

    val resolved = mutableSetOf<String>()
    val pending = ArrayDeque(direct)
    while (pending.isNotEmpty()) {
        val name = pending.removeFirst()
        if (!resolved.add(name)) continue
        byFunction[name]?.forEach(pending::addLast)
    }
    return resolved
}

private fun collectNames(statement: AslStatement, into: MutableSet<String>) {
    when (statement) {
        is AslAssign -> collectNames(statement.value, into)
        is AslLet -> collectNames(statement.value, into)
        is AslVar -> collectNames(statement.value, into)
        is AslSet -> collectNames(statement.value, into)
        is AslIf -> {
            collectNames(statement.condition, into)
            statement.body.forEach { collectNames(it, into) }
        }
        is AslReturn -> statement.value?.let { collectNames(it, into) }
        is AslForI32 -> {
            collectNames(statement.start, into)
            collectNames(statement.endInclusive, into)
            statement.body.forEach { collectNames(it, into) }
        }
        is AslForU32 -> {
            collectNames(statement.start, into)
            collectNames(statement.endExclusive, into)
            statement.body.forEach { collectNames(it, into) }
        }
        AslContinue, AslDiscard -> Unit
    }
}

private fun collectNames(expr: AslExpr, into: MutableSet<String>) {
    when (expr) {
        is AslRef -> into += expr.wgslName
        is AslIndex -> {
            into += expr.arrayName
            collectNames(expr.index, into)
        }
        is AslChainIndex -> {
            into += expr.varName
            collectNames(expr.outer, into)
            collectNames(expr.inner, into)
        }
        is AslCall -> {
            into += expr.function
            expr.args.forEach { collectNames(it, into) }
        }
        is AslSwizzle -> collectNames(expr.base, into)
        is AslBinary -> {
            collectNames(expr.left, into)
            collectNames(expr.right, into)
        }
        is AslUnary -> collectNames(expr.operand, into)
        is AslArrayLiteral -> expr.elements.forEach { collectNames(it, into) }
        is AslConstruct -> expr.args.forEach { collectNames(it, into) }
        else -> Unit
    }
}

@Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
internal fun AslUniformBlock.totalByteSize(): Long {
    var offset = 0
    for (field in fields) {
        val (align, size) = when (val t = field.type) {
            is AslType.Data -> {
                val elementAlign = when (t.shape) {
                    GpuDataShape.Float, GpuDataShape.UInt4 -> 4
                    GpuDataShape.Vec2 -> 8
                    GpuDataShape.Vec3, GpuDataShape.Vec4, GpuDataShape.Mat4 -> 16
                }
                if (field.count > 1) {
                    val arrayElementAlign = maxOf(elementAlign, 16)
                    val arrayElementStride = maxOf(t.shape.uniformFloats * Float.SIZE_BYTES, 16)
                    arrayElementAlign to (arrayElementStride * field.count)
                } else {
                    val fieldSize = when (t.shape) {
                        GpuDataShape.Float, GpuDataShape.UInt4 -> 4
                        GpuDataShape.Vec2 -> 8
                        GpuDataShape.Vec3 -> 12
                        GpuDataShape.Vec4 -> 16
                        GpuDataShape.Mat4 -> 64
                    }
                    elementAlign to fieldSize
                }
            }
            AslType.I32, AslType.U32, AslType.Bool -> {
                if (field.count > 1) {
                    16 to (16 * field.count)
                } else {
                    4 to 4
                }
            }
            is AslType.ArrayData -> {
                16 to (16 * t.count * field.count)
            }
            else -> 16 to (16 * field.count)
        }
        val remainder = offset % align
        if (remainder != 0) {
            offset += (align - remainder)
        }
        offset += size
    }
    val structAlign = 16
    val remainder = offset % structAlign
    if (remainder != 0) {
        offset += (structAlign - remainder)
    }
    return offset.toLong()
}
