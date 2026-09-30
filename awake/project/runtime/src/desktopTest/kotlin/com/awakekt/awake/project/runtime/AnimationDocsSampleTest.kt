/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.gltf.firstSkinnedAsset
import com.awakekt.awake.asset.gltf.toAnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

/** The "Animation" guide: a skinned glTF model given an animator by hand, and the system that plays it. */
class AnimationDocsSampleTest {

    @Test
    fun anAnimatorMovesTheJointsOfASkinnedModel() = runTest {
        val models = GltfAssetResolver().apply { setAssetSource(files("assets/models/arm.gltf" to skinnedArmGltf())) }
        models.preload("assets/models/arm.gltf")
        val game = app {
            scene("arm") {
                assets { resolver(models) }
                scene(rendering("skinned.scene.json"))
                // --8<-- [start:system]
                frameSystem("animation") { AnimationSystem() }
                // --8<-- [end:system]
                // --8<-- [start:animator]
                onReady {
                    val arm = requireEntity("arm")
                    val model = checkNotNull(models.getLoadedScene("assets/models/arm.gltf"))
                    val skin = checkNotNull(model.firstSkinnedAsset()).skin
                    val player = AnimationPlayer(model.toAnimationLibrary())
                    player.play("clip_0") // an unnamed clip is clip_<index>
                    world.add(arm, Animator(player, skin))
                    world.add(arm, SkinnedPose(player.update(0f).jointPalette(skin)))
                }
                // --8<-- [end:animator]
            }
        }
        game.ready(DocsRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val pose = runtime.world.get<SkinnedPose>(runtime.requireEntity("arm"))!!
        val before = pose.jointPalette.copyOf()
        repeat(20) { game.update(FRAME, WIDTH, HEIGHT) }

        assertFalse(before.contentEquals(pose.jointPalette), "the joints move as the clip plays")
    }
}
