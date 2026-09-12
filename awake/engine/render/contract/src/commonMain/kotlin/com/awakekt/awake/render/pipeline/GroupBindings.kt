/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

/** What kind of GPU resource occupies one binding slot. */
enum class ResourceKind {
    UniformBuffer,
    StorageBuffer,
    Sampler,
    SampledTexture,
}

/** Texture sample interpretation required by a sampled-image binding. */
enum class TextureSampleType {
    Float,
    Depth,
    Sint,
    Uint,
}

/** Sampler operation required by a sampler binding. */
enum class SamplerType {
    Filtering,
    NonFiltering,
    Comparison,
}

/**
 * Which shader stages read a binding.
 *
 * Declared rather than inferred: Vulkan rejects a pipeline whose layout omits a stage its
 * shader actually reads (`VUID-VkGraphicsPipelineCreateInfo-layout-07988`), and "textures are
 * fragment-only" stops being true the moment a terrain shader samples a heightmap in the
 * vertex stage to displace it. WebGPU derives stages from the shader itself and ignores this.
 */
enum class ShaderStage {
    Vertex,
    Fragment,
}

/**
 * One binding slot inside a bind group.
 *
 * @property binding The slot index within its group, matching the shader's own `@binding`.
 * @property kind What occupies the slot.
 * @property stages Which stages read it -- see [ShaderStage] for why this is not inferred.
 * @property arrayed Whether a [ResourceKind.SampledTexture] is `texture_2d_array` rather than
 *   `texture_2d`. A flag rather than its own [ResourceKind] because Vulkan's descriptor type is
 *   the same either way -- there, dimensionality lives in the image view -- so only WebGPU's
 *   `viewDimension` reads it, and every existing `kind == SampledTexture` test keeps matching
 *   arrays instead of silently skipping them.
 */
data class ResourceBinding(
    val binding: Int,
    val kind: ResourceKind,
    val stages: Set<ShaderStage>,
    val arrayed: Boolean = false,
    val textureSampleType: TextureSampleType = TextureSampleType.Float,
    val samplerType: SamplerType = SamplerType.Filtering,
    val minBindingSize: Long = 0L,
) {
    init {
        require(binding >= 0) { "Binding index must be non-negative; was $binding." }
        require(minBindingSize >= 0L) { "minBindingSize must be non-negative; was $minBindingSize." }
        require(stages.isNotEmpty()) {
            "Binding $binding declares no shader stage. A binding no stage reads should be " +
                "omitted rather than declared with an empty stage set."
        }
        require(!arrayed || kind == ResourceKind.SampledTexture) {
            "Only a sampled texture can be arrayed; binding $binding is $kind."
        }
        require(kind == ResourceKind.SampledTexture || textureSampleType == TextureSampleType.Float) {
            "Only a sampled texture can declare a sample type; binding $binding is $kind."
        }
        require(kind == ResourceKind.Sampler || samplerType == SamplerType.Filtering) {
            "Only a sampler can declare a sampler type; binding $binding is $kind."
        }
    }
}

/**
 * What occupies one bind group -- the half of a pipeline's binding ABI [BindingLayout] does not
 * describe.
 *
 * [BindingLayout] answers "which group index does the material live at"; this answers "and what
 * is inside it". Both are needed to build a Vulkan descriptor set layout, which is why that
 * layout is currently hand-written per backend instead of derived: nothing in the render
 * contract could state the second half.
 *
 * A pipeline that declares none of this keeps the fixed glTF metallic-roughness shape both
 * backends hardcode today -- see [StandardMaterial] and `PipelineSpec.materialBindings`.
 *
 * Ordering is not significant; [entries] is a list rather than a set only because a binding
 * index already makes each entry unique, and duplicates are rejected here rather than left for
 * a backend to fail on later with a less specific message.
 */
