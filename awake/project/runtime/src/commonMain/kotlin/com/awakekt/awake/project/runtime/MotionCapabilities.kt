/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.Particle2DSystem
import com.awakekt.awake.particles.ParticleSystem
import com.awakekt.awake.scene.core.transform.SceneSpinControl
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.core.transform.SpinSystem
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.particles.ParticleContentSystem
import com.awakekt.awake.scene.particles.SceneParticleEmitter
import com.awakekt.awake.scene.particles.TransformPlacement
import com.awakekt.awake.scene.particles.loadParticleImages
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimationSystem
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationSystem
import com.awakekt.awake.scene.rendering.animation.SceneKeyframeAnimation
import com.awakekt.awake.scene.rendering.animation.SceneLocomotionAnimation
import com.awakekt.awake.scene.rendering.light.DayCycleSystem
import com.awakekt.awake.scene.rendering.light.SceneDayCycle
import com.awakekt.awake.scene.rendering.mesh.SceneTextureClips
import com.awakekt.awake.scene.rendering.mesh.TextureClipSystem
import com.awakekt.awake.scene.scene2d.SceneParticleEmitter2D
import com.awakekt.awake.scene.scene2d.SceneSpriteClips
import com.awakekt.awake.scene.scene2d.SpriteClipSystem
import com.awakekt.awake.scene.scene2d.Transform2DPlacement

/** The name of the system that advances a scene's `sprite_clips` in its schedule. */
internal const val SPRITE_CLIPS_SYSTEM = "sprite-clips"

/** The name of the system that advances a scene's `particle_emitter_2d` components in its schedule. */
internal const val PARTICLES_2D_SYSTEM = "particles-2d"

/** Spin, locomotion, keyframes, sprite-sheet clips and sprite clips, for the scenes that have them. */
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
        if (scene.uses(SceneSpriteClips::class)) plan.frame(SPRITE_CLIPS_SYSTEM) { SpriteClipSystem() }
    }
}

/** A scene's `particle_emitter` and `particle_emitter_2d` components. */
internal object ParticlesCapability : SceneCapability {
    override val id = "com.awakekt.awake.particles"

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        if (scene.uses(SceneParticleEmitter::class)) content[CoreSceneContent.ParticleImages] = loadParticleImages(scene, files)
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (scene.uses(SceneParticleEmitter2D::class)) plan.frame(PARTICLES_2D_SYSTEM) { Particle2DSystem(Transform2DPlacement()) }
        if (!scene.uses(SceneParticleEmitter::class) || !plan.hasRenderer) return
        plan.frame("particle-content") {
            ParticleContentSystem(it.renderer, it.content[CoreSceneContent.ParticleSprites].orEmpty(), it.content[CoreSceneContent.ParticleImages].orEmpty())
        }
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
