/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/**
 * Whether the straight line between two points is clear: the sense of sight's question, and the
 * question that muffles a sound. A host answers it from what it has: Core's physics raycast, a
 * navigation grid (`NavFieldLineOfSight` in `awake:ai:behavior`), or its own geometry.
 */
fun interface LineOfSight {
    /**
     * Whether nothing blocks the line from [from] to [to] in [world]. [viewer] and [target] are the
     * entities at each end, when known, so a raycast can ignore their own bodies.
     */
    fun isClear(world: World, from: Vec3f, to: Vec3f, viewer: Entity, target: Entity?): Boolean

    /** The answer when nothing ever blocks. */
    companion object {
        /** Nothing blocks. */
        val Clear: LineOfSight = LineOfSight { _, _, _, _, _ -> true }
    }
}

/**
 * Where entities are and which way they face, for [PerceptionSystem]. A host supplies the one it
 * has, such as one that reads a scene's transforms.
 */
interface PerceptionSpace {
    /** Writes [entity]'s position into [into] and returns true, or returns false when it has none. */
    fun position(world: World, entity: Entity, into: Vec3f): Boolean

    /**
     * Writes the direction [entity] faces into [into] and returns true, or returns false when it has
     * none, in which case it sees with its peripheral cone in every direction. Need not be normalised.
     */
    fun forward(world: World, entity: Entity, into: Vec3f): Boolean
}

/**
 * An agent that perceives. Add it beside a behaviour tree or state machine and
 * [PerceptionSystem] keeps its [memory] and writes what it perceives to their blackboards.
 *
 * @property faction The agent's faction, which decides how it regards what it perceives.
 * @property settings How it perceives.
 * @property memory What it has perceived and not forgotten.
 */
class Perceiver(
    val faction: FactionId,
    val settings: PerceptionSettings = PerceptionSettings(),
    val memory: PerceptionMemory = PerceptionMemory(),
) {
    internal var nextUpdate: Double = Double.NaN
    internal val targetPosition = Vec3f(0f, 0f, 0f)
    internal val noisePosition = Vec3f(0f, 0f, 0f)
}

/**
 * Something perceivers can see and hear: an entity with a faction.
 *
 * @property faction Its faction.
 * @property visible Whether it can be seen now; false for something hidden, such as a crouching
 *   player in tall grass. It can still be heard.
 * @property height How far above its position a viewer aims at it.
 */
class Perceivable(
    val faction: FactionId,
    var visible: Boolean = true,
    val height: Float = 1f,
)

/**
 * A sound for perceivers to hear, given to [PerceptionSystem.hear].
 *
 * @property position Where it is made.
 * @property loudness How far it carries, relative to a hearer's range: 1 reaches the full range.
 * @property source The entity that made it, if any; a hearer remembers it as sensed there.
 */
data class SoundEvent(
    val position: Vec3f,
    val loudness: Float = 1f,
    val source: Entity? = null,
) {
    init {
        require(loudness.isFinite() && loudness >= 0f) { "A sound's loudness is 0 or more, not $loudness." }
    }
}
