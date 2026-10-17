/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.ai.btree.BehaviorTreeComponent
import com.awakekt.awake.ai.btree.Blackboard
import com.awakekt.awake.ai.fsm.AiStateMachineComponent
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import kotlin.math.PI
import kotlin.math.cos
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Updates every [Perceiver]'s memory from what it sees and hears, and writes the result to the
 * blackboard of its behaviour tree or state machine (see [PerceptionKeys]). Run it before the
 * behaviour systems.
 *
 * Sight is budgeted: each perceiver looks every [PerceptionSettings.updateInterval] seconds, staggered
 * so they do not all look on one frame, at most [maxSightUpdates] look per update, and, with a
 * [sightBudget], no more look once that much time has passed in the update (one always does).
 * Perceivers left over by the budget look first next time, so none is starved. Hearing is not
 * budgeted: every perceiver hears each sound given to [hear] on the next update.
 *
 * A look reads only the perceivable entities near enough to see: where they are is bucketed once per
 * update into cells [indexCellSize] metres square.
 *
 * Everything is deterministic: the same world, the same updates and the same sounds give the same
 * memories. A [sightBudget] is the exception, since how many look then depends on the machine.
 *
 * @param space Where entities are and which way they face.
 * @param factions Who regards whom how.
 * @param lineOfSight What blocks sight, and muffles sound.
 * @param maxSightUpdates The most perceivers that look in one update.
 * @param sightBudget The most time looks may take in one update, or `null` for no limit.
 * @param timeSource What measures [sightBudget].
 * @param indexCellSize The size of the cells perceivable entities are bucketed into; 0 checks every
 *   one on every look. About the usual sight range suits.
 */