data class GroupBindings(val entries: List<ResourceBinding>) {
    init {
        require(entries.isNotEmpty()) { "A bind group with no bindings should be absent, not empty." }
        require(entries.size == entries.map { it.binding }.toSet().size) {
            "Binding indices must be unique within a group; got ${entries.map { it.binding }}."
        }
    }

    /** The entry at [binding], or null when this group declares no such slot. */
    fun at(binding: Int): ResourceBinding? = entries.firstOrNull { it.binding == binding }

    /** Declared minimum buffer byte size for [binding], or 0 when absent/not a uniform. */
    fun uniformBufferSize(binding: Int = 0): Long =
        entries.firstOrNull { it.binding == binding && it.kind == ResourceKind.UniformBuffer }?.minBindingSize ?: 0L

    companion object {
        /** A scene shader whose material group contains only its per-draw uniform block. */
        val UniformOnlyMaterial = GroupBindings(
            listOf(
                ResourceBinding(0, ResourceKind.UniformBuffer, setOf(ShaderStage.Vertex, ShaderStage.Fragment)),
            ),
        )

        /** A shader with a uniform block and one base-color texture/sampler pair. */
        val TexturedMaterial = GroupBindings(
            listOf(
                ResourceBinding(0, ResourceKind.UniformBuffer, setOf(ShaderStage.Vertex, ShaderStage.Fragment)),
                ResourceBinding(1, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(2, ResourceKind.Sampler, setOf(ShaderStage.Fragment)),
            ),
        )

        /**
         * The material group every pipeline uses today: an MVP/lighting uniform block, a
         * base-color image and its sampler, then the four remaining glTF metallic-roughness
         * maps.
         *
         * Image and sampler are two bindings rather than one combined image-sampler because
         * WGSL has no combined-sampler type -- naga always emits a `texture_2d` plus a
         * `sampler` as two separate slots. The four PBR maps need no sampler of their own;
         * they are all sampled through binding 2's.
         *
         * Bindings 3 and 4 are a deliberate gap -- they were the shadow map and its sampler
         * before those moved to their own group, and renumbering would mean editing generated
         * WGSL for no gain. A gap is legal and costs nothing.
         *
         * Every entry is unconditional: a shader that samples none of them simply never reads
         * them, and a material with no map for a channel binds a 1x1 neutral placeholder. One
         * shared shape beats a per-shader variant.
         *
         * The uniform block spans both stages because the vertex shader reads the MVP and the
         * fragment shader reads the light fields appended to that same block.
         */
        val StandardMaterial = GroupBindings(
            listOf(
                ResourceBinding(
                    binding = 0,
                    kind = ResourceKind.UniformBuffer,
                    stages = setOf(ShaderStage.Vertex, ShaderStage.Fragment),
                    minBindingSize = 368L,
                ),
                ResourceBinding(1, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(2, ResourceKind.Sampler, setOf(ShaderStage.Fragment)),
                ResourceBinding(5, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(6, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(7, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(8, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
            ),
        )

        /** Particle sprites use only their uniform block, texture, and sampler. */
        val ParticleMaterial = GroupBindings(
            listOf(
                ResourceBinding(0, ResourceKind.UniformBuffer, setOf(ShaderStage.Vertex, ShaderStage.Fragment)),
                ResourceBinding(1, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(2, ResourceKind.Sampler, setOf(ShaderStage.Fragment)),
            ),
        )

        /** The sampled UI target composite pass: two texture/sampler pairs plus its mode block. */
        val UiTargetComposite = GroupBindings(
            listOf(
                ResourceBinding(0, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(1, ResourceKind.Sampler, setOf(ShaderStage.Fragment)),
                ResourceBinding(2, ResourceKind.SampledTexture, setOf(ShaderStage.Fragment)),
                ResourceBinding(3, ResourceKind.Sampler, setOf(ShaderStage.Fragment)),
                ResourceBinding(4, ResourceKind.UniformBuffer, setOf(ShaderStage.Fragment)),
            ),
        )
    }
}
