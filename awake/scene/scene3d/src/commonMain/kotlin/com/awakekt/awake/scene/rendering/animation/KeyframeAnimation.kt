/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.lerp
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Looping keyframe tracks on an entity: its local [position], [rotation] and [scale], and the
 * [alpha] of its own `pbr_material`. Keys sit at seconds into a loop of [duration] seconds and are
 * interpolated linearly; a track holds its first key's value before that key and its last key's
 * value after the last one, then starts over when the loop does. An empty track leaves its value
 * as authored.
 *
 * [rotation] is Euler radians like the node's own and is interpolated per axis, so keys at 0 and
 * 2π make a full turn. [alpha] replaces the material's base colour alpha, which shows on a textured
 * `mesh_renderer` drawn `transparent`.
 */
@Serializable
@SerialName("keyframe_animation")
data class SceneKeyframeAnimation(
    val duration: Float,
    val position: List<SceneVec3Key> = emptyList(),
    val rotation: List<SceneVec3Key> = emptyList(),
    val scale: List<SceneVec3Key> = emptyList(),
    val alpha: List<SceneFloatKey> = emptyList(),
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (duration <= 0f) add(SceneValidationIssue(path, "keyframe_animation.duration must be greater than 0"))
        val tracks = mapOf(
            "position" to position.map { it.time },
            "rotation" to rotation.map { it.time },
            "scale" to scale.map { it.time },
            "alpha" to alpha.map { it.time },
        )
        for ((name, times) in tracks) {
            if (times.any { it < 0f || it > duration } || times.zipWithNext().any { (a, b) -> b < a }) {
                add(SceneValidationIssue(path, "keyframe_animation.$name keys must be in time order within 0..duration"))
            }
        }
    }
}

/** A [value] at [time] seconds into a [SceneKeyframeAnimation]'s loop. */
@Serializable
data class SceneVec3Key(val time: Float, val value: SceneVec3)

/** A [value] at [time] seconds into a [SceneKeyframeAnimation]'s loop. */
@Serializable
data class SceneFloatKey(val time: Float, val value: Float)

/** [SceneKeyframeAnimation] on a live entity, with how far into its loop it is. */
class KeyframeAnimation(val tracks: SceneKeyframeAnimation) {
    /** Seconds into the loop. */
    var time: Float = 0f
        internal set
}

object KeyframeAnimationBinding : SceneComponentBinding<KeyframeAnimation, SceneKeyframeAnimation> {
    override val componentClass: KClass<KeyframeAnimation> = KeyframeAnimation::class
    override val schemaClass: KClass<SceneKeyframeAnimation> = SceneKeyframeAnimation::class
    override val serializer = SceneKeyframeAnimation.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneKeyframeAnimation,
        context: SceneResolutionContext,
    ) {
        world.add(entity, KeyframeAnimation(component))
    }

    override fun export(world: World, entity: Entity, component: KeyframeAnimation): SceneKeyframeAnimation =
        component.tracks
}

/**
 * Advances each [KeyframeAnimation] and writes its tracks to the entity's [Transform] and
 * [PbrMaterial]. Runs before the transform system, which picks up the moved transform.
 */
class KeyframeAnimationSystem : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(Transform::class, KeyframeAnimation::class) { entity, transform, animation ->
            val tracks = animation.tracks
            if (tracks.duration <= 0f) return@queryEach
            val time = (animation.time + delta).mod(tracks.duration)
            animation.time = time
            tracks.position.sampleInto(time, transform.position)
            tracks.rotation.sampleInto(time, transform.rotation)
            tracks.scale.sampleInto(time, transform.scale)
            if (tracks.alpha.isEmpty()) return@queryEach
            val material = world.get<PbrMaterial>(entity) ?: return@queryEach
            val alpha = tracks.alpha.sample(time)
            if (material.baseColorFactor.a != alpha) material.baseColorFactor = material.baseColorFactor.copy(a = alpha)
        }
    }
}

private fun List<SceneVec3Key>.sampleInto(time: Float, target: Vec3f) {
    if (isEmpty()) return
    val next = keyAfter(size, time) { this[it].time }
    val from = this[(next - 1).coerceAtLeast(0)]
    val to = this[next.coerceAtMost(size - 1)]
    val fraction = fraction(from.time, to.time, time)
    target.set(
        lerp(from.value.x, to.value.x, fraction),
        lerp(from.value.y, to.value.y, fraction),
        lerp(from.value.z, to.value.z, fraction),
    )
}

private fun List<SceneFloatKey>.sample(time: Float): Float {
    val next = keyAfter(size, time) { this[it].time }
    val from = this[(next - 1).coerceAtLeast(0)]
    val to = this[next.coerceAtMost(size - 1)]
    return lerp(from.value, to.value, fraction(from.time, to.time, time))
}

/** The index of the first key after [time], or [size] when there is none. */
private inline fun keyAfter(size: Int, time: Float, timeAt: (Int) -> Float): Int {
    var index = 0
    while (index < size && timeAt(index) <= time) index++
    return index
}

private fun fraction(from: Float, to: Float, time: Float): Float =
    if (to > from) ((time - from) / (to - from)).coerceIn(0f, 1f) else 0f
