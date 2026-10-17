/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial

/**
 * Pluggable resolver for dynamic GPU assets. Allows plugins (e.g. glTF, terrain, a custom model format)
 * to resolve asset names on demand without hardcoding asset names or paths in the scene fixture.
 */
interface SceneAssetResolver {
    /**
     * Determines whether this resolver can construct a [Mesh] for the given asset [name].
     *
     * @param name The descriptive name or URI of the requested mesh asset.
     * @return `true` if this resolver can handle the mesh asset, `false` otherwise.
     */
    fun canResolveMesh(name: String): Boolean = false

    /**
     * Creates and uploads the requested [Mesh] to the GPU.
     *
     * @param runtime The active [SceneAppLifecycleRuntime] providing engine services and device access.
     * @param name The descriptive name or URI of the requested mesh asset.
     * @return The created [Mesh] instance, or `null` if resolution failed.
     */
    fun createMesh(runtime: SceneAppLifecycleRuntime, name: String): Mesh? = null

    /**
     * Determines whether this resolver can construct a [Material] for the given asset [name].
     *
     * @param name The descriptive name or URI of the requested material asset.
     * @return `true` if this resolver can handle the material asset, `false` otherwise.
     */
    fun canResolveMaterial(name: String): Boolean = false

    /**
     * Creates and configures the requested [Material] on the GPU.
     *
     * @param runtime The active [SceneAppLifecycleRuntime] providing engine services and device access.
     * @param name The descriptive name or URI of the requested material asset.
     * @return The created [Material] instance, or `null` if resolution failed.
     */
    fun createMaterial(runtime: SceneAppLifecycleRuntime, name: String): Material? = null

    /**
     * The factors material [name] was authored with (a glTF file's metallic, roughness, colours and
     * alpha mode), drawn for an entity that has no [PbrMaterial] of its own; null when it has none.
     *
     * @param name The name of the material.
     * @return The authored [PbrMaterial] factors, or `null` if unspecified.
     */
    fun materialDefaults(name: String): PbrMaterial? = null

    /**
     * The factors a mesh drawn with material [material] was authored with. Several meshes can share
     * one material name, such as the parts of a skinned model that draw untextured, so a resolver that
     * keeps factors per mesh overrides this. By default it is [materialDefaults] for [material].
     *
     * @param mesh The name of the mesh.
     * @param material The name of the material.
     * @return The authored [PbrMaterial] factors, or `null` if unspecified.
     */
    fun materialDefaults(mesh: String, material: String): PbrMaterial? = materialDefaults(material)

    /**
     * The texture a `sprite` or `tilemap` names [name] as it stands now, or null when this resolver
     * does not supply it. [SceneAssetLibrary.texture] asks on each frame something using it is drawn,
     * until it is [SceneTextureLoad.Loaded], so a resolver that loads it starts on the first ask and
     * answers [SceneTextureLoad.Loading] meanwhile; [AssetSourceTextureResolver] loads them from files.
     * A texture registered by name with the scene's assets is used without asking.
     *
     * @param name The texture's name, as the `sprite` or `tilemap` gives it.
     * @return Where the texture stands, or `null` if this resolver does not supply it.
     */
    fun texture(name: String): SceneTextureLoad? = null
}
