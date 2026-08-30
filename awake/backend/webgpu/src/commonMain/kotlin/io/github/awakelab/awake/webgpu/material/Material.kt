/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.material

import io.github.awakelab.awake.render.pipeline.GroupBindings
import io.github.awakelab.awake.render.pipeline.ResourceKind
import io.github.awakelab.awake.webgpu.device.GraphicsDevice
import io.github.awakelab.awake.webgpu.fastArrayBufferOf
import io.github.awakelab.awake.webgpu.pipeline.WebGpuBindGroupHandle
import io.github.awakelab.awake.webgpu.texture.Texture
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTextureView
import io.github.awakelab.awake.render.material.Material as RenderMaterial

/**
 * Two unrelated cases share this type, matching the shared `Renderer.createMaterial` contract:
 * a UI-compositing material ([createResourcesFromRenderTarget], sampled by
 * [io.github.awakelab.awake.webgpu.ui.UiTextureRenderPipeline], which owns its own bind
 * groups) and a 3D `DrawCall.material` with a real base-color texture ([createResources]),
 * which owns the uniform buffer + bind group `textured.wgsl` declares.
 *
 * Unlike Vulkan's descriptor set (host-side writable, so one set can be rewritten per draw),
 * a `GPUBindGroup` is immutable once created -- so `bindGroup` is built once, lazily, against
 * the pipeline this material is first drawn through.
 */
