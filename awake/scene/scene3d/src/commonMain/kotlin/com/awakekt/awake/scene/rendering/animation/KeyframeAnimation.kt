/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.lerp
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.componentType
import com.awakekt.awake.ecs.family
import com.awakekt.awake.ecs.get
import com.awakekt.awake.scene.binding.RegisteredBinding
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
    @PropertyRange(min = 0.0, exclusiveMin = true) val duration: Float,
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

/**
 * Live keyframe animation component tracking playback time and evaluating property tracks.
 *
 * @property tracks The keyframe animation curve and track definitions.
 */
class KeyframeAnimation(val tracks: SceneKeyframeAnimation) {
    /** Seconds into the loop. */
    var time: Float = 0f
        internal set

    // Each track's key after [time] last frame: the next frame starts its search there instead of
    // at key 0, so a frame costs a key or two per track however long the track is (#1428).
    internal var positionKey = 0
    internal var rotationKey = 0
    internal var scaleKey = 0
    internal var alphaKey = 0
}

/**
 * Binding definition connecting [SceneKeyframeAnimation] descriptors to ECS [KeyframeAnimation] components.
 */
@RegisteredBinding
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
 *
 * With [hasRenderer] false, for a host that draws nothing, the position, rotation and scale tracks
 * still write the [Transform] every frame, since a headless host reads it (a collider, a trigger, a
 * socket or the network follows the moved node); only the [alpha][SceneKeyframeAnimation.alpha]
 * track, which nothing but a renderer reads, is skipped.
 *
 * @param hasRenderer Whether anything draws the entities; false skips the alpha track.
 */
class KeyframeAnimationSystem(private val hasRenderer: Boolean) : System {
    /** A system for a host that draws, writing every track. */
    constructor() : this(hasRenderer = true)

    override fun update(world: World, delta: Float) {
        world.family(TRANSFORM, KEYFRAMES).forEach { entity, transform, animation ->
            val tracks = animation.tracks
            if (tracks.duration <= 0f) return@forEach
            val time = (animation.time + delta).mod(tracks.duration)
            animation.time = time
            animation.positionKey = tracks.position.sampleInto(time, transform.position, animation.positionKey)
            animation.rotationKey = tracks.rotation.sampleInto(time, transform.rotation, animation.rotationKey)
            animation.scaleKey = tracks.scale.sampleInto(time, transform.scale, animation.scaleKey)
            if (!hasRenderer || tracks.alpha.isEmpty()) return@forEach
            val material = world.get(entity, MATERIAL) ?: return@forEach
            val next = keyAfter(tracks.alpha.size, time, animation.alphaKey) { tracks.alpha[it].time }
            animation.alphaKey = next
            val alpha = tracks.alpha.sample(time, next)
            if (material.baseColorFactor.a != alpha) material.baseColorFactor = material.baseColorFactor.copy(a = alpha)
        }
    }

    // An inline family walk with handles resolved once: queryEach's capturing lambda was a frame's only allocation.
    private companion object {
        val TRANSFORM = componentType<Transform>()
        val KEYFRAMES = componentType<KeyframeAnimation>()
        val MATERIAL = componentType<PbrMaterial>()
    }
}

/** Writes this track at [time] into [target], searching from key [hint]; returns the key after [time], the next frame's hint. */
private fun List<SceneVec3Key>.sampleInto(time: Float, target: Vec3f, hint: Int): Int {
    if (isEmpty()) return 0
    val next = keyAfter(size, time, hint) { this[it].time }
    val from = this[(next - 1).coerceAtLeast(0)]
    val to = this[next.coerceAtMost(size - 1)]
    val fraction = fraction(from.time, to.time, time)
    target.set(
        lerp(from.value.x, to.value.x, fraction),
        lerp(from.value.y, to.value.y, fraction),
        lerp(from.value.z, to.value.z, fraction),
    )
    return next
}

/** This track at [time], whose key after [time] is [next]. */
private fun List<SceneFloatKey>.sample(time: Float, next: Int): Float {
    val from = this[(next - 1).coerceAtLeast(0)]
    val to = this[next.coerceAtMost(size - 1)]
    return lerp(from.value, to.value, fraction(from.time, to.time, time))
}

/**
 * The index of the first key after [time], or [size] when there is none, for keys in time order as
 * [SceneKeyframeAnimation.validate] requires. The search starts at [hint], last frame's answer, while
 * [time] has not gone back before the key below it; a loop that starts over searches from key 0.
 */
internal inline fun keyAfter(size: Int, time: Float, hint: Int, timeAt: (Int) -> Float): Int {
    var index = if (hint in 1..size && timeAt(hint - 1) <= time) hint else 0
    while (index < size && timeAt(index) <= time) index++
    return index
}

private fun fraction(from: Float, to: Float, time: Float): Float =
    if (to > from) ((time - from) / (to - from)).coerceIn(0f, 1f) else 0f
