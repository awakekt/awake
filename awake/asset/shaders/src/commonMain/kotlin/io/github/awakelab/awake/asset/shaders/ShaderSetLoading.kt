/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaders

import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.render.pipeline.PipelineSpec
import io.github.awakelab.awake.render.pipeline.PipelineVariant
import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.ShaderSource
import io.github.awakelab.awake.render.pipeline.entryPoint

/** [stage]'s source, whatever variant it is -- shared by both backends' bootstrap classes so
 * neither hand-duplicates this lookup + error message. */
fun ShaderStages.source(stage: ShaderStage): ShaderSource =
    this[stage] ?: error("No $stage stage registered in this ShaderStages.")

/** [stage]'s resource path, for a caller that genuinely needs a file name rather than the
 * source itself. Fails on an inline shader, which has no path to give. */
fun ShaderStages.resourcePath(stage: ShaderStage): String =
    (this[stage] as? ShaderSource.ResourcePath)?.path
        ?: error("Stage $stage carries its source inline; it has no resource path.")

/** [stage]'s entry point -- shared by both backends' bootstrap classes. */
fun ShaderStages.entryPoint(stage: ShaderStage): String =
    this[stage]?.entryPoint
        ?: error("No $stage stage registered in this ShaderStages.")

/**
 * This shader program as a [PipelineSpec] for [vertexFormat], collapsing the four
 * [resourcePath]/[entryPoint] lookups both backends' bootstrap classes used to spell out by hand
 * per pipeline.
 *
 * Here rather than in either backend because the lookups are identical on both sides -- the only
 * thing that differed was which `ShaderStages` (`ShaderSet.vulkan` or `ShaderSet.webGpu`) the
 * caller reached for, and that choice stays at the call site.
 */
fun ShaderStages.spec(
    vertexFormat: VertexFormat,
    variant: PipelineVariant = PipelineVariant.Opaque,
    bindingLayout: BindingLayout = BindingLayout.Standard,
): PipelineSpec = PipelineSpec(
    vertexFormat = vertexFormat,
    vertexShader = source(ShaderStage.VERTEX),
    fragmentShader = source(ShaderStage.FRAGMENT),
    variant = variant,
    bindingLayout = bindingLayout,
)

/** [name]'s pipeline build wrapped so a resource-not-found/shader-module-creation failure says
 * which pipeline actually failed instead of just a bare file path or native error code -- shared
 * by both backends' bootstrap classes (`VulkanEngine`/`WebGpuEngine`), each of which builds
 * several pipelines outside `buildPipelineTable`'s own shared per-request wrapping (shadow/
 * skybox/instanced/skinnedInstanced/particle/additional-format). */
suspend fun <T> withPipelineLoadContext(name: String, block: suspend () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        throw IllegalStateException("Failed to build pipeline '$name': ${e.message}", e)
    }
