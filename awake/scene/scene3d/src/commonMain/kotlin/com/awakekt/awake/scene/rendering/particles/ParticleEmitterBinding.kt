/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.particles

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/**
 * A [SceneParticleEmitter] on a live entity; [ParticleContentSystem] gives it its [ParticleEmitter].
 *
 * @property settings The emitter as the scene file describes it, kept so the scene exports unchanged.
 */
class ParticleEmitterSource(val settings: SceneParticleEmitter)

/** Loads [SceneParticleEmitter] as the entity's [ParticleEmitterSource], and exports it back unchanged. */
object ParticleEmitterBinding : SceneComponentBinding<ParticleEmitterSource, SceneParticleEmitter> {
    override val componentClass: KClass<ParticleEmitterSource> = ParticleEmitterSource::class
    override val schemaClass: KClass<SceneParticleEmitter> = SceneParticleEmitter::class
    override val serializer = SceneParticleEmitter.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneParticleEmitter,
        context: SceneResolutionContext,
    ) {
        world.add(entity, ParticleEmitterSource(component))
    }

    override fun export(world: World, entity: Entity, component: ParticleEmitterSource): SceneParticleEmitter =
        component.settings
}
