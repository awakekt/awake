/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.gltf.LoadedSkinnedScene
import com.awakekt.awake.asset.gltf.firstSkinnedAsset
import com.awakekt.awake.asset.gltf.toAnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.core.animation.AnimationPose
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime

/**
 * Gives every skinned glTF model an [Animator] playing its first clip on a loop. A model drawn as
 * parts (`gltf-primitive:<path>#<i>`) animates once: the parts' parent node holds the animator and
 * each skinned part draws its pose, skinned to the model's first skin. A part with no joints, a
 * rigid piece no joint carries, draws in place and takes no pose. A model with no clips, such as a
 * piece of armour another skeleton poses, stands in its rest pose with no animator, and whoever
 * owns it can replace the pose.
 */
internal fun SceneAppLifecycleRuntime.startSkinnedAnimations(models: GltfAssetResolver, among: Collection<Entity>? = null) {
    val assets = requireAssetLibrary()
    val drawn = mutableListOf<Pair<Entity, String>>()
    world.queryEach(MeshRenderer::class) { entity, renderer ->
        if (among != null && entity !in among) return@queryEach
        val skinned = renderer.mesh.takeIf { it.format.isSkinned }
        skinned?.let(assets::meshName)?.let { drawn += entity to it }
    }
    for ((entity, mesh) in drawn) {
        val path = models.modelPath(mesh)
        val scene = models.getLoadedScene(path)
        val skin = scene?.firstSkinnedAsset()?.skin ?: continue
        val owner = if (mesh == path) entity else world.get<Transform>(entity)?.parent ?: entity
        val pose = world.get<SkinnedPose>(owner) ?: world.startPose(owner, scene, skin)
        if (owner != entity) world.add(entity, pose)
    }
}

/** Gives [owner] [scene]'s first clip on a loop, or, when the model has no clips, its rest pose and no animator. */
private fun World.startPose(owner: Entity, scene: LoadedSkinnedScene, skin: Skin): SkinnedPose {
    if (scene.clips.isEmpty()) return SkinnedPose(AnimationPose(scene.skeleton).jointPalette(skin)).also { add(owner, it) }
    val clips = scene.toAnimationLibrary()
    val player = AnimationPlayer(clips)
    clips.clips.keys.firstOrNull()?.let { player.play(it) }
    add(owner, Animator(player, skin))
    return SkinnedPose(player.update(0f).jointPalette(skin)).also { add(owner, it) }
}
