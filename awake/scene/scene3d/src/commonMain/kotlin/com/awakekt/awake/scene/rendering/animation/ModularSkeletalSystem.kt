/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.componentType
import com.awakekt.awake.ecs.family
import com.awakekt.awake.ecs.get
import com.awakekt.awake.ecs.has

/**
 * ECS System synchronizing modular skeletal characters with active skeletal animation poses.
 *
 * Each frame:
 * 1. Queries all entities carrying both [ModularCharacterComponent] and [Animator].
 * 2. Evaluates the skeletal pose from the animation player once.
 * 3. Updates or attaches the joint palette in [SkinnedPose] so all modular slot meshes
 *    deform against the shared skeleton without redundant CPU evaluations. A palette that already
 *    fits the character's skin is written in place, so a posed character allocates nothing per frame.
 *
 * With [hasRenderer] false, for a host that draws nothing such as a game server, every visible
 * character's clock, clip switch and crossfade advances exactly as it does on a host that draws, but
 * no bone is posed and [SkinnedPose.jointPalette] is left as it was, as [AnimationSystem] does. Two
 * kinds of character are still posed, because something reads their bones without drawing them: the
 * target of an enabled [SocketAttachmentComponent], which [SocketAttachmentSystem] follows, and a
 * character carrying [PosedWithoutRenderer]. A character with no [SkinnedPose] yet is posed once, so
 * it has one to read.
 *
 * @param hasRenderer Whether anything draws the poses; false skips posing all but the characters named above.
 */
class ModularSkeletalSystem(private val hasRenderer: Boolean) : System {
    /** A system for a host that draws, posing every visible character every frame. */
    constructor() : this(hasRenderer = true)

    /**
     * Updates all animated [ModularCharacterComponent] entities in [world] by [delta] seconds.
     *
     * @param world Active ECS world containing modular character and animator components.
     * @param delta Elapsed time in seconds since the previous frame.
     */
    override fun update(world: World, delta: Float) {
        if (!hasRenderer) {
            world.family(SOCKET).forEach { _, socket ->
                if (socket.enabled) world.get(socket.targetEntity, SKINNED_POSE)?.boneRead = true
            }
        }
        world.family(CHARACTER, ANIMATOR).forEach { entity, character, animator ->
            if (!character.isVisible) return@forEach
            val skinnedPose = world.get(entity, SKINNED_POSE)
            if (hasRenderer || skinnedPose == null || readsBones(world, entity, skinnedPose)) {
                pose(world, entity, character, animator.player, skinnedPose, delta)
            } else {
                animator.player.advance(delta)
            }
        }
    }

    /** Whether something reads [skinnedPose]'s bones on a host that draws nothing; clears the socket mark. */
    private fun readsBones(world: World, entity: Entity, skinnedPose: SkinnedPose): Boolean {
        val read = skinnedPose.boneRead || world.has(entity, POSED_WITHOUT_RENDERER)
        skinnedPose.boneRead = false
        return read
    }

    /** Advances [player] by [delta] and writes its pose into the character's [SkinnedPose], adding one if missing. */
    private fun pose(
        world: World,
        entity: Entity,
        character: ModularCharacterComponent,
        player: AnimationPlayer,
        skinnedPose: SkinnedPose?,
        delta: Float,
    ) {
        val pose = player.update(delta)
        val skin = character.skin
        if (skinnedPose == null) {
            // Adding SkinnedPose does not change the (character, animator) family being walked.
            world.add(entity, SkinnedPose(pose.jointPalette(skin)))
            return
        }
        val palette = skinnedPose.jointPalette
        // In place when the palette fits: a new one per character per frame is garbage that lives a frame.
        skinnedPose.jointPalette = if (palette.size == skin.joints.size * MATRIX_FLOATS) {
            pose.jointPalette(skin, palette)
        } else {
            pose.jointPalette(skin)
        }
    }

    // Resolved once for the process, not per entity per frame (#1429).
    private companion object {
        val CHARACTER = componentType<ModularCharacterComponent>()
        val ANIMATOR = componentType<Animator>()
        val SKINNED_POSE = componentType<SkinnedPose>()
        val SOCKET = componentType<SocketAttachmentComponent>()
        val POSED_WITHOUT_RENDERER = componentType<PosedWithoutRenderer>()
    }
}

private const val MATRIX_FLOATS = 16
