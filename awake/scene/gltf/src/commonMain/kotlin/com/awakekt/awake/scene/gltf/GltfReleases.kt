/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

/**
 * Which models a [GltfAssetResolver] lets go of the CPU copies of once they are uploaded, and what it
 * has let go of. A model's geometry and images were kept for the resolver's life so that a mesh or
 * material released and needed again could be made again: 27 MiB of a populated scene's live heap a
 * consumer measured, most of it never made twice.
 *
 * A model is releasable when it was loaded through the async load path, which a scene makes its
 * meshes through, or marked so; [kept] models never are, for a host that reads their geometry or
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

    private fun belongsTo(key: String, path: String): Boolean =
        key == path || key.startsWith("$path#") || key.startsWith("$PRIMITIVE_MESH_PREFIX$path#")
}

/** The model a material key belongs to: the key itself, or what comes before its slot. */
internal fun materialModelPath(key: String): String = key.substringBefore('#')
