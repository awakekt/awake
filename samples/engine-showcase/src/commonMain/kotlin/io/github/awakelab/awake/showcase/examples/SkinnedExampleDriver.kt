/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.examples

import io.github.awakelab.awake.asset.gltf.GltfParser
import io.github.awakelab.awake.asset.gltf.firstSkinnedAsset
import io.github.awakelab.awake.asset.gltf.toAnimationLibrary
import io.github.awakelab.awake.core.animation.AnimationLibrary
import io.github.awakelab.awake.core.animation.AnimationPlayer
import io.github.awakelab.awake.core.animation.Skin
import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.host.readResourceBytes
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.render.renderer.SkinnedUniformLayout
import io.github.awakelab.awake.render.renderer.createMaterial
import io.github.awakelab.awake.scene.rendering.components.Animator
import io.github.awakelab.awake.scene.rendering.components.SkinnedPose
import io.github.awakelab.awake.scene.runtime.Scene
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime

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
