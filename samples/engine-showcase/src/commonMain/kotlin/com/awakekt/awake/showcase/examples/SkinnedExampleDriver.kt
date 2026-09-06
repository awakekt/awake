/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.asset.gltf.GltfParser
import com.awakekt.awake.asset.gltf.firstSkinnedAsset
import com.awakekt.awake.asset.gltf.toAnimationLibrary
import com.awakekt.awake.core.animation.AnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.document.Scene
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime

/** CesiumMan.gltf, parsed once and driven each frame while the skinned-mesh example is
 * active. The one example whose content isn't pure data -- joint-palette sampling from a
 * playback clock is real per-frame simulation, not fakeable as a scene document. */
internal object SkinnedExampleDriver {
    private var vertices: FloatArray? = null
    private var indices: IntArray? = null
    private var skin: Skin? = null
    private var animationLibrary: AnimationLibrary? = null

    suspend fun preload() {
        if (skin != null) return
        val bytes = readResourceBytes("assets/models/CesiumMan.gltf")
        val loaded = GltfParser.parseSkinned(bytes.decodeToString())
        val asset =
            requireNotNull(loaded.firstSkinnedAsset()) { "CesiumMan.gltf has no skinned node." }
        vertices = asset.mesh.toInterleavedSkinned()
        indices = asset.mesh.indices
        skin = asset.skin
        animationLibrary = loaded.toAnimationLibrary()
    }

    fun createMesh(runtime: SceneAppLifecycleRuntime): Mesh = runtime.renderer.createMesh(
        MeshGeometry(
            requireNotNull(vertices),
            requireNotNull(indices),
            format = VertexFormat.PositionNormalColorSkin,
        ),
    )

    fun createMaterial(runtime: SceneAppLifecycleRuntime): Material =
        runtime.renderer.createMaterial(SkinnedUniformLayout)

    /** CesiumMan's bone hierarchy, shared with the ragdoll showcase rather than parsed twice. */
    internal fun skeleton() = requireNotNull(animationLibrary).skeleton

    /** CesiumMan's skinning joints, for turning a posed skeleton into a joint palette. */
    internal fun skin() = requireNotNull(skin)

    /** Creates one independent player for the instantiated entity; clips/library remain shared
     * source data, while timing and pose state belong to this entity alone. */
    fun attachPose(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        val currentSkin = requireNotNull(skin)
        val player = AnimationPlayer(requireNotNull(animationLibrary))
        val firstClip = requireNotNull(animationLibrary).clips.keys.first()
        player.play(firstClip)
        val node = instance.roots.find { it.name == "skinned-mesh" } ?: return
        runtime.world.add(node.entity, Animator(player, currentSkin))
        runtime.world.add(node.entity, SkinnedPose(player.update(0f).jointPalette(currentSkin)))
    }
}
