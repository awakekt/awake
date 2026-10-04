/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.SceneSpinControl
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.core.transform.SpinSystem
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimationSystem
import com.awakekt.awake.scene.rendering.animation.LocomotionAnimationSystem
import com.awakekt.awake.scene.rendering.animation.SceneKeyframeAnimation
import com.awakekt.awake.scene.rendering.animation.SceneLocomotionAnimation
import com.awakekt.awake.scene.rendering.particles.ParticleContentSystem
import com.awakekt.awake.scene.rendering.particles.ParticleSystem
import com.awakekt.awake.scene.rendering.particles.SceneParticleEmitter
import com.awakekt.awake.scene.runtime.SceneSystemPhase

/** Spin, locomotion, keyframes and particles, for the scenes that have them. */
internal fun MutableList<PlaySpec>.addMotionSpecs(scene: SceneDocument) {
    if (scene.has(SceneSpinControl::class)) {
        add(PlaySpec("spin-clock", SceneSystemPhase.Frame) { SpinClockSystem() })
        add(PlaySpec("spin", SceneSystemPhase.Frame) { SpinSystem() })
    }
    if (scene.has(SceneLocomotionAnimation::class)) {
        add(PlaySpec("locomotion", SceneSystemPhase.Frame) { LocomotionAnimationSystem() })
    }
    if (scene.has(SceneKeyframeAnimation::class)) {
        add(PlaySpec("keyframes", SceneSystemPhase.Frame) { KeyframeAnimationSystem() })
    }
    if (scene.has(SceneParticleEmitter::class)) {
        add(PlaySpec("particle-content", SceneSystemPhase.Frame) { ParticleContentSystem(it.renderer, it.particleSprites) })
        add(PlaySpec("particles", SceneSystemPhase.Frame) { ParticleSystem() })
    }
}

/** Turns each [SpinControl] at its own speed; [SpinSystem] only applies the angle. */
private class SpinClockSystem : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(SpinControl::class) { _, spin -> spin.radians += spin.speed * delta }
    }
}
