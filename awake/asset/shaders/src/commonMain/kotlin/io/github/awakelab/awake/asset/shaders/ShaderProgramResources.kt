/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaders

import io.github.awakelab.awake.asset.shaderdsl.AslShaderDefinition
import io.github.awakelab.awake.core.math.ClipSpace
import io.github.awakelab.awake.core.host.readResourceBytes
import io.github.awakelab.awake.render.pipeline.ShaderSource

enum class ShaderStage {
    VERTEX,
    FRAGMENT,
    COMPUTE,
}

/** Resolves [this] to raw bytes -- [ShaderSource.ResourcePath] reads from disk/assets via
 * [readResourceBytes] (hence `suspend`), the other two variants already have bytes in hand. */
suspend fun ShaderSource.resolveBytes(): ByteArray = when (this) {
    is ShaderSource.ResourcePath -> readResourceBytes(path)
    is ShaderSource.PrecompiledBinary -> bytes
    is ShaderSource.InlineText -> sourceCode.encodeToByteArray()
}

/** One backend's shader stages, keyed by [ShaderStage] -- [graphics] is the shape every real
 * pipeline in this engine uses today (vertex + fragment); [compute] exists for a future compute
 * pipeline (none exist yet, see this module's own doc comment) but costs nothing to keep. */
class ShaderStages private constructor(
    val stages: Map<ShaderStage, ShaderSource>,
) {
    operator fun get(stage: ShaderStage): ShaderSource? = stages[stage]

    companion object {
        fun graphics(vertex: ShaderSource, fragment: ShaderSource) = ShaderStages(
            mapOf(ShaderStage.VERTEX to vertex, ShaderStage.FRAGMENT to fragment),
        )

        fun compute(compute: ShaderSource) = ShaderStages(
            mapOf(ShaderStage.COMPUTE to compute),
        )
    }
}

/** A shader program described once per backend -- [vulkan]/[webGpu] each resolve independently
 * (Vulkan wants separate `.vert.spv`/`.frag.spv` files, WebGPU wants one `.wgsl` file read for
 * both stages), so a caller building an `AwakeApplication` for either backend just reads its own
 * half; the other is never touched. */
data class ShaderSet(
    val vulkan: ShaderStages,
    val webGpu: ShaderStages,
)

/** Escape hatch for a shader whose two backends genuinely need different stage sources (not just
 * different file paths under the same naming convention -- see [shaderSet] below for that common
 * case). */
fun shaderSet(
    vulkan: ShaderStages,
    webGpu: ShaderStages,
): ShaderSet = ShaderSet(
    vulkan = vulkan,
    webGpu = webGpu,
)

/** One [directory]/[name]-derived [ShaderStages] -- [vertexFile]/[fragmentFile] are the same
 * string for a backend whose stages share one file (WebGPU's `.wgsl`), different strings for a
 * backend with separate per-stage files (Vulkan's `.vert.spv`/`.frag.spv`). The only thing that
 * varies per backend in [shaderSet] below is these file names -- everything else (the
 * `ResourcePath`/entry-point shape) is written here exactly once. */
private fun conventionStages(directory: String, vertexFile: String, fragmentFile: String): ShaderStages =
    ShaderStages.graphics(
        vertex = ShaderSource.ResourcePath("$directory/$vertexFile", entryPoint = "vertexMain"),
        fragment = ShaderSource.ResourcePath("$directory/$fragmentFile", entryPoint = "fragmentMain"),
    )

/** The common case: a shader named [name] follows this engine's fixed file-naming convention on
 * both backends (`assets/shader/vulkan/$name.wgsl`, `assets/shader/webgpu/$name.wgsl`), so a
 * caller just names it once. No directory override params -- confirmed via a full-repo audit
 * that zero call sites have ever needed one; a shader whose stages genuinely don't fit this
 * convention uses the [shaderSet] escape hatch above instead of bending this one.
 *
 * Both halves name WGSL. Vulkan ships source rather than SPIR-V because `VulkanShaderResolver`
 * compiles it through the in-process naga binding and caches per path, which costs one compile
 * per shader at load and keeps a single naga in the repo instead of two to hold in lockstep. */
fun shaderSet(name: String): ShaderSet = ShaderSet(
    vulkan = conventionStages("assets/shader/vulkan", "$name.wgsl", "$name.wgsl"),
    webGpu = conventionStages("assets/shader/webgpu", "$name.wgsl", "$name.wgsl"),
)

/**
 * [definition]'s WGSL for both backends, carried in memory rather than loaded.
 *
 * The same shape [shaderSet] produces, minus the file: nothing has to sync a resource into each
 * app's per-platform tree, so this works identically on iOS and wasm, whose loaders cannot see a
 * library's own resources. `emitWgsl()` is called once per set, at construction.
 */
fun aslShaderSet(definition: AslShaderDefinition): ShaderSet {
    val stages = definition.graphicsStages()
    return ShaderSet(vulkan = stages, webGpu = stages)
}

/**
 * A shader set built once per backend, with that backend's [ClipSpace] in scope.
 *
 * The convention a shader is compiled against is not a property of the shader -- it is a property
 * of the backend rendering it, and it decides real code: whether an NDC coordinate becomes a
 * texture UV directly or flipped (see `ndcToUv`). Handing the definition its clip space removes
 * the step where somebody has to remember to thread a flag through, which is the step `lit_shadow`
 * skipped while `depth_fog` next door did not.
 *
 * The two emitted sources are usually identical, and that is fine: [build] simply never consulted
 * its argument.
 */
fun aslShaderSet(build: (ClipSpace) -> AslShaderDefinition): ShaderSet = ShaderSet(
    vulkan = build(ClipSpace.Vulkan).graphicsStages(),
    webGpu = build(ClipSpace.WebGpu).graphicsStages(),
)

/** One emitted WGSL source, addressed by both entry points. */
private fun AslShaderDefinition.graphicsStages(): ShaderStages {
    val wgsl = emitWgsl()
    return ShaderStages.graphics(
        vertex = ShaderSource.InlineText(wgsl, entryPoint = "vertexMain"),
        fragment = ShaderSource.InlineText(wgsl, entryPoint = "fragmentMain"),
    )
}

