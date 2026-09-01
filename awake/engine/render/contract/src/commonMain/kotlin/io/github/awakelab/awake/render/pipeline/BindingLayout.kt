/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.pipeline

/**
 * Semantic resources that can occupy a pipeline descriptor set or bind group.
 *
 * Open rather than an enum: the engine's own three cover everything it draws, but a consumer
 * registering a `ContentFeature` may need a group the engine has no name for -- a terrain splat
 * set, say -- and an enum makes that unstateable without editing the render contract. Nothing
 * matches on this exhaustively; every use is either one of the named cases or a
 * [BindingLayout.slot] lookup, so [Custom] costs no branch anywhere.
 */
sealed interface BindingSemantic {
    data object Material : BindingSemantic
    data object ShadowDepth : BindingSemantic
    data object JointPalette : BindingSemantic

    /**
     * The current frame's camera-space depth, written by a pre-pass before the scene pass.
     *
     * What water, soft particles and depth fog read to compare their own fragment against the
     * opaque scene behind it. Distinct from [ShadowDepth], which is the same kind of resource
     * rendered from the light instead of the camera -- a pipeline can declare both.
     */
    data object SceneDepth : BindingSemantic

    /**
     * A group the engine does not name, identified by [name].
     *
     * Value-equal like the objects above, so it works as a [BindingLayout] key. Two features
     * choosing the same [name] collide, which is correct -- they would also collide in the
     * pipeline registry, which keys content features the same way.
     */
    data class Custom(val name: String) : BindingSemantic {
        init {
            require(name.isNotBlank()) { "A custom binding semantic needs a name." }
        }
    }
}

/**
 * The group a depth pass reads its current cascade from.
 *
 * A [BindingSemantic.Custom] rather than a named case: it is meaningful only inside the depth
 * pass, and the engine's own vocabulary describes what the SCENE pass binds.
 */
val ShadowCascadePassBinding = BindingSemantic.Custom("shadowCascadePass")

/**
 * The backend-neutral binding ABI for one pipeline.
 *
 * The numeric slot is resolved once when a pipeline is authored. Recording code names the
 * resource instead of repeating a Vulkan descriptor-set/WebGPU bind-group index. Shadow depth
 * and joint palettes intentionally share the secondary slot because no pipeline declares both.
 */
data class BindingLayout private constructor(
    private val slots: Map<BindingSemantic, Int>,
) {
    init {
        require(slots.values.all { it >= 0 }) { "Binding slots must be non-negative." }
    }

    fun slot(semantic: BindingSemantic): Int = requireNotNull(slots[semantic]) {
        "Binding semantic $semantic is not declared by this pipeline."
    }

    fun contains(semantic: BindingSemantic): Boolean = semantic in slots

    companion object {
        /** Creates a pipeline-specific semantic layout from its authored ABI. */
        fun of(vararg entries: Pair<BindingSemantic, Int>): BindingLayout {
            require(entries.size == entries.map { it.first }.toSet().size) {
                "Binding semantics must be declared once per layout."
            }
            return BindingLayout(entries.toMap())
        }

        /**
         * The current scene ABI: material at the first group, shared resources at the second.
         *
         * [BindingSemantic.SceneDepth] gets a third group rather than joining the second,
         * because unlike shadow depth and the joint palette it CAN co-occur with them -- water
         * that both samples the depth behind it and receives a shadow. Sharing would bind
         * whichever came last and render the wrong thing without erroring. A slot costs nothing
         * to a pipeline that declares no layout for it; see `RenderPipeline`'s
         * `extraDescriptorSetLayouts`.
         */
        val Standard = of(
            BindingSemantic.Material to 0,
            BindingSemantic.ShadowDepth to 1,
            BindingSemantic.JointPalette to 1,
            // The depth pass's own cascade matrix. Shares the second group with the two above
            // for the same reason they share it with each other: the passes are disjoint. A
            // depth-only pass has no shadow map to sample and no skinned palette to read -- it
            // IS the pass that writes the shadow map.
            ShadowCascadePassBinding to 1,
            BindingSemantic.SceneDepth to 2,
        )
    }
}
