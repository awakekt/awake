/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.plugins

import io.github.awakelab.awake.asset.gltf.GltfParser
import io.github.awakelab.awake.asset.gltf.LoadedSkinnedScene
import io.github.awakelab.awake.asset.gltf.firstSkinnedAsset
import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.host.readResourceBytes
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.render.renderer.SkinnedUniformLayout
import io.github.awakelab.awake.render.renderer.createMaterial
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.scene.runtime.SceneAssetResolver
import io.github.awakelab.awake.studio.fixture.StudioFixtureBounds

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
