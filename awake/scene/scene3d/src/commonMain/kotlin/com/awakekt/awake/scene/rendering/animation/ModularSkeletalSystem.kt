/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World

/**
 * ECS System synchronizing modular skeletal characters with active skeletal animation poses.
 *
 * Each frame:
 * 1. Queries all entities carrying both [ModularCharacterComponent] and [Animator].
 * 2. Evaluates the skeletal pose from the animation player once.
 * 3. Updates or attaches the joint palette in [SkinnedPose] so all modular slot meshes
 *    deform against the shared skeleton without redundant CPU evaluations.
 */
class ModularSkeletalSystem : System {

    /**
     * Updates all animated [ModularCharacterComponent] entities in [world] by [delta] seconds.
     *
     * @param world Active ECS world containing modular character and animator components.
     * @param delta Elapsed time in seconds since the previous frame.
     */
    override fun update(world: World, delta: Float) {
        world.queryEach(ModularCharacterComponent::class, Animator::class) { entity, character, animator ->
            if (!character.isVisible) return@queryEach

            // Evaluate animation pose from player
            val currentPose = animator.player.update(delta)
            val jointPalette = currentPose.jointPalette(character.skin)

            val existingPose = world.get<SkinnedPose>(entity)
            if (existingPose != null) {
                // Update in-place
                jointPalette.copyInto(existingPose.jointPalette)
            } else {
                world.add(entity, SkinnedPose(jointPalette))
            }
        }
    }
}