class PerceptionSystem(
    private val space: PerceptionSpace,
    private val factions: FactionTable = FactionTable(),
    private val lineOfSight: LineOfSight = LineOfSight.Clear,
    private val maxSightUpdates: Int = Int.MAX_VALUE,
    private val sightBudget: Duration? = null,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    indexCellSize: Float = DEFAULT_CELL_SIZE,
) : System {
    private val index = SightIndex(indexCellSize)
    private val candidates = IntBuffer()
    private var budgetStart: TimeMark? = null
    private val sounds = mutableListOf<SoundEvent>()
    private var cursor = 0
    private val eye = Vec3f(0f, 0f, 0f)
    private val facing = Vec3f(0f, 0f, 0f)
    private val seen = Vec3f(0f, 0f, 0f)
    private val toward = Vec3f(0f, 0f, 0f)

    init {
        require(maxSightUpdates > 0) { "maxSightUpdates is $maxSightUpdates; at least one perceiver must look." }
        require(sightBudget == null || sightBudget.isPositive()) { "sightBudget is $sightBudget; it must be more than zero." }
        require(indexCellSize.isFinite() && indexCellSize >= 0f) { "indexCellSize is $indexCellSize; it must be 0 or more." }
    }

    /** The perception clock: seconds of updates so far. */
    var time: Double = 0.0
        private set

    /** How many perceivers looked in the latest update. */
    var sightUpdates: Int = 0
        private set

    /** Queues [sound] for every perceiver to hear on the next update. */
    fun hear(sound: SoundEvent) {
        sounds += sound
    }

    override fun update(world: World, delta: Float) {
        time += delta
        sightUpdates = 0
        index.invalidate()
        budgetStart = sightBudget?.let { timeSource.markNow() }
        val perceivers = world.family<Perceiver>()
        val count = perceivers.size
        if (count == 0) {
            sounds.clear()
            return
        }
        val start = cursor % count
        var index = 0
        var last = start - 1
        // From the cursor to the end, then from the start to the cursor: left-over perceivers go first.
        perceivers.forEach { entity, perceiver ->
            if (index >= start && updateOne(world, entity, perceiver)) last = index
            index++
        }
        index = 0
        perceivers.forEach { entity, perceiver ->
            if (index < start && updateOne(world, entity, perceiver)) last = index
            index++
        }
        cursor = last + 1
        sounds.clear()
    }

    /** Hears, looks when due and within budget, forgets, and writes; true when it looked. */
    private fun updateOne(world: World, entity: Entity, perceiver: Perceiver): Boolean {
        val memory = perceiver.memory
        memory.now = time
        if (perceiver.nextUpdate.isNaN()) {
            // Stagger first looks over one interval by entity id, so a crowd does not look together.
            perceiver.nextUpdate = time + perceiver.settings.updateInterval * (entity.id % STAGGER_STEPS) / STAGGER_STEPS
        }
        for (sound in sounds) listen(world, entity, perceiver, sound)
        val looks = time + CLOCK_SLACK >= perceiver.nextUpdate && sightUpdates < maxSightUpdates && withinTime()
        if (looks) {
            sightUpdates++
            perceiver.nextUpdate = time + perceiver.settings.updateInterval
            look(world, entity, perceiver)
        }
        memory.forgetOlderThan(time - perceiver.settings.forgetAfter)
        // The target choice measures from where the perceiver stands now.
        if (!space.position(world, entity, eye)) eye.set(0f, 0f, 0f)
        world.get<BehaviorTreeComponent>(entity)?.let { write(perceiver, it.blackboard) }
        world.get<AiStateMachineComponent>(entity)?.let { write(perceiver, it.blackboard) }
        return looks
    }

    /** Whether the time budget allows another look; the first look of an update always may. */
    private fun withinTime(): Boolean {
        val start = budgetStart ?: return true
        return sightUpdates == 0 || start.elapsedNow() < sightBudget!!
    }

    private fun look(world: World, viewer: Entity, perceiver: Perceiver) {
        val sight = perceiver.settings.sight
        for (remembered in perceiver.memory.entities) remembered.inSight = false
        if (!space.position(world, viewer, eye)) return
        eye.y += sight.eyeHeight
        val faces = space.forward(world, viewer, facing) && facing.length3() > 0f
        if (faces) facing.normalize()
        val cone = Cone(sight, faces)
        if (!index.built) index.rebuild(world, space, seen)
        index.near(eye.x, eye.z, cone.reach, candidates)
        for (c in 0 until candidates.size) {
            val i = candidates[c]
            val target = index.entity(i)
            val perceivable = index.perceivable(i)
            if (target == viewer || !perceivable.visible) continue
            val sense = senseOf(cone, perceivable, index.positionOf(i, seen))
            if (sense != null && (!sight.lineOfSight || lineOfSight.isClear(world, eye, seen, viewer, target))) {
                perceiver.memory.remember(target).apply {
                    index.positionOf(i, lastKnownPosition)
                    lastSensed = time
                    relation = factions.relation(perceiver.faction, perceivable.faction)
                    this.sense = sense
                    inSight = true
                }
            }
        }
    }

    /** One look's cones, with their half-angle cosines worked out once. */
    private class Cone(val sight: SightSettings, val faces: Boolean) {
        val focusCos = halfAngleCos(sight.focusAngle)
        val peripheralCos = halfAngleCos(sight.peripheralAngle)
        val reach = maxOf(sight.focusRange, sight.peripheralRange)
    }

    /** Which cone [seen] (a target at [perceivable]'s height) is in from [eye], or `null`. */
    private fun senseOf(cone: Cone, perceivable: Perceivable, seen: Vec3f): Sense? {
        seen.y += perceivable.height
        toward.set(seen.x - eye.x, seen.y - eye.y, seen.z - eye.z)
        val distance = toward.length3()
        val alignment = if (!cone.faces || distance == 0f) 1f else toward.dot(facing) / distance
        val sight = cone.sight
        return when {
            distance > cone.reach -> null
            cone.faces && distance <= sight.focusRange && alignment >= cone.focusCos -> Sense.Focus
            distance <= sight.peripheralRange && (!cone.faces || alignment >= cone.peripheralCos) -> Sense.Peripheral
            else -> null
        }
    }

    private fun listen(world: World, hearer: Entity, perceiver: Perceiver, sound: SoundEvent) {
        if (!hears(world, hearer, perceiver, sound)) return
        val memory = perceiver.memory
        val previous = memory.noise
        if (previous == null || previous.heardAt < time || sound.loudness >= previous.loudness) {
            memory.noise = HeardNoise(Vec3f(sound.position.x, sound.position.y, sound.position.z), time, sound.loudness, sound.source)
        }
        val source = sound.source
        val perceivable = source?.let { world.get<Perceivable>(it) }
        if (source != null && perceivable != null) {
            memory.remember(source).apply {
                lastKnownPosition.set(sound.position)
                lastSensed = time
                relation = factions.relation(perceiver.faction, perceivable.faction)
                if (!inSight) this.sense = Sense.Hearing
            }
        }
    }

    /** Whether [sound] reaches [hearer]: within range, and within the muffled range or unblocked. */
    private fun hears(world: World, hearer: Entity, perceiver: Perceiver, sound: SoundEvent): Boolean {
        if (sound.source == hearer || !space.position(world, hearer, eye)) return false
        val hearing = perceiver.settings.hearing
        eye.y += perceiver.settings.sight.eyeHeight
        val distance = eye.distanceTo(sound.position)
        val reach = hearing.range * sound.loudness
        return distance <= reach &&
            (distance <= reach * hearing.muffledBy || lineOfSight.isClear(world, eye, sound.position, hearer, sound.source))
    }

    private fun write(perceiver: Perceiver, board: Blackboard) {
        val keys = perceiver.settings.keys
        val memory = perceiver.memory
        val target = chooseTarget(memory)
        keys.target?.let { if (target != null) board[it] = target.entity else board.remove(it) }
        keys.targetPosition?.let {
            if (target != null) board[it] = perceiver.targetPosition.set(target.lastKnownPosition) else board.remove(it)
        }
        keys.seesHostile?.let { board[it] = memory.seesAny(Relation.Hostile) }
        keys.hostilesInSight?.let { board[it] = memory.countInSight(Relation.Hostile) }
        keys.noisePosition?.let {
            val noise = memory.noise
            if (noise != null) board[it] = perceiver.noisePosition.set(noise.position) else board.remove(it)
        }
    }

    /** The nearest hostile in sight, or else the hostile sensed most recently (the earliest on a tie). */
    private fun chooseTarget(memory: PerceptionMemory): PerceivedEntity? {
        var best: PerceivedEntity? = null
        var bestDistance = Float.MAX_VALUE
        for (entry in memory.entities) {
            if (entry.relation != Relation.Hostile) continue
            val distance = entry.lastKnownPosition.distanceTo(eye)
            val better = when {
                best == null -> true
                entry.inSight != best.inSight -> entry.inSight
                entry.inSight -> distance < bestDistance
                else -> entry.lastSensed > best.lastSensed
            }
            if (better) {
                best = entry
                bestDistance = distance
            }
        }
        return best
    }

    private companion object {
        const val STAGGER_STEPS = 16
        const val DEFAULT_CELL_SIZE = 16f

        // Float deltas summed into the clock land a hair short of a whole interval.
        const val CLOCK_SLACK = 1e-6
        const val HALF_TURN = 180f

        fun halfAngleCos(degrees: Float): Float = cos(degrees / 2f * PI.toFloat() / HALF_TURN)
    }
}
