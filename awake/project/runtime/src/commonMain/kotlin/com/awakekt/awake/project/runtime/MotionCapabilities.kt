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
import com.awakekt.awake.scene.runtime.SceneSystemRole
import com.awakekt.awake.scene.scene2d.SceneParticleEmitter2D
import com.awakekt.awake.scene.scene2d.SceneSpriteClips
import com.awakekt.awake.scene.scene2d.SpriteClipSystem
import com.awakekt.awake.scene.scene2d.Transform2DPlacement

/** The name of the system that advances a scene's `sprite_clips` in its schedule. */
internal const val SPRITE_CLIPS_SYSTEM = CoreSystems.SPRITE_CLIPS

/** The name of the system that advances a scene's `particle_emitter_2d` components in its schedule. */
internal const val PARTICLES_2D_SYSTEM = CoreSystems.PARTICLES_2D

/** Spin, locomotion, keyframes, sprite-sheet clips and sprite clips, for the scenes that have them. */
internal object MotionCapability : SceneCapability {
    override val id = "com.awakekt.awake.motion"

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (scene.uses(SceneSpinControl::class)) {
            plan.frame(CoreSystems.SPIN_CLOCK) { SpinClockSystem() }
            plan.frame(CoreSystems.SPIN) { SpinSystem() }
        }
        if (scene.uses(SceneLocomotionAnimation::class)) plan.frame(CoreSystems.LOCOMOTION) { LocomotionAnimationSystem() }
        // A headless plan still moves keyframed nodes, which colliders and sockets follow; only alpha is skipped.
        val hasRenderer = plan.hasRenderer
        if (scene.uses(SceneKeyframeAnimation::class)) plan.frame(CoreSystems.KEYFRAMES, SceneSystemRole.Presentation) { KeyframeAnimationSystem(hasRenderer) }
        if (scene.uses(SceneTextureClips::class)) plan.frame(CoreSystems.TEXTURE_CLIPS, SceneSystemRole.Presentation) { TextureClipSystem() }
        if (scene.uses(SceneSpriteClips::class)) plan.frame(CoreSystems.SPRITE_CLIPS, SceneSystemRole.Presentation) { SpriteClipSystem() }
    }
}

/** A scene's `particle_emitter` and `particle_emitter_2d` components. */
internal object ParticlesCapability : SceneCapability {
    override val id = "com.awakekt.awake.particles"

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        if (scene.uses(SceneParticleEmitter::class)) content[CoreSceneContent.ParticleImages] = loadParticleImages(scene, files)
    }

    // Planned whenever something can draw them, not only for a document that has an emitter: a scene can
    // spawn prefabs with emitters at runtime, and with nothing to emit both systems' frames are an empty query.
    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (scene.uses(SceneParticleEmitter2D::class)) plan.frame(CoreSystems.PARTICLES_2D) { Particle2DSystem(Transform2DPlacement()) }
        if (!plan.hasRenderer) return
        plan.frame(CoreSystems.PARTICLE_CONTENT, SceneSystemRole.Presentation) {
            val sprites = it.content[CoreSceneContent.ParticleSprites].orEmpty()
            // The shared loads already hold the scene's own images, so the map isn't given beside them.
            it.content[CoreSceneContent.ParticleImageLoads]?.let { loads -> ParticleContentSystem(it.renderer, sprites, emptyMap(), loads) }
                ?: ParticleContentSystem(it.renderer, sprites, it.content[CoreSceneContent.ParticleImages].orEmpty())
        }
        plan.frame(CoreSystems.PARTICLES, SceneSystemRole.Presentation) { ParticleSystem(TransformPlacement) }
    }
}

/** The sun's path and the blended sky, light and fog of a `day_cycle`. */
internal object DayCycleCapability : SceneCapability {
    override val id = "com.awakekt.awake.day-cycle"

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (scene.uses(SceneDayCycle::class)) plan.frame(CoreSystems.DAY_CYCLE, SceneSystemRole.Presentation) { DayCycleSystem() }
    }
}

/** Turns each [SpinControl] at its own speed; [SpinSystem] only applies the angle. */
private class SpinClockSystem : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(SpinControl::class) { _, spin -> spin.radians += spin.speed * delta }
    }
}
