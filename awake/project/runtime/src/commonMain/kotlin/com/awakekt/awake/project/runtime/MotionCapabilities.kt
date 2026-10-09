/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.ParticleSystem
import com.awakekt.awake.scene.core.transform.SceneSpinControl
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.core.transform.SpinSystem
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.particles.ParticleContentSystem
import com.awakekt.awake.scene.particles.SceneParticleEmitter
import com.awakekt.awake.scene.particles.TransformPlacement
import com.awakekt.awake.scene.particles.loadParticleSprites
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimationSystem
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationSystem
import com.awakekt.awake.scene.rendering.animation.SceneKeyframeAnimation
import com.awakekt.awake.scene.rendering.animation.SceneLocomotionAnimation
import com.awakekt.awake.scene.rendering.light.DayCycleSystem
import com.awakekt.awake.scene.rendering.light.SceneDayCycle
import com.awakekt.awake.scene.rendering.mesh.SceneTextureClips
import com.awakekt.awake.scene.rendering.mesh.TextureClipSystem

/** Spin, locomotion, keyframes and sprite-sheet clips, for the scenes that have them. */
internal object MotionCapability : SceneCapability {
    override val id = "com.awakekt.awake.motion"

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (scene.uses(SceneSpinControl::class)) {
            plan.frame("spin-clock") { SpinClockSystem() }
            plan.frame("spin") { SpinSystem() }
        }
        if (scene.uses(SceneLocomotionAnimation::class)) plan.frame("locomotion") { LocomotionAnimationSystem() }
        if (scene.uses(SceneKeyframeAnimation::class)) plan.frame("keyframes") { KeyframeAnimationSystem() }
        if (scene.uses(SceneTextureClips::class)) plan.frame("texture-clips") { TextureClipSystem() }
    }
}

/** A scene's `particle_emitter`s, drawn with the sprite images their `texture`s name. */
internal object ParticlesCapability : SceneCapability {
    override val id = "com.awakekt.awake.particles"

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        if (scene.uses(SceneParticleEmitter::class)) content[CoreSceneContent.ParticleSprites] = loadParticleSprites(scene, files)
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (!scene.uses(SceneParticleEmitter::class) || !plan.hasRenderer) return
        plan.frame("particle-content") { ParticleContentSystem(it.renderer, it.content[CoreSceneContent.ParticleSprites].orEmpty()) }
        plan.frame("particles") { ParticleSystem(TransformPlacement) }
    }
}

/** The sun's path and the blended sky, light and fog of a `day_cycle`. */
internal object DayCycleCapability : SceneCapability {
    override val id = "com.awakekt.awake.day-cycle"

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (scene.uses(SceneDayCycle::class)) plan.frame("day-cycle") { DayCycleSystem() }
    }
}

/** Turns each [SpinControl] at its own speed; [SpinSystem] only applies the angle. */
private class SpinClockSystem : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(SpinControl::class) { _, spin -> spin.radians += spin.speed * delta }
    }
}
