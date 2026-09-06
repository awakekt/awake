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

@AwakeSceneDsl
class SceneAssetsDsl internal constructor() {
    private val meshFactories = linkedMapOf<String, SceneMeshFactory>()
    private val materialFactories = linkedMapOf<String, SceneMaterialFactory>()
    private val rendererFactories = linkedMapOf<SceneRenderableKey, SceneMeshRendererFactory>()
    private val resolvers = mutableListOf<SceneAssetResolver>()

    fun resolver(resolver: SceneAssetResolver) {
        resolvers += resolver
    }

    fun mesh(name: String, factory: SceneMeshFactory) {
        require(name.isNotBlank()) { "Scene mesh names must not be blank." }
        meshFactories[name] = factory
    }

    fun mesh(name: String, geometry: MeshGeometry) {
        mesh(name) { renderer.createMesh(geometry) }
    }

    fun proceduralMesh(name: String, block: MeshGenerateScope.() -> Unit) {
        mesh(name, generate(block))
    }

    fun material(name: String, factory: SceneMaterialFactory) {
        require(name.isNotBlank()) { "Scene material names must not be blank." }
        materialFactories[name] = factory
    }

    fun renderer(
        mesh: String,
        material: String,
        factory: SceneMeshRendererFactory,
    ) {
        rendererFactories[SceneRenderableKey(mesh, material)] = factory
    }

    internal fun buildLibrary(): SceneAssetLibrary = SceneAssetLibrary(
        meshFactories = meshFactories.toMap(),
        materialFactories = materialFactories.toMap(),
        rendererFactories = rendererFactories.toMap(),
        dynamicResolvers = resolvers.toList(),
    )
}
