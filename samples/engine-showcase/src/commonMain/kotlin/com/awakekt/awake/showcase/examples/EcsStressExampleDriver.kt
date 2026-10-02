/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

/** Where one stress entity rests and how far into the wave it starts. Plain data; [SwarmMotionSystem] moves it. */
internal class SwarmMotion(
    val restY: Float,
    val phase: Float,
    val spin: Float,
)

/**
 * Moves every [SwarmMotion] entity: a travelling wave in height and a spin, written straight into
 * its [Transform]. One pass over a maintained family, no allocation, no structural change.
 */
internal class SwarmMotionSystem : System {
    private var time = 0f

    override fun update(world: World, delta: Float) {
        if (!EcsStressExampleDriver.moving) return
        time += delta
        world.family<Transform, SwarmMotion>().forEach { _, transform, motion ->
            transform.position.y = motion.restY + sin(time * WAVE_SPEED + motion.phase) * WAVE_HEIGHT
            transform.rotation.y = time * motion.spin
        }
    }

    private companion object {
        const val WAVE_SPEED = 2f
        const val WAVE_HEIGHT = 0.6f
    }
}

/**
 * Spawns a field of cube entities, each a real ECS entity with its own [Transform],
 * [MeshRenderer], [MeshBounds] and [SwarmMotion], and destroys exactly those again.
 *
 * The entities share five cube meshes, one per colour, and one material, so the renderer folds
 * them into instanced draws of up to 4,096 per mesh: the draw count grows with entities / 4,096,
 * not with entities. What stays per entity is the ECS work this demonstration exists to measure --
 * moving, culling and extracting each one every frame.
 */
internal object EcsStressExampleDriver {
    /** The counts the panel offers. */
    val counts = listOf(1_000, 10_000, 25_000, 50_000, 100_000)

    const val DEFAULT_COUNT = 10_000

    /** How many entities the next activation spawns; set from the launch options or the panel. */
    var count: Int = DEFAULT_COUNT
        private set

    /** Whether [SwarmMotionSystem] moves them. Off, the same entities measure a static scene. */
    var moving: Boolean = true

    private val spawned = ArrayList<Entity>()
    private var requestedCount: Int? = null

    /** Entities this driver currently owns. */
    val spawnedCount: Int get() = spawned.size

    /** Asks for [newCount] entities from the next frame on; respawning mid-composition would tear down what the UI reads. */
    fun request(newCount: Int) {
        require(newCount > 0) { "An entity count must be positive." }
        if (newCount != count) requestedCount = newCount
    }

    /** Sets the count before the showcase first activates, from a launch option. */
    fun preset(newCount: Int) {
        require(newCount > 0) { "An entity count must be positive." }
        count = newCount
    }

    fun attach(runtime: SceneAppLifecycleRuntime) {
        // The game/render split is what this showcase is read by; F2 still turns it off.
        runtime.perfStatsEnabled = true
        spawn(runtime.world, renderers(runtime))
    }

    fun detach(world: World) {
        spawned.forEach(world::destroy)
        spawned.clear()
    }

    /** Applies a count the panel asked for. */
    fun advance(runtime: SceneAppLifecycleRuntime) {
        val next = requestedCount ?: return
        requestedCount = null
        count = next
        detach(runtime.world)
        spawn(runtime.world, renderers(runtime))
    }

    /** One [MeshRenderer] per palette colour, shared by every entity of that colour. */
    private fun renderers(runtime: SceneAppLifecycleRuntime): List<MeshRenderer> {
        val material = runtime.requireMaterial("lit-shadow")
        return PALETTE.indices.map { index -> MeshRenderer(runtime.requireMesh(meshName(index)), material) }
    }

    /** The asset name of the cube in palette colour [index]. */
    fun meshName(index: Int): String = "stress-cube-$index"

    /** A unit cube in palette colour [index]: one colour per mesh, so a mesh is a batch. */
    fun cubeGeometry(index: Int): MeshGeometry {
        val geometry = generate { cube(size = 1f) }
        val color = PALETTE[index]
        val colorAt = geometry.format.floatOffsetOf(VertexSemantic.Color)
        val vertices = geometry.vertices.copyOf()
        for (at in colorAt until vertices.size step geometry.format.strideFloats) {
            vertices[at] = color.r
            vertices[at + 1] = color.g
            vertices[at + 2] = color.b
        }
        return geometry.copy(vertices = vertices)
    }

    /**
     * Lays [count] entities out in a square, coloured in blocks so neighbours share a renderer.
     *
     * Each [MeshRenderer] in [renderers] is shared by every entity of its colour: it is immutable,
     * so sharing costs nothing and saves an object per entity. [MeshBounds] is not shared; it
     * caches each entity's world bounds.
     */
    internal fun spawn(world: World, renderers: List<MeshRenderer>) {
        val side = ceil(sqrt(count.toFloat())).toInt()
        val offset = (side - 1) * SPACING / 2f
        spawned.ensureCapacity(count)
        for (index in 0 until count) {
            val column = index % side
            val row = index / side
            val renderer = renderers[(column / COLOR_BLOCK + row / COLOR_BLOCK) % renderers.size]
            val entity = world.create()
            world.add(
                entity,
                Transform(
                    position = Vec3f(column * SPACING - offset, REST_Y, row * SPACING - offset),
                    scale = Vec3f(CUBE_SCALE, CUBE_SCALE, CUBE_SCALE),
                ),
            )
            world.add(entity, renderer)
            renderer.mesh.localBounds?.let { world.add(entity, MeshBounds(it)) }
            world.add(entity, SwarmMotion(REST_Y, phase = (column + row) * PHASE_STEP, spin = SPIN_MIN + (index % SPIN_KINDS) * SPIN_STEP))
            spawned += entity
        }
    }

    /** Saturated enough to read at a distance through the lit shader's tone mapping. */
    private val PALETTE = listOf(
        Color(0.95f, 0.32f, 0.22f, 1f),
        Color(0.98f, 0.72f, 0.12f, 1f),
        Color(0.28f, 0.82f, 0.36f, 1f),
        Color(0.12f, 0.62f, 0.86f, 1f),
        Color(0.58f, 0.38f, 0.92f, 1f),
    )

    /** How many palette colours there are, so a caller can register one mesh each. */
    val paletteSize: Int get() = PALETTE.size

    private const val COLOR_BLOCK = 8
    private const val SPACING = 1.5f
    private const val CUBE_SCALE = 0.8f
    private const val REST_Y = 0.6f
    private const val PHASE_STEP = 0.15f
    private const val SPIN_MIN = 0.5f
    private const val SPIN_STEP = 0.4f
    private const val SPIN_KINDS = 5
}
