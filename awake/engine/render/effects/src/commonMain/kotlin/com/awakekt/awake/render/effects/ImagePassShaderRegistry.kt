/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.bindingsByGroup
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.imagepass.ImagePassShaderSpec
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.renderer.UniformLayout

/**
 * Registry of shared [ImagePassShaderSpec] definitions used by `:awake:engine:render:effects`.
 *
 * Parallel to `PipelineRegistry` for 3D scene pipelines: effects register their ASL shaders here
 * once instead of adding fields to `UiShaderSet` or `Renderer` constructors.
 */
object ImagePassShaderRegistry {
    private val specsById = LinkedHashMap<String, ImagePassShaderSpec>()

    init {
        registerBuiltInEffectShaders()
    }

    /**
     * Registers [spec] under its [ImagePassShaderSpec.id].
     *
     * Re-registering an identical [ImagePassShaderSpec] is idempotent; registering a conflicting
     * spec under the same id throws [IllegalArgumentException].
     */
    fun register(spec: ImagePassShaderSpec): ImagePassShaderSpec {
        val existing = specsById[spec.id]
        require(existing == null || existing == spec) {
            "ImagePassShaderRegistry already holds a different shader spec for id '${spec.id}'."
        }
        specsById[spec.id] = spec
        return spec
    }

    /**
     * Builds and registers an [ImagePassShaderSpec] from an [AslShaderDefinition].
     */
    fun registerAsl(
        id: String,
        definition: AslShaderDefinition,
        vertexFormat: VertexFormat = ImageEffectVertexFormat,
        uniformLayout: UniformLayout? = null,
    ): ImagePassShaderSpec = register(
        ImagePassShaderSpec(
            id = id,
            wgslSource = definition.emitWgsl(),
            groupBindings = definition.bindingsByGroup()[0] ?: GroupBindings(emptyList()),
            vertexFormat = vertexFormat,
            uniformLayout = uniformLayout,
        ),
    )

    /** Returns the registered [ImagePassShaderSpec] for [id], or `null` if none is registered. */
    operator fun get(id: String): ImagePassShaderSpec? = specsById[id]

    /** Returns the registered [ImagePassShaderSpec] for [id], or throws if absent. */
    fun require(id: String): ImagePassShaderSpec =
        requireNotNull(specsById[id]) { "No ImagePassShaderSpec registered for id '$id'." }

    /** Snapshot of all registered [ImagePassShaderSpec]s in registration order. */
    fun all(): List<ImagePassShaderSpec> = specsById.values.toList()

    private fun registerBuiltInEffectShaders() {
        register(ImageEffectShaders.CopySpec)
        register(ImageEffectShaders.TintSpec)
        register(BlurEffectShaders.BlurPassSpec)
        register(BlurEffectShaders.BlurCompositeSpec)
    }
}
