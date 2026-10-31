/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.asset.gltf.GltfParser
import com.awakekt.awake.asset.meshlod.GltfLodAssets
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.scene.runtime.SceneMeshLevels

/**
 * The levels of detail of the static glTF models a [GltfAssetResolver] loaded: for each model with
 * `MSFT_lod` (or `_LODn`-named) levels, every mesh the model's slots name, at every level.
 *
 * A level's mesh keys are its full mesh's with the model path prefixed `lod<k>:`, so
 * `models/rock.glb` at level 2 is `lod2:models/rock.glb`, and its second part
 * `gltf-primitive:lod2:models/rock.glb#1`. Each level keeps the full mesh's vertices, normals
 * included, and only its triangles change: the cooker never moves a vertex, so shading matches
 * across levels and the levels can share one vertex buffer.
 */
internal class GltfLodLevels {
    private class ModelLevels(val count: Int, val coverage: FloatArray)

    private val models = HashMap<String, ModelLevels>()

    /**
     * Builds the levels of model [path] from its [bytes] into [meshes], for each key in [baseKeys]
     * (the meshes its slots name, and [path] itself), where [baseKeys] maps a key to whether it is
     * textured. A model whose levels don't line up with its full mesh is left with none.
     */
    @Suppress("ReturnCount") // Each early out is a model whose levels don't line up with its full mesh: it keeps none.
    fun preload(path: String, bytes: ByteArray, externalResources: Map<String, ByteArray>, baseKeys: Map<String, Boolean>, meshes: MutableMap<String, MeshGeometry>) {
        if (path in models) return
        val assets = runCatching { GltfLodAssets.read(bytes) }.getOrNull() ?: return
        val built = HashMap<String, MeshGeometry>()
        for (level in 1 until assets.count) {
            val levelBytes = assets.level(level)
            val scene = if (levelBytes.startsWithGlbMagic()) {
                GltfParser.parseScene(levelBytes, externalResources)
            } else {
                GltfParser.parseScene(levelBytes.decodeToString().toEmbeddedGlb(), externalResources)
            }
            val primitives = scene.meshes.flatMap { it.primitives }
            for ((key, textured) in baseKeys) {
                val base = meshes[key] ?: return
                // The model path names all its primitives; a part key names one, by its index.
                val levelPrimitives = if (key == path) {
                    primitives
                } else {
                    listOfNotNull(primitives.getOrNull(key.substringAfterLast('#').toIntOrNull() ?: return))
                }
                if (levelPrimitives.isEmpty()) return
                val geometry = levelPrimitives.toStaticGeometry(textured)
                // The same vertices as the full mesh, so a level is only its indices.
                if (geometry.vertices.size != base.vertices.size) return
                built[levelKey(key, path, level)] = MeshGeometry(base.vertices, geometry.indices, base.format)
            }
        }
        meshes.putAll(built)
        models[path] = ModelLevels(assets.count, coverage(assets.screenCoverage, assets.count))
    }

    /** [name]'s levels, when the model it belongs to, [path], has any. */
    fun levels(name: String, path: String): SceneMeshLevels? {
        val model = models[path] ?: return null
        return SceneMeshLevels((1 until model.count).map { levelKey(name, path, it) }, model.coverage)
    }

    /** Whether [name] is a level's mesh key rather than a full mesh's. */
    fun isLevelKey(name: String): Boolean = LEVEL_PREFIX.containsMatchIn(name)

    /** The model path a level key [name] belongs to, with its `lod<k>:` prefix removed. */
    fun baseKey(name: String): String = name.replace(LEVEL_PREFIX, "")

    /** Takes [from]'s levels of model [path], whose geometry the caller copies with the rest of its keys. */
    fun adopt(path: String, from: GltfLodLevels) {
        from.models[path]?.let { models.getOrPut(path) { it } }
    }

    fun forget(path: String, meshes: MutableMap<String, MeshGeometry>) {
        val model = models.remove(path) ?: return
        for (level in 1 until model.count) meshes.keys.removeAll { it.contains("lod$level:$path") }
    }

    private fun levelKey(key: String, path: String, level: Int): String = key.replace(path, "lod$level:$path")

    /**
     * The asset's `MSFT_screencoverage`, or, without one, 25% for the full mesh and half as much for
     * each level after it, the last drawn at any size.
     */
    private fun coverage(given: List<Float>?, count: Int): FloatArray = FloatArray(count) { level ->
        when {
            given != null -> given[level]
            level == count - 1 -> 0f
            else -> DEFAULT_BASE_COVERAGE / (1 shl level)
        }
    }

    private companion object {
        const val DEFAULT_BASE_COVERAGE = 0.25f
        val LEVEL_PREFIX = Regex("lod\\d+:")
    }
}

private fun ByteArray.startsWithGlbMagic(): Boolean =
    size >= GLB_MAGIC.size && GLB_MAGIC.indices.all { this[it] == GLB_MAGIC[it] }

private val GLB_MAGIC = "glTF".encodeToByteArray()
