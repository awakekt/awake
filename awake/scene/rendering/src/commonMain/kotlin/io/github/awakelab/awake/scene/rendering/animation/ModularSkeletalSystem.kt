/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering.animation

import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World

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
