/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins

import com.awakekt.awake.asset.gltf.GltfParser
import com.awakekt.awake.asset.gltf.LoadedSkinnedScene
import com.awakekt.awake.asset.gltf.firstSkinnedAsset
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneAssetResolver
import com.awakekt.awake.studio.fixture.StudioFixtureBounds

/**
 * Generic glTF asset resolver for Awake Studio.
 *
 * Resolves ANY `.gltf` or `.glb` path dynamically on demand. Zero hardcoded model or character
 * identifiers. Caches loaded scenes so that entities referencing them can have their skeletons,
 * poses, and animation players bound automatically.
 */
class GltfAssetResolver : SceneAssetResolver {
    private val loadedScenes = mutableMapOf<String, LoadedSkinnedScene>()

    fun getLoadedScene(path: String): LoadedSkinnedScene? = loadedScenes[path]

    suspend fun preload(path: String) {
        if (path in loadedScenes) return
        val bytes = readResourceBytes(path)
        val loaded = GltfParser.parseSkinned(bytes.decodeToString())
        loadedScenes[path] = loaded
    }

    override fun canResolveMesh(name: String): Boolean =
        name.endsWith(".gltf", ignoreCase = true) || name.endsWith(".glb", ignoreCase = true)

    override fun createMesh(runtime: SceneAppLifecycleRuntime, name: String): Mesh? {
        val scene = loadedScenes[name] ?: error(
            "glTF asset '$name' has not been preloaded. Ensure the scene or plugin preloaded it.",
        )
        val skinnedAsset = scene.firstSkinnedAsset()
        val geometry = if (skinnedAsset != null) {
            MeshGeometry(
                skinnedAsset.mesh.toInterleavedSkinned(),
                skinnedAsset.mesh.indices,
                format = VertexFormat.PositionNormalColorSkin,
            )
        } else {
            return null
        }
        StudioFixtureBounds.register(name, geometry)
        return runtime.renderer.createMesh(geometry)
    }

    override fun canResolveMaterial(name: String): Boolean =
        name == "skinned-material"

    override fun createMaterial(runtime: SceneAppLifecycleRuntime, name: String): Material? {
        if (name == "skinned-material") {
            return runtime.renderer.createMaterial(SkinnedUniformLayout)
        }
        return null
    }

    companion object {
        val instance = GltfAssetResolver()
    }
}
