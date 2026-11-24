/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family2System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.componentType
import com.awakekt.awake.ecs.family
import com.awakekt.awake.ecs.get
import com.awakekt.awake.ecs.has
import com.awakekt.awake.scene.rendering.mesh.DetailPins

/**
 * ECS System synchronizing modular skeletal characters with active skeletal animation poses.
 *
 * Each frame:
 * 1. Queries all entities carrying both [ModularCharacterComponent] and [Animator].
 * 2. Skips bone posing for characters the renderer culled last frame and drew nowhere, advancing
 *    only their animation clock so clip timing and crossfades stay exact, as [AnimationSystem] does.
 * 3. Applies the opt-in [AnimationUpdateRate] by camera distance (always full rate for pinned
 *    characters via [com.awakekt.awake.scene.rendering.mesh.DetailPin]), and skips held poses via
 *    [AnimationPlayer.updateIfChanged].
 * 4. Updates or attaches the joint palette in [SkinnedPose] in place so all modular slot meshes
 *    deform against the shared skeleton without redundant CPU evaluations or per-frame allocations.
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
class ModularSkeletalSystem(private val hasRenderer: Boolean) : Family2System<ModularCharacterComponent, Animator>(ModularCharacterComponent::class, Animator::class) {
    private var updateRate: AnimationUpdateRate = AnimationUpdateRate.EveryFrame
    private val detailPins = DetailPins()
    private var attachedPins: DetailPins? = null

    /** A system for a host that draws, posing every drawn visible character every frame. */
    constructor() : this(hasRenderer = true)

    /**
     * A system with an opt-in distance-based [updateRate] (see [AnimationUpdateRate]), matching
     * [AnimationSystem].
     *
     * @param updateRate How often a drawn modular character far from the camera is posed.
     * @param hasRenderer Whether anything draws the poses; false skips posing all but the characters named above.
     */
    constructor(
        updateRate: AnimationUpdateRate,
        hasRenderer: Boolean = true,
    ) : this(hasRenderer) {
        this.updateRate = updateRate
    }

    override fun begin(world: World, delta: Float) {
        if (!hasRenderer) {
            attachedPins = null
            world.family(SOCKET).forEach { _, socket ->
                if (socket.enabled) world.get(socket.targetEntity, SKINNED_POSE)?.boneRead = true
            }
        } else {
            attachedPins = if (updateRate === AnimationUpdateRate.EveryFrame) null else detailPins.attach(world)
        }
    }

    override fun process(
        world: World,
        delta: Float,
        entity: Entity,
        first: ModularCharacterComponent,
        second: Animator,
    ) {
        if (!first.isVisible) return
        val skinnedPose = world.get(entity, SKINNED_POSE)
        if (!hasRenderer) {
            processHeadless(world, delta, entity, first, second.player, skinnedPose)
        } else if (skinnedPose == null) {
            pose(world, entity, first, second.player, null, delta)
        } else {
            processDrawn(world, delta, entity, first, second.player, skinnedPose)
        }
    }

    private fun processHeadless(
        world: World,
        delta: Float,
        entity: Entity,
        character: ModularCharacterComponent,
        player: AnimationPlayer,
        skinnedPose: SkinnedPose?,
    ) {
        if (skinnedPose == null || readsBones(world, entity, skinnedPose)) {
            pose(world, entity, character, player, skinnedPose, delta)
        } else {
            player.advance(delta)
        }
    }

    private fun processDrawn(
        world: World,
        delta: Float,
        entity: Entity,
        character: ModularCharacterComponent,
        player: AnimationPlayer,
        skinnedPose: SkinnedPose,
    ) {
        val unseen = skinnedPose.culled && !skinnedPose.drawn
        skinnedPose.culled = false
        skinnedPose.drawn = false
        if (unseen) {
            player.advance(delta)
        } else {
            val interval = if (attachedPins?.isPinned(entity) == true) {
                0f
            } else {
                updateRate.intervalFor(skinnedPose.cameraDistance)
            }
            skinnedPose.nextPoseIn -= delta
            if (interval > 0f && skinnedPose.nextPoseIn > 0f) {
                player.advance(delta)
            } else {
                skinnedPose.nextPoseIn = (skinnedPose.nextPoseIn + interval).coerceAtLeast(0f)
                pose(world, entity, character, player, skinnedPose, delta)
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
        val skin = character.skin
        if (skinnedPose == null) {
            val pose = player.update(delta)
            // Adding SkinnedPose does not change the (character, animator) family being walked.
            world.add(entity, SkinnedPose(pose.jointPalette(skin)))
            return
        }
        val palette = skinnedPose.jointPalette
        val fits = palette.size == skin.joints.size * MATRIX_FLOATS
        val pose = if (fits) player.updateIfChanged(delta) ?: return else player.update(delta)
        // In place when the palette fits: a new one per character per frame is garbage that lives a frame.
        skinnedPose.jointPalette = if (fits) {
            pose.jointPalette(skin, palette)
        } else {
            pose.jointPalette(skin)
        }
    }

    override fun update(world: World, delta: Float) = runTick(world, delta) { entity, first, second ->
        process(world, delta, entity, first, second)
    }

    // Resolved once for the process, not per entity per frame (#1429).
    private companion object {
        val SKINNED_POSE = componentType<SkinnedPose>()
        val SOCKET = componentType<SocketAttachmentComponent>()
        val POSED_WITHOUT_RENDERER = componentType<PosedWithoutRenderer>()
    }
}

private const val MATRIX_FLOATS = 16
