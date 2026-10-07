/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

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
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimationSystem
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationSystem
import com.awakekt.awake.scene.rendering.animation.SceneKeyframeAnimation
import com.awakekt.awake.scene.rendering.animation.SceneLocomotionAnimation
import com.awakekt.awake.scene.rendering.light.DayCycleSystem
import com.awakekt.awake.scene.rendering.light.SceneDayCycle
import com.awakekt.awake.scene.rendering.mesh.SceneTextureClips
import com.awakekt.awake.scene.rendering.mesh.TextureClipSystem
import com.awakekt.awake.scene.runtime.SceneSystemPhase

/** Spin, locomotion, keyframes, sprite-sheet clips, particles and the day cycle, for the scenes that have them. */
internal fun MutableList<SceneSystemSpec>.addMotionSpecs(scene: SceneDocument) {
    if (scene.has(SceneSpinControl::class)) {
        add(SceneSystemSpec("spin-clock", SceneSystemPhase.Frame) { SpinClockSystem() })
        add(SceneSystemSpec("spin", SceneSystemPhase.Frame) { SpinSystem() })
    }
    if (scene.has(SceneLocomotionAnimation::class)) {
        add(SceneSystemSpec("locomotion", SceneSystemPhase.Frame) { LocomotionAnimationSystem() })
    }
    if (scene.has(SceneKeyframeAnimation::class)) {
        add(SceneSystemSpec("keyframes", SceneSystemPhase.Frame) { KeyframeAnimationSystem() })
    }
    if (scene.has(SceneTextureClips::class)) {
        add(SceneSystemSpec("texture-clips", SceneSystemPhase.Frame) { TextureClipSystem() })
    }
    if (scene.has(SceneParticleEmitter::class)) {
        add(SceneSystemSpec("particle-content", SceneSystemPhase.Frame) { ParticleContentSystem(it.renderer, it.particleSprites) })
        add(SceneSystemSpec("particles", SceneSystemPhase.Frame) { ParticleSystem(TransformPlacement) })
    }
    if (scene.has(SceneDayCycle::class)) {
        add(SceneSystemSpec("day-cycle", SceneSystemPhase.Frame) { DayCycleSystem() })
    }
}

/** Turns each [SpinControl] at its own speed; [SpinSystem] only applies the angle. */
private class SpinClockSystem : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(SpinControl::class) { _, spin -> spin.radians += spin.speed * delta }
    }
}
