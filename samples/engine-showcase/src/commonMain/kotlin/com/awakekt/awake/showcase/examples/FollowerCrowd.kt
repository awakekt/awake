/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasFollowerPool
import com.awakekt.awake.scene.canvas.SceneCanvasElement
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasCurve
import com.awakekt.awake.ui.canvas.CanvasCurvePoint
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasFollowRules
import kotlin.math.cos
import kotlin.math.sin

/** Where one walker goes round: its circle's centre and radius, how fast, and where it starts. */
internal class Walker(val centreX: Float, val centreZ: Float, val radius: Float, val speed: Float, val phase: Float)

/**
 * The walking cubes of the many-followers showcase and their followers: a name plate over each, from
 * one [CanvasFollowerPool], and a quest marker held to the screen's edge over every
 * [QUEST_EVERY]th, from another. Spawning again reuses the pools' entities.
 */
internal class FollowerCrowd(private val world: World, private val renderer: MeshRenderer) {
    private val platePool = CanvasFollowerPool(world, plateTemplate(PlateRules()))
    private val questPool = CanvasFollowerPool(world, questTemplate())
    private val walkers = ArrayList<Entity>()
    private val plates = ArrayList<Entity>()
    private val markers = ArrayList<Entity>()

    // Each plate's box as last seen: a plate laid out again has a new one.
    private var seenBounds = arrayOfNulls<Rectangle>(0)

    /** How many walkers there are. */
    val size: Int get() = walkers.size

    /** Puts [count] walkers in the field, each with its plate, after taking away any there were. */
    fun spawn(count: Int, rules: PlateRules) {
        clear()
        walkers.ensureCapacity(count)
        for (index in 0 until count) {
            val name = "walker-$index"
            val walker = walkerAt(index)
            val entity = world.create()
            world.add(entity, Name(name))
            world.add(entity, Transform(position = Vec3f(walker.centreX, WALKER_Y, walker.centreZ), scale = Vec3f(WALKER_SCALE, WALKER_SCALE, WALKER_SCALE)))
            world.add(entity, renderer)
            renderer.mesh.localBounds?.let { world.add(entity, MeshBounds(it)) }
            world.add(entity, walker)
            walkers += entity
            plates += platePool.acquire(name) { text = plateText(index, health(index)) }
            if (index % QUEST_EVERY == 0) markers += questPool.acquire(name) { text = "!" }
        }
        apply(rules)
    }

    /** Sets every plate's follow rules to [rules]. */
    fun apply(rules: PlateRules) {
        val followRules = rules.followRules()
        plates.forEach { world.get(it, CanvasElement::class)?.followRules = followRules }
    }

    /** Takes the walkers away and gives their followers back to the pools. */
    fun clear() {
        walkers.forEach(world::destroy)
        walkers.clear()
        plates.forEach(platePool::release)
        plates.clear()
        markers.forEach(questPool::release)
        markers.clear()
    }

    /** Takes everything away, the pools' hidden followers too. */
    fun dispose() {
        clear()
        platePool.clear()
        questPool.clear()
    }

    /** Moves each walker round its circle to where it is [time] seconds in. */
    fun walk(time: Float) {
        world.family<Transform, Walker>().forEach { _, transform, walker ->
            val angle = walker.phase + time * walker.speed / walker.radius
            transform.position.x = walker.centreX + cos(angle) * walker.radius
            transform.position.z = walker.centreZ + sin(angle) * walker.radius
        }
    }

    /** Changes one plate's number in [TURN_EVERY] on [frame], so the text is laid out again as a game's would be. */
    fun turnNumbers(frame: Int) {
        for (index in frame % TURN_EVERY until plates.size step TURN_EVERY) {
            world.get(plates[index], CanvasElement::class)?.text = plateText(index, health(index + frame))
        }
    }

    /** How many plates were laid out since the last call, by their boxes: a plate laid out again has a new one. */
    fun laidOut(): Int {
        if (seenBounds.size < plates.size) seenBounds = arrayOfNulls(plates.size)
        var count = 0
        for (index in plates.indices) {
            val bounds = world.get(plates[index], CanvasElement::class)?.screenBounds
            if (bounds != null && bounds !== seenBounds[index]) count++
            seenBounds[index] = bounds
        }
        return count
    }

