/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.render.renderer.CullMode
import io.github.awakelab.awake.scene.document.SceneMeshRenderer
import io.github.awakelab.awake.scene.document.SceneRenderableRequest
import io.github.awakelab.awake.scene.rendering.mesh.MeshRenderer

typealias SceneMeshFactory = SceneAppLifecycleRuntime.() -> Mesh
typealias SceneMaterialFactory = SceneAppLifecycleRuntime.() -> Material
typealias SceneMeshRendererFactory = SceneAppLifecycleRuntime.() -> MeshRenderer

data class SceneRenderableKey(
    val mesh: String,
    val material: String,
)

/**
 * Builds and caches a scene's GPU assets by name, and owns when they die.
 *
 * ### Why there is a reference count
 *
 * Every asset used to live until [dispose] at session teardown, which is correct for a scene
 * loaded once. It is a leak for a streamed one: `WorldPartitionSystem` can now load and unload
 * cells continuously, and an unloading cell that removes its entities has no way to free the
 * meshes those entities referenced.
 *
 * [requireMesh]/[requireMaterial] therefore acquire, and [releaseMesh]/[releaseMaterial] let go.
 * An asset is destroyed when its last holder releases it, so two cells sharing a mesh do not
 * destroy it out from under each other -- the case that makes a plain "destroy on unload" wrong.
 *
 * A caller that never releases keeps the old behaviour exactly: the count stays above zero and
 * [dispose] cleans up. No existing caller changed.
 *
 * ### The budget bounds retained meshes, not live ones
 *
 * A budget cannot evict what something is drawing, so it can only govern assets nothing holds.
 * With plain refcounting there are none -- the last release destroys immediately -- so
 * [retainedMeshBudgetBytes] changes that: a released mesh is *kept* for reuse instead, and
 * re-acquiring it costs nothing. That is what a player crossing a cell boundary back and forth
 * needs, and it is also the only way an eviction policy has anything to act on.
 *
 * Over budget, the least-recently-released mesh is destroyed first. A budget of zero -- the
 * default -- retains nothing and behaves exactly as destroy-on-last-release did, so this is opt-in.
 *
 * Materials are refcounted but not budgeted: a material is a small uniform buffer plus textures
 * the `Renderer` owns, so counting one would either miss most of its cost or double-count a
 * shared texture. Meshes are where a streamed world's memory actually goes.
 *
 * Still no eviction of *live* assets, which would mean destroying something mid-draw.
 */
