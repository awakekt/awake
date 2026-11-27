/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.asset.gltf.GltfMesh
import com.awakekt.awake.asset.gltf.LoadedSkinnedScene
import com.awakekt.awake.core.host.DeviceProfile
import com.awakekt.awake.core.host.readDeviceProfile

/**
 * Which models a [GltfAssetResolver] lets go of the CPU copies of once they are uploaded, and what it
 * has let go of. A model's geometry and images were kept for the resolver's life so that a mesh or
 * material released and needed again could be made again: 27 MiB of a populated scene's live heap a
 * consumer measured, most of it never made twice.
 *
 * A model is releasable when it was loaded through the async load path, which a scene makes its
 * meshes through, or marked so; a model marked [keep] never is, for a host that reads its geometry or
 * images itself, such as for picking, collision, or a re-upload after the GPU device is lost. What a
 * releasable model let go of comes back through the same async load: the mesh's or material's load
 * reads the model again before it is made.
 *
 * Read and written on the frame thread only.
 */
internal class GltfReleases {
    private val releasable = mutableSetOf<String>()
    private val kept = mutableSetOf<String>()

    /** Static mesh keys whose geometry was let go of once uploaded. */
    val droppedMeshes = mutableSetOf<String>()

    /** Material keys whose images were let go of once uploaded. */
    val releasedMaterials = mutableSetOf<String>()

    /** Marks model [path] releasable, unless it is [kept]. */
    fun release(path: String) {
        if (path !in kept) releasable += path
    }

    /** Keeps model [path]'s CPU copies for as long as the resolver holds it, from now on. */
    fun keep(path: String) {
        kept += path
        releasable -= path
    }

    fun isReleasable(path: String): Boolean = path in releasable

    fun isKept(path: String): Boolean = path in kept

    /** What was let go of for model [path], forgotten with it. */
    fun forget(path: String) {
        releasable -= path
        kept -= path
        droppedMeshes.removeAll { belongsTo(it, path) }
        releasedMaterials.removeAll { belongsTo(it, path) }
    }

    /** The dropped mesh keys of model [path]. */
    fun droppedMeshesOf(path: String): List<String> = droppedMeshes.filter { belongsTo(it, path) }

    /** The released material keys of model [path]. */
    fun releasedMaterialsOf(path: String): List<String> = releasedMaterials.filter { belongsTo(it, path) }

    private fun belongsTo(key: String, path: String): Boolean {
        val base = GltfLodLevels.stripLevelPrefix(key)
        return base == path || base.startsWith("$path#") || base.startsWith("$PRIMITIVE_MESH_PREFIX$path#")
    }
}

/** The model a material key belongs to: the key itself, or what comes before its slot. */
internal fun materialModelPath(key: String): String = key.substringBefore('#')

internal fun GltfPart.withoutImageBytes(): GltfPart = when (this) {
    is GltfPart.Skinned -> GltfPart.Skinned(mesh.withoutImageBytes(), textured)
    is GltfPart.Static -> GltfPart.Static(
        primitive.copy(
            baseColorImageBytes = null,
            metallicRoughnessImageBytes = null,
            normalImageBytes = null,
            occlusionImageBytes = null,
            emissiveImageBytes = null,
        ),
    )
}

internal fun LoadedSkinnedScene.withoutImageBytes(): LoadedSkinnedScene = copy(
    meshes = meshes.map(GltfMesh::withoutImageBytes),
    primitives = primitives.map { it.map(GltfMesh::withoutImageBytes) },
)

internal fun LoadedSkinnedScene.withoutGeometry(): LoadedSkinnedScene = copy(
    meshes = meshes.map(GltfMesh::withoutGeometry),
    primitives = primitives.map { it.map(GltfMesh::withoutGeometry) },
)

private fun GltfMesh.withoutImageBytes(): GltfMesh = copy(
    baseColorImageBytes = null,
    metallicRoughnessImageBytes = null,
    normalImageBytes = null,
    occlusionImageBytes = null,
    emissiveImageBytes = null,
)

private fun GltfMesh.withoutGeometry(): GltfMesh = copy(
    positions = EMPTY_FLOATS,
    normals = null,
    colors = null,
    uvs = null,
    indices = EMPTY_INTS,
    jointIndices = null,
    jointWeights = null,
    baseColorImageBytes = null,
    metallicRoughnessImageBytes = null,
    normalImageBytes = null,
    occlusionImageBytes = null,
    emissiveImageBytes = null,
)

private val EMPTY_FLOATS = FloatArray(0)
private val EMPTY_INTS = IntArray(0)

/**
 * Refcount + LRU retention policy for `GltfAssetResolver.loadedScenes` so skinned skeletons and
 * animation clips from visited zones do not accumulate forever on 4 GB RAM devices.
 */
internal class GltfSceneRetention(
    private val loadedScenes: MutableMap<String, LoadedSkinnedScene>,
    private val isKept: (String) -> Boolean,
    private val forgetModel: (String) -> Unit,
) {
    private val holders = HashMap<String, Int>()
    private val accessOrder = LinkedHashSet<String>()

    var maxRetainedScenes: Int = defaultMaxRetainedScenes()
        set(value) {
            field = value.coerceAtLeast(1)
            evictExcess()
        }

    fun touch(path: String) {
        if (path !in loadedScenes) return
        accessOrder.remove(path)
        accessOrder.add(path)
    }

    fun recordLoaded(path: String) {
        if (path !in loadedScenes) return
        accessOrder.remove(path)
        accessOrder.add(path)
        evictExcess(protectedPath = path)
    }

    fun acquire(path: String) {
        holders[path] = (holders[path] ?: 0) + 1
        if (path in loadedScenes) {
            accessOrder.remove(path)
            accessOrder.add(path)
        }
    }

    fun release(path: String) {
        val current = holders[path] ?: return
        if (current > 1) {
            holders[path] = current - 1
        } else {
            holders.remove(path)
            evictExcess()
        }
    }

    fun holderCount(path: String): Int = holders[path] ?: 0

    fun onForget(path: String) {
        accessOrder.remove(path)
        holders.remove(path)
    }

    fun trimUnused() {
        val candidates = loadedScenes.keys.filter { it !in holders && !isKept(it) }
        for (path in candidates) {
            forgetModel(path)
        }
    }

    fun evictExcess(protectedPath: String? = null) {
        while (loadedScenes.size > maxRetainedScenes) {
            val victim = accessOrder.firstOrNull {
                it != protectedPath && it in loadedScenes && it !in holders && !isKept(it)
            } ?: break
            forgetModel(victim)
        }
    }

    companion object {
        private const val LOW_RAM_BYTES: Long = 4L * 1024L * 1024L * 1024L
        const val LOW_RAM_MAX_RETAINED_SCENES: Int = 32
        const val DEFAULT_MAX_RETAINED_SCENES: Int = 128

        fun defaultMaxRetainedScenes(profile: DeviceProfile = readDeviceProfile()): Int {
            val total = profile.totalMemoryBytes
            val isLowRam = profile.isLowRamDevice || (total != null && total in 1L..LOW_RAM_BYTES)
            return if (isLowRam) LOW_RAM_MAX_RETAINED_SCENES else DEFAULT_MAX_RETAINED_SCENES
        }
    }
}