class Material(
    graphicsDevice: GraphicsDevice,
    private val uniformFloatCount: Int = 16,
    /** What this material's bind group declares. Defaults to the glTF metallic-roughness shape
     * every material had before the declaration existed. Unlike Vulkan, the *layout* still comes
     * from the shader via `getBindGroupLayout`; this decides the entries written against it. */
    private val bindings: GroupBindings = GroupBindings.StandardMaterial,
) : RenderMaterial {
    private val device = graphicsDevice.wgpuContext.device

    /** Sampled textures past the base-color one, in binding order -- [pbrTextures] fills them
     * positionally, the same rule Vulkan's `PBR_TEXTURE_BINDINGS` applies to its own writes. */
    private val pbrBindings = materialPbrBindings(bindings)

    var previewTextureView: GPUTextureView? = null
        private set
    var previewSampler: GPUSampler? = null
        private set

    private var texture: Texture? = null
    private var pbrTextures: List<Texture> = emptyList()
    private var uniformBuffer: GPUBuffer? = null
    private var bindGroup: GPUBindGroup? = null

    /** Scoped to the UI-compositing case -- see this class's own doc comment. */
    fun createResourcesFromRenderTarget(textureView: GPUTextureView, sampler: GPUSampler) {
        previewTextureView = textureView
        previewSampler = sampler
    }

    /** The 3D `DrawCall.material` case: keeps [texture] plus [pbr] (metallicRoughness,
     * normal, occlusion, emissive -- in `textured.wgsl`'s binding 5-8 order, each a neutral
     * 1x1 stand-in when the material has no such map) and allocates this material's own
     * uniform buffer sized by [uniformFloatCount]. The bind group itself waits for
     * [bindGroupFor]: its layout comes from the pipeline, which this class never sees. */
    fun createResources(texture: Texture, pbr: List<Texture>) {
        this.texture = texture
        pbrTextures = pbr
        uniformBuffer = device.createBuffer(
            BufferDescriptor(
                size = (uniformFloatCount * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
            ),
        )
    }

    /** True once [createResources] has run -- a `PositionNormalColorUv` draw call whose
     * material has no texture can't be drawn through `textured.wgsl` at all. */
    val hasTexture: Boolean get() = texture != null

    /** Builds (once) this material's bind group from [bindings] -- against [pipeline]'s group-0
     * layout, then reuses it every frame. For the default declaration that is exactly what this
     * used to hardcode: uniform, base-color view, sampler, then the four PBR views at 5-8, all
     * sampled through the base-color sampler as `textured.wgsl` expects.
     *
     * WebGPU validates entries against the shader-derived layout, so a declaration that does not
     * match the shader fails here rather than rendering something wrong.
     *
     * ponytail: cached for the first pipeline only; make it a per-pipeline map if one material
     * ever gets drawn through two pipelines (e.g. a wireframe variant of the textured one). */
    fun bindGroupFor(pipeline: GPURenderPipeline): GPUBindGroup = bindGroup ?: device.createBindGroup(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(0u),
            entries = bindGroupEntries(),
        ),
    ).also { bindGroup = it }

    private fun bindGroupEntries(): List<BindGroupEntry> {
        require(pbrTextures.size >= pbrBindings.size) {
            "Declaration needs ${pbrBindings.size} sampled textures past the base-color one " +
                "(bindings $pbrBindings) but createResources supplied ${pbrTextures.size}."
        }
        return bindings.entries.map { entry ->
            val resource = when (entry.kind) {
                ResourceKind.UniformBuffer -> BufferBinding(buffer = requireUniformBuffer())
                ResourceKind.Sampler -> requireTexture().sampler
                ResourceKind.SampledTexture ->
                    if (entry.binding == BASE_COLOR_IMAGE_BINDING) {
                        requireTexture().view
                    } else {
                        pbrTextures[pbrBindings.indexOf(entry.binding)].view
                    }
                // No material-side source for one yet -- a joint palette is a storage buffer, but
                // it is bound at its own group, never here. See D28's plan doc.
                ResourceKind.StorageBuffer -> error(
                    "Binding ${entry.binding} declares a storage buffer, which a Material " +
                        "cannot supply.",
                )
            }
            BindGroupEntry(binding = entry.binding.toUInt(), resource = resource)
        }
    }

    /** [bindGroupFor] as the shared render layer's opaque handle, cached the same way. */
    fun bindingFor(pipeline: GPURenderPipeline): WebGpuBindGroupHandle =
        bindGroupHandle ?: WebGpuBindGroupHandle(bindGroupFor(pipeline)).also { bindGroupHandle = it }

    private var bindGroupHandle: WebGpuBindGroupHandle? = null

    /** Writes this material's whole `Uniforms` block, not just its MVP -- callers pass the
     * concatenated field list their shader declares. Like every other `queue.writeBuffer` on this
     * backend this is queue-scheduled, not interleaved into the encoder -- so two draw calls
     * sharing ONE material within a frame would both see the last write (see `Renderer`'s own
     * class doc comment for the same ceiling on the shared uniform buffer). */
    override fun updateUniformBuffer(uniformFloats: FloatArray) {
        // Same check Vulkan's Material.updateUniformBuffer has -- catches an oversized write
        // here, in Kotlin, instead of writeBuffer silently overrunning the GPU buffer (WebGPU
        // has no equivalent of Vulkan's vkMapMemory validation error to catch it for us).
        require(uniformFloats.size <= uniformFloatCount) {
            "Uniform write of ${uniformFloats.size} floats overflows this Material's " +
                "$uniformFloatCount-float buffer -- createMaterial(uniformFloatCount = ...) " +
                "was sized for a smaller layout than what's actually being written."
        }
        device.queue.writeBuffer(requireUniformBuffer(), 0uL, fastArrayBufferOf(uniformFloats))
    }

    /** Must be safe to call unconditionally (every `AppLifecycle.dispose` calls it regardless of which
     * of the two cases above the material was built for). WebGPU's views/samplers/bind groups
     * are garbage-collected by the JS runtime; only the buffer has an explicit release. The
     * [texture] itself is owned by `Renderer` (which created it), same as Vulkan. */
    override fun destroy() {
        previewTextureView = null
        previewSampler = null
        uniformBuffer?.close()
        uniformBuffer = null
        bindGroup = null
        bindGroupHandle = null
        texture = null
        pbrTextures = emptyList()
    }

    private companion object {
        /** `textured.wgsl`'s base-color image. Its sampler at 2 serves every other sampled
         * texture in the group, which is why the PBR maps declare no sampler of their own. */
        const val BASE_COLOR_IMAGE_BINDING = 1
    }

    private fun requireTexture(): Texture = requireNotNull(texture) {
        "Material has no texture -- was it built via createMaterial(texture = ...)?"
    }

    private fun requireUniformBuffer(): GPUBuffer = requireNotNull(uniformBuffer) {
        "Material has no uniform buffer -- was it built via createMaterial(texture = ...)?"
    }
}

/** Sampled textures past the base-color one at binding 1, in binding order. Extracted so the
 * positional mapping onto `createResources`' PBR list can be asserted without a GPU device --
 * it is the one part of the entry list that is not a one-to-one lookup. */
internal fun materialPbrBindings(bindings: GroupBindings): List<Int> =
    bindings.entries
        .filter { it.kind == ResourceKind.SampledTexture && it.binding > 1 }
        .map { it.binding }
        .sorted()
