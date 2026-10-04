/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.SceneAppDsl
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

/** Spin, locomotion, keyframes and particles, for the scenes that have them. */
internal fun SceneAppDsl.motionSystems(scene: SceneDocument, particleSprites: Map<String, TextureAsset>) {
    if (scene.has(SceneSpinControl::class)) {
        frameSystem("spin-clock") { SpinClockSystem() }
        frameSystem("spin") { SpinSystem() }
    }
    if (scene.has(SceneLocomotionAnimation::class)) frameSystem("locomotion") { LocomotionAnimationSystem() }
    if (scene.has(SceneKeyframeAnimation::class)) frameSystem("keyframes") { KeyframeAnimationSystem() }
    if (scene.has(SceneParticleEmitter::class)) {
        var content: ParticleContentSystem? = null
        frameSystem("particle-content") { ParticleContentSystem(renderer, particleSprites).also { content = it } }
        frameSystem("particles") { ParticleSystem() }
        onDispose { content?.release() }
    }
}

/** Turns each [SpinControl] at its own speed; [SpinSystem] only applies the angle. */
private class SpinClockSystem : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(SpinControl::class) { _, spin -> spin.radians += spin.speed * delta }
    }
}
