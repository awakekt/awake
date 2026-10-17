/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How something was last sensed. */
@Serializable
enum class Sense {
    /** Seen inside the focus cone. */
    @SerialName("focus")
    Focus,

    /** Seen inside the peripheral cone only. */
    @SerialName("peripheral")
    Peripheral,

    /** Heard. */
    @SerialName("hearing")
    Hearing,
}

/**
 * What an agent remembers of one entity.
 *
 * @property entity The entity.
 */
class PerceivedEntity internal constructor(val entity: Entity) {
    /** Where it was last sensed. Updated in place. */
    val lastKnownPosition: Vec3f = Vec3f(0f, 0f, 0f)

    /** The perception clock, in seconds, when it was last sensed. */
    var lastSensed: Double = 0.0
        internal set

    /** How the agent's faction regards it. */
    var relation: Relation = Relation.Neutral
        internal set

    /** How it was last sensed. */
    var sense: Sense = Sense.Hearing
        internal set

    /** Whether it was in sight at the latest sight update. */
    var inSight: Boolean = false
        internal set
}

/**
 * The latest noise an agent heard.
 *
 * @property position Where it came from.
 * @property heardAt The perception clock, in seconds, when it was heard.
 * @property loudness How loud it was.
 * @property source The entity that made it, if known.
 */
data class HeardNoise(
    val position: Vec3f,
    val heardAt: Double,
    val loudness: Float,
    val source: Entity?,
)

/**
 * What one agent has perceived and not yet forgotten: each entity's last known position and time,
 * and the latest noise. [PerceptionSystem] keeps it; behaviour conditions and game code read it.
 */
class PerceptionMemory {
    private val known = LinkedHashMap<Entity, PerceivedEntity>()

    /** The perception clock, in seconds, at this agent's latest update. */
    var now: Double = 0.0
        internal set

    /** The latest noise heard and not yet forgotten. */
    var noise: HeardNoise? = null
        internal set

    /** Everything remembered, in the order it was first sensed. */
    val entities: Collection<PerceivedEntity> get() = known.values

    /** What is remembered of [entity], or `null`. */
    operator fun get(entity: Entity): PerceivedEntity? = known[entity]

    /** Whether anything with [relation] (any relation when null) is in sight now. */
    fun seesAny(relation: Relation? = null): Boolean =
        known.values.any { it.inSight && (relation == null || it.relation == relation) }

    /** How many things with [relation] are in sight now. */
    fun countInSight(relation: Relation? = null): Int =
        known.values.count { it.inSight && (relation == null || it.relation == relation) }

    /** Whether anything with [relation] is remembered, in sight or not. */
    fun remembersAny(relation: Relation? = null): Boolean =
        known.values.any { relation == null || it.relation == relation }

    /** Forgets [entity]. */
    fun forget(entity: Entity) {
        known.remove(entity)
    }

    /** Forgets everything. */
    fun clear() {
        known.clear()
        noise = null
    }

    internal fun remember(entity: Entity): PerceivedEntity = known.getOrPut(entity) { PerceivedEntity(entity) }

    internal fun forgetOlderThan(cutoff: Double) {
        known.values.removeAll { it.lastSensed < cutoff }
        if ((noise?.heardAt ?: cutoff) < cutoff) noise = null
    }

    /** What is remembered, as data. */
    fun snapshot(): PerceptionMemorySnapshot =
        PerceptionMemorySnapshot(
            now = now,
            entities = known.values.map {
                RememberedEntity(
                    it.entity.packed,
                    it.lastKnownPosition.x,
                    it.lastKnownPosition.y,
                    it.lastKnownPosition.z,
                    it.lastSensed,
                    it.relation,
                    it.sense,
                    it.inSight,
                )
            },
            noise = noise?.let { RememberedNoise(it.position.x, it.position.y, it.position.z, it.heardAt, it.loudness, it.source?.packed) },
        )

    /** Replaces what is remembered with [snapshot]. */
    fun restore(snapshot: PerceptionMemorySnapshot) {
        known.clear()
        now = snapshot.now
        for (saved in snapshot.entities) {
            remember(Entity(saved.entity)).apply {
                lastKnownPosition.set(saved.x, saved.y, saved.z)
                lastSensed = saved.lastSensed
                relation = saved.relation
                sense = saved.sense
                inSight = saved.inSight
            }
        }
        noise = snapshot.noise?.let { HeardNoise(Vec3f(it.x, it.y, it.z), it.heardAt, it.loudness, it.source?.let(::Entity)) }
    }
}

/**
 * A [PerceptionMemory] as data.
 *
 * @property now The perception clock at the latest update.
 * @property entities Each remembered entity.
 * @property noise The latest noise.
 */
@Serializable
data class PerceptionMemorySnapshot(
    val now: Double = 0.0,
    val entities: List<RememberedEntity> = emptyList(),
    val noise: RememberedNoise? = null,
)

/**
 * One remembered entity in a [PerceptionMemorySnapshot].
 *
 * @property entity The entity's packed handle.
 * @property x Last known x.
 * @property y Last known y.
 * @property z Last known z.
 * @property lastSensed When it was last sensed.
 * @property relation How the agent regards it.
 * @property sense How it was last sensed.
 * @property inSight Whether it was in sight.
 */
@Serializable
data class RememberedEntity(
    val entity: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val lastSensed: Double,
    val relation: Relation,
    val sense: Sense,
    val inSight: Boolean,
)

/**
 * The remembered noise in a [PerceptionMemorySnapshot].
 *
 * @property x Where, x.
 * @property y Where, y.
 * @property z Where, z.
 * @property heardAt When.
 * @property loudness How loud.
 * @property source The packed handle of what made it, if known.
 */
@Serializable
data class RememberedNoise(
    val x: Float,
    val y: Float,
    val z: Float,
    val heardAt: Double,
    val loudness: Float,
    val source: Long? = null,
)