    private companion object {
        const val QUEST_EVERY = 25
        const val TURN_EVERY = 30
        const val WALKER_Y = 0.5f
        const val WALKER_SCALE = 0.8f
        const val FIELD = 110f
        const val SALT_X = 17
        const val SALT_Z = 7919
        const val HASH_PRIME = 2_654_435_761L.toInt()
        const val HASH_MASK = 0xFFFF
        const val MIN_RADIUS = 2f
        const val RADIUS_STEP = 1.5f
        const val RADIUS_KINDS = 5
        const val MIN_SPEED = 1.2f
        const val SPEED_STEP = 0.5f
        const val SPEED_KINDS = 4
        const val PHASE_STEP = 0.37f
        const val HEALTH_MIN = 40
        const val HEALTH_RANGE = 60
        const val HEALTH_PRIME = 31

        /** Walker [index]'s circle, spread across the field repeatably. */
        fun walkerAt(index: Int) = Walker(
            centreX = spread(index, SALT_X),
            centreZ = spread(index, SALT_Z),
            radius = MIN_RADIUS + (index % RADIUS_KINDS) * RADIUS_STEP,
            speed = (if (index % 2 == 0) 1f else -1f) * (MIN_SPEED + (index % SPEED_KINDS) * SPEED_STEP),
            phase = index * PHASE_STEP,
        )

        fun spread(index: Int, salt: Int): Float {
            val hash = ((index + salt) * HASH_PRIME) and HASH_MASK
            return (hash.toFloat() / HASH_MASK - 0.5f) * FIELD
        }

        fun plateText(index: Int, health: Int) = "Walker $index · $health"

        fun health(seed: Int): Int = HEALTH_MIN + (seed * HEALTH_PRIME) % HEALTH_RANGE
    }
}

/** How the name plates follow: whether they hide behind walls and whether they shrink with distance. */
internal data class PlateRules(val occlusion: Boolean = true, val scale: Boolean = true) {
    /** These as the plates' follow rules: culled past [MAX_DISTANCE], faded out towards it, in the `plates` group. */
    fun followRules() = CanvasFollowRules(
        maxDistance = MAX_DISTANCE,
        occlusion = occlusion,
        group = "plates",
        scale = if (scale) CanvasCurve(listOf(CanvasCurvePoint(NEAR, 1f), CanvasCurvePoint(FAR, FAR_SCALE))) else null,
        fade = CanvasCurve(listOf(CanvasCurvePoint(FADE_FROM, 1f), CanvasCurvePoint(MAX_DISTANCE, 0f))),
    )

    private companion object {
        const val MAX_DISTANCE = 90f
        const val NEAR = 10f
        const val FAR = 60f
        const val FAR_SCALE = 0.6f
        const val FADE_FROM = 70f
    }
}

private const val PLATE_WIDTH = 110f
private const val PLATE_HEIGHT = 18f
private const val PLATE_FONT = 11f
private const val PLATE_LIFT = 1.1f
private const val MARKER_SIZE = 22f
private const val MARKER_FONT = 14f
private const val MARKER_LIFT = 2.2f
private const val MARKER_MARGIN = 28f

/** A name plate a little above its walker, its anchor at its bottom centre. */
private fun plateTemplate(rules: PlateRules) = SceneCanvasElement(
    anchor = CanvasAnchor.BottomCenter,
    offsetX = 0f,
    offsetY = 0f,
    width = PLATE_WIDTH,
    height = PLATE_HEIGHT,
    fontSize = PLATE_FONT,
    background = "#000000A0",
    followOffset = SceneVec3(0f, PLATE_LIFT, 0f),
    followRules = rules.followRules(),
)

/** A quest marker above the plate, held to the screen's edge while its walker is off screen, the few nearest at once. */
private fun questTemplate() = SceneCanvasElement(
    anchor = CanvasAnchor.BottomCenter,
    offsetX = 0f,
    offsetY = 0f,
    width = MARKER_SIZE,
    height = MARKER_SIZE,
    fontSize = MARKER_FONT,
    color = "#1A1A1A",
    background = "#FFC21AFF",
    followOffset = SceneVec3(0f, MARKER_LIFT, 0f),
    followRules = CanvasFollowRules(group = "quest", priority = 1f, pinToEdge = true, edgeMargin = MARKER_MARGIN),
)
