/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.generate.MeshGenerateScope
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.scene.authoring.dsl.AwakeSceneDsl
import com.awakekt.awake.scene.runtime.SceneAssetLibrary
import com.awakekt.awake.scene.runtime.SceneAssetResolver
import com.awakekt.awake.scene.runtime.SceneMaterialFactory
import com.awakekt.awake.scene.runtime.SceneMeshFactory
import com.awakekt.awake.scene.runtime.SceneMeshRendererFactory
import com.awakekt.awake.scene.runtime.SceneRenderableKey
import com.awakekt.awake.scene.runtime.SceneTextureFactory

/**
 * DSL scope for configuring scene assets, mesh generators, materials, and asset resolvers.
 */
@AwakeSceneDsl
class SceneAssetsDsl internal constructor() {
    private val meshFactories = linkedMapOf<String, SceneMeshFactory>()
    private val textureFactories = linkedMapOf<String, SceneTextureFactory>()
    private val materialFactories = linkedMapOf<String, SceneMaterialFactory>()
    private val rendererFactories = linkedMapOf<SceneRenderableKey, SceneMeshRendererFactory>()
    private val resolvers = mutableListOf<SceneAssetResolver>()

    /**
     * Registers a dynamic asset resolver for loading runtime assets.
     *
     * @param resolver The [SceneAssetResolver] implementation to register.
     */
    fun resolver(resolver: SceneAssetResolver) {
        resolvers += resolver
    }

    /**
     * Registers a factory creating a named mesh.
     *
     * @param name Unique name of the mesh asset.
     * @param factory Factory lambda producing the mesh on demand.
     */
    fun mesh(name: String, factory: SceneMeshFactory) {
        require(name.isNotBlank()) { "Scene mesh names must not be blank." }
        meshFactories[name] = factory
    }

    /**
     * Registers a static mesh from pre-computed [MeshGeometry].
     *
     * @param name Unique name of the mesh asset.
     * @param geometry Precomputed geometry containing vertices, indices, and format.
     */
    fun mesh(name: String, geometry: MeshGeometry) {
        mesh(name) { renderer.createMesh(geometry) }
    }

    /**
     * Generates and registers a procedural mesh using [MeshGenerateScope].
     *
     * @param name Unique name of the mesh asset.
     * @param block Builder lambda specifying procedural vertex and index generation.
     */
    fun proceduralMesh(name: String, block: MeshGenerateScope.() -> Unit) {
        mesh(name, generate(block))
    }

    /**
     * Registers a factory creating a named material.
     *
     * @param name Unique name of the material asset.
     * @param factory Factory lambda producing the material on demand.
     */
    fun material(name: String, factory: SceneMaterialFactory) {
        require(name.isNotBlank()) { "Scene material names must not be blank." }
        materialFactories[name] = factory
    }

    /**
     * Registers a custom renderer factory for a mesh-material combination.
     *
     * @param mesh Unique name of the associated mesh.
     * @param material Unique name of the associated material.
     * @param factory Factory lambda creating the mesh renderer.
     */
    fun renderer(
        mesh: String,
        material: String,
        factory: SceneMeshRendererFactory,
    ) {
        rendererFactories[SceneRenderableKey(mesh, material)] = factory
    }

    /** Registers decoded pixels under the name a scene sprite uses. Called on first GPU use. */
    fun texture(name: String, factory: SceneTextureFactory) {
        require(name.isNotBlank()) { "Scene texture names must not be blank." }
        textureFactories[name] = factory
    }

    internal fun buildLibrary(): SceneAssetLibrary = SceneAssetLibrary(
        meshFactories = meshFactories.toMap(),
        materialFactories = materialFactories.toMap(),
        rendererFactories = rendererFactories.toMap(),
        dynamicResolvers = resolvers.toList(),
        textureFactories = textureFactories.toMap(),
    )
}