class SceneAssetLibrary(
    private val meshFactories: Map<String, SceneMeshFactory>,
    private val materialFactories: Map<String, SceneMaterialFactory>,
    private val rendererFactories: Map<SceneRenderableKey, SceneMeshRendererFactory>,
    private val dynamicResolvers: List<SceneAssetResolver> = emptyList(),
    /** Bytes of released-but-kept mesh to hold before evicting. Zero destroys on last release. */
    private val retainedMeshBudgetBytes: Long = 0,
) {
    private val meshes = linkedMapOf<String, Mesh>()
    private val materials = linkedMapOf<String, Material>()
    private val meshHolders = mutableMapOf<String, Int>()
    private val materialHolders = mutableMapOf<String, Int>()

    /** Released meshes kept for reuse, eldest first -- `LinkedHashMap` iteration order IS the
     * eviction order, so re-inserting on release is what makes this least-recently-released. */
    private val retainedMeshes = linkedMapOf<String, Mesh>()
    private var retainedBytes = 0L

    /** Builds [name] on first use and takes a reference to it; see [releaseMesh]. */
    fun requireMesh(runtime: SceneAppLifecycleRuntime, name: String): Mesh {
        meshHolders[name] = (meshHolders[name] ?: 0) + 1
        // Revived before building: a retained mesh is the whole point of retaining it.
        retainedMeshes.remove(name)?.let { revived ->
            retainedBytes -= revived.sizeBytes
            meshes[name] = revived
            return revived
        }
        return meshes.getOrPut(name) {
            val factory = meshFactories[name]
            if (factory != null) return@getOrPut runtime.factory()
            for (resolver in dynamicResolvers) {
                if (resolver.canResolveMesh(name)) {
                    val resolved = resolver.createMesh(runtime, name)
                    if (resolved != null) return@getOrPut resolved
                }
            }
            error("No scene mesh named '$name' is registered or could be resolved by installed plugins.")
        }
    }

    /** Builds [name] on first use and takes a reference to it; see [releaseMaterial]. */
    fun requireMaterial(runtime: SceneAppLifecycleRuntime, name: String): Material {
        materialHolders[name] = (materialHolders[name] ?: 0) + 1
        return materials.getOrPut(name) {
            val factory = materialFactories[name]
            if (factory != null) return@getOrPut runtime.factory()
            for (resolver in dynamicResolvers) {
                if (resolver.canResolveMaterial(name)) {
                    val resolved = resolver.createMaterial(runtime, name)
                    if (resolved != null) return@getOrPut resolved
                }
            }
            error("No scene material named '$name' is registered or could be resolved by installed plugins.")
        }
    }

    /**
     * Drops one reference to [name], destroying the mesh when the last holder lets go.
     *
     * Releasing something never acquired throws rather than passing quietly: it means a caller's
     * acquire and release are unbalanced, and the direction that stays silent is the one that
     * double-frees a live GPU buffer.
     *
     * @return true when this call destroyed the mesh.
     */
    fun releaseMesh(name: String): Boolean = release(name, meshHolders, meshes) { mesh ->
        if (retainedMeshBudgetBytes <= 0) {
            mesh.destroy()
        } else {
            retainedMeshes[name] = mesh
            retainedBytes += mesh.sizeBytes
            evictRetainedMeshes()
        }
    }

    /**
     * Destroys least-recently-released meshes until the retained set is within budget.
     *
     * A single mesh larger than the whole budget is destroyed immediately rather than retained
     * forever -- the loop empties the set, which is correct: nothing is holding it, and keeping
     * something over budget defeats having one.
     */
    private fun evictRetainedMeshes() {
        while (retainedBytes > retainedMeshBudgetBytes && retainedMeshes.isNotEmpty()) {
            val eldest = retainedMeshes.keys.first()
            retainedMeshes.remove(eldest)?.let {
                retainedBytes -= it.sizeBytes
                it.destroy()
            }
        }
    }

    /** Bytes currently held in released-but-kept meshes, for a test or a diagnostic. */
    fun retainedMeshBytes(): Long = retainedBytes

    /** Whether [name] is released but still kept for reuse. */
    fun isMeshRetained(name: String): Boolean = name in retainedMeshes

    /** [releaseMesh] for materials. */
    fun releaseMaterial(name: String): Boolean =
        release(name, materialHolders, materials) { it.destroy() }

    /** How many holders [name] has, for a test or a diagnostic. Zero when it is not loaded. */
    fun meshHolderCount(name: String): Int = meshHolders[name] ?: 0

    /** [meshHolderCount] for materials. */
    fun materialHolderCount(name: String): Int = materialHolders[name] ?: 0

    private fun <T> release(
        name: String,
        holders: MutableMap<String, Int>,
        cache: MutableMap<String, T>,
        destroy: (T) -> Unit,
    ): Boolean {
        val count = checkNotNull(holders[name]) {
            "Released '$name', which nothing is holding. An unbalanced release is how a live GPU " +
                "resource gets destroyed while something still draws it."
        }
        if (count > 1) {
            holders[name] = count - 1
            return false
        }
        holders.remove(name)
        cache.remove(name)?.let(destroy)
        return true
    }

    fun resolve(
        runtime: SceneAppLifecycleRuntime,
        request: SceneRenderableRequest,
    ): MeshRenderer {
        val key = SceneRenderableKey(
            mesh = request.meshRenderer.mesh,
            material = request.meshRenderer.material,
        )
        val customRenderer = rendererFactories[key]
        if (customRenderer != null) {
            return runtime.customRenderer()
        }
        return MeshRenderer(
            mesh = requireMesh(runtime, key.mesh),
            material = requireMaterial(runtime, key.material),
            cullMode = request.meshRenderer.cullMode.toCullMode(),
        )
    }

    private fun SceneMeshRenderer.CullMode.toCullMode(): CullMode = when (this) {
        SceneMeshRenderer.CullMode.None -> CullMode.None
        SceneMeshRenderer.CullMode.Back -> CullMode.Back
        SceneMeshRenderer.CullMode.Front -> CullMode.Front
    }

    /**
     * Destroys everything still held, whatever its reference count.
     *
     * Session teardown outranks refcounting: a consumer that leaked a reference should not leak
     * the GPU resource too. Anything already released is gone from the caches, so this cannot
     * double-destroy it.
     */
    fun dispose() {
        meshes.values.forEach { mesh -> mesh.destroy() }
        retainedMeshes.values.forEach { mesh -> mesh.destroy() }
        materials.values.forEach { material -> material.destroy() }
        meshes.clear()
        retainedMeshes.clear()
        retainedBytes = 0
        materials.clear()
        meshHolders.clear()
        materialHolders.clear()
    }
}
