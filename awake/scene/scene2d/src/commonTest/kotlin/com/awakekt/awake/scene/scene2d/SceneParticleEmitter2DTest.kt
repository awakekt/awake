/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.particles.ParticleBurstSchedule
import com.awakekt.awake.core.particles.ParticleDeathListener
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.BurstEmitter2DPool
import com.awakekt.awake.particles.DEFAULT_PARTICLE_SORTING_LAYER_2D
import com.awakekt.awake.particles.Particle2DSystem
import com.awakekt.awake.particles.ParticleEmitter2D
import com.awakekt.awake.particles.ParticleSpawnRate
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneVec2
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneParticleEmitter2DTest {
    init {
        SceneComponentRegistry.registerGlobal(ParticleEmitter2DBinding)
    }

    private val richAuthored = SceneParticleEmitter2D(
        texture = "fx/ember_sheet.png",
        columns = 4,
        rows = 2,
        frameCount = 6,
        frameRate = 16f,
        enabled = true,
        maxParticles = 96,
        spawnRate = 24f,
        lifetime = 0.5f,
        maxLifetime = 1.25f,
        size = 0.3f,
        endSize = 0.8f,
        startAlpha = 0.9f,
        alphaCurve = SceneParticleAlphaCurve2D(fadeInEnd = 0.15f, fadeOutStart = 0.7f),
        tint = SceneColor(1f, 0.7f, 0.2f, 1f),
        endTint = SceneColor(0.8f, 0.1f, 0.05f, 0.5f),
        shape = SceneParticleShape2D(kind = ParticleEmissionShape2D.Ring, radius = 1.5f, innerRadius = 0.5f),
        speed = 2f,
        maxSpeed = 4.5f,
        directionDegrees = 60f,
        spreadDegrees = 40f,
        radialSpeed = 0.75f,
        convergeToOrigin = false,
        gravity = SceneVec2(0.5f, -6f),
        drag = 0.4f,
        turbulence = 1.2f,
        turbulenceFrequency = 2.5f,
        spin = SceneParticleSpin2D(minDegreesPerSecond = -90f, maxDegreesPerSecond = 90f, randomStartAngle = true),
        additive = true,
        localSpace = true,
        inheritOrientation = true,
        sortingLayer = "Foreground",
        sortOrder = 7,
        burstCount = 32,
        burstCycle = SceneParticleBurstCycle2D(
            cycleSeconds = 2f,
            activeSeconds = 1f,
            burstInterval = 0.25f,
            burstSize = 8,
        ),
    )

    private fun problems(emitter: SceneParticleEmitter2D): List<String> =
        emitter.validate("nodes[0]").map { it.message }

    @Test
    fun defaultUntexturedEmitterAndFullConfiguredEmitterValidateAndRoundTrip() {
        assertEquals(emptyList(), problems(SceneParticleEmitter2D()))
        assertEquals(emptyList(), problems(richAuthored))
        assertEquals(8, richAuthored.cellCount)

        val document = SceneDocument(nodes = listOf(SceneNode("sparks", components = listOf(richAuthored))))
        val decoded = SceneLoader.decode(SceneLoader.encode(document)).nodes.single().components.single()
        assertEquals(richAuthored, decoded)

        val world = World()
        val entity = world.create()
        ParticleEmitter2DBinding.attachTyped(world, entity, richAuthored, NoopResolutionContext(world))
        val live = assertNotNull(world.get<ParticleEmitter2D>(entity))
        assertEquals(richAuthored, ParticleEmitter2DBinding.export(world, entity, live))

        // Disc, Box, and RectEdge shapes also round-trip cleanly.
        for (shape in listOf(
            SceneParticleShape2D(kind = ParticleEmissionShape2D.Point),
            SceneParticleShape2D(kind = ParticleEmissionShape2D.Disc, radius = 2f),
            SceneParticleShape2D(kind = ParticleEmissionShape2D.Box, halfWidth = 3f, halfHeight = 1.5f),
            SceneParticleShape2D(kind = ParticleEmissionShape2D.RectEdge, halfWidth = 2f, halfHeight = 1f),
        )) {
            val variant = richAuthored.copy(shape = shape)
            assertEquals(variant, ParticleEmitter2D(variant).toScene())
        }
    }

    @Test
    fun validationRejectsInvalidSheetPoolLifetimeMotionAndNestedSettings() {
        assertTrue(problems(SceneParticleEmitter2D(texture = "   ")).any { "texture must not be blank" in it })
        assertTrue(problems(SceneParticleEmitter2D(texture = "", columns = 2)).any { "require a texture" in it })
        assertTrue(problems(SceneParticleEmitter2D(texture = "", frameCount = 2)).any { "require a texture" in it })
        assertTrue(
            problems(SceneParticleEmitter2D(texture = "a.png", columns = 2, rows = 2, frameCount = 5))
                .any { "within the sheet's 4 cells" in it },
        )
        assertTrue(
            problems(SceneParticleEmitter2D(texture = "a.png", columns = 65536, rows = 65536))
                .any { "cell count" in it },
        )
        assertTrue(problems(SceneParticleEmitter2D(maxParticles = 0)).any { "maxParticles" in it })
        assertTrue(problems(SceneParticleEmitter2D(spawnRate = -1f)).any { "spawnRate" in it })
        assertTrue(problems(SceneParticleEmitter2D(sortingLayer = " ")).any { "sortingLayer" in it })
        assertTrue(problems(SceneParticleEmitter2D(lifetime = 0f)).any { "lifetime" in it })
        assertTrue(problems(SceneParticleEmitter2D(lifetime = 2f, maxLifetime = 1f)).any { "maxLifetime" in it })
        assertTrue(problems(SceneParticleEmitter2D(size = 0f)).any { "size" in it })
        assertTrue(problems(SceneParticleEmitter2D(endSize = -0.1f)).any { "endSize" in it })
        assertTrue(problems(SceneParticleEmitter2D(startAlpha = 1.2f)).any { "startAlpha" in it })
        assertTrue(problems(SceneParticleEmitter2D(burstCount = 0)).any { "burstCount" in it })
        assertTrue(problems(SceneParticleEmitter2D(speed = -1f)).any { "speed" in it })
        assertTrue(problems(SceneParticleEmitter2D(speed = 3f, maxSpeed = 2f)).any { "maxSpeed" in it })
        assertTrue(problems(SceneParticleEmitter2D(spreadDegrees = 400f)).any { "spreadDegrees" in it })
        assertTrue(problems(SceneParticleEmitter2D(drag = -0.5f)).any { "drag" in it })
        assertTrue(problems(SceneParticleEmitter2D(turbulenceFrequency = -1f)).any { "turbulenceFrequency" in it })
        assertTrue(
            problems(
                SceneParticleEmitter2D(
                    shape = SceneParticleShape2D(kind = ParticleEmissionShape2D.Ring, radius = 1f, innerRadius = 2f),
                ),
            ).any { "innerRadius must not exceed radius" in it },
        )
        assertTrue(
            problems(SceneParticleEmitter2D(alphaCurve = SceneParticleAlphaCurve2D(fadeInEnd = 0.8f, fadeOutStart = 0.3f)))
                .any { "fadeInEnd" in it },
        )
        assertTrue(
            problems(SceneParticleEmitter2D(spin = SceneParticleSpin2D(minDegreesPerSecond = 20f, maxDegreesPerSecond = -20f)))
                .any { "minDegreesPerSecond" in it },
        )
        assertTrue(
            problems(
                SceneParticleEmitter2D(
                    burstCycle = SceneParticleBurstCycle2D(cycleSeconds = 1f, activeSeconds = 2f, burstInterval = 0.2f, burstSize = 4),
                ),
            ).any { "activeSeconds" in it },
        )
        assertFailsWith<IllegalArgumentException> {
            ParticleEmitter2D(SceneParticleEmitter2D(maxParticles = 0))
        }
    }

    @Test
    fun worldSpaceParticlesTrailBehindMovingNodeWhileLocalSpaceMovesWithTransform() {
        val world = World()
        val transformSystem = TransformSystem()
        val particleSystem = Particle2DSystem()

        val worldEntity = world.create()
        val worldTransform = Transform()
        val worldEmitter = ParticleEmitter2D(maxParticles = 8, random = Random(7)).apply {
            spawnRate = 0f
            speed = 0f
            lifetime = 5f
            localSpace = false
        }
        world.add(worldEntity, worldTransform)
        world.add(worldEntity, worldEmitter)

        val localEntity = world.create()
        val localTransform = Transform()
        val localEmitter = ParticleEmitter2D(maxParticles = 8, random = Random(7)).apply {
            spawnRate = 0f
            speed = 0f
            lifetime = 5f
            localSpace = true
        }
        world.add(localEntity, localTransform)
        world.add(localEntity, localEmitter)

        transformSystem.update(world, 0f)
        particleSystem.update(world, 0f)
        worldEmitter.emit(1)
        localEmitter.emit(1)

        // Move both entities to (10, 4).
        worldTransform.position.set(10f, 4f, 0f)
        localTransform.position.set(10f, 4f, 0f)
        transformSystem.update(world, 0.1f)
        particleSystem.update(world, 0.1f)

        val draws = world.collectSpriteDraws()
        assertEquals(2, draws.size)
        val trailingDraw = draws[0]
        val attachedDraw = draws[1]
        assertEquals(0f, trailingDraw.model.m03, 1e-4f)
        assertEquals(0f, trailingDraw.model.m13, 1e-4f)
        assertEquals(10f, attachedDraw.model.m03, 1e-4f)
        assertEquals(4f, attachedDraw.model.m13, 1e-4f)
    }

    @Test
    fun inheritOrientationRotatesLaunchDirectionAndPauseFreezesSimulation() {
        val world = World()
        val transformSystem = TransformSystem()
        var playing = true
        val particleSystem = Particle2DSystem(isPlaying = { playing })

        val entity = world.create()
        val transform = Transform().apply {
            rotation.z = (PI / 2.0).toFloat() // 90 deg CCW turns +X launch into +Y
        }
        val emitter = ParticleEmitter2D(maxParticles = 8, random = Random(12)).apply {
            spawnRate = 0f
            directionDegrees = 0f // +X before entity rotation
            spreadDegrees = 0f
            speed = 4f
            lifetime = 5f
            inheritOrientation = true
        }
        world.add(entity, transform)
        world.add(entity, emitter)

        transformSystem.update(world, 0f)
        particleSystem.update(world, 0f)
        emitter.emit(1)
        particleSystem.update(world, 0.5f)

        val launched = world.collectSpriteDraws().single().model
        assertTrue(abs(launched.m03) < 1e-3f)
        assertEquals(2f, launched.m13, 1e-3f)

        playing = false
        particleSystem.update(world, 1f)
        assertEquals(2f, world.collectSpriteDraws().single().model.m13, 1e-5f, "paused Particle2DSystem must freeze live particles")
    }

    @Test
    fun oneShotBurstEntityDestroysItselfOnCompletionAndInvokesDeathCallback() {
        val world = World()
        val particleSystem = Particle2DSystem()
        val deaths = mutableListOf<Pair<Float, Float>>()

        val entity = world.create()
        val emitter = ParticleEmitter2D(maxParticles = 4, random = Random(3)).apply {
            spawnRate = 100f
            burstCount = 2
            lifetime = 0.2f
            speed = 0f
            onParticleDeath = ParticleDeathListener { x, y, _ -> deaths += x to y }
        }
        world.add(entity, Transform().apply { position.set(3f, -2f, 0f) })
        world.add(entity, emitter)

        particleSystem.update(world, 0.05f)
        assertTrue(world.isAlive(entity))
        assertEquals(2, emitter.liveParticleCount)

        particleSystem.update(world, 0.3f)
        assertFalse(world.isAlive(entity), "spent non-pooled burstCount entity must be destroyed")
        assertEquals(2, deaths.size)
        assertEquals(3f, deaths[0].first, 1e-4f)
        assertEquals(-2f, deaths[0].second, 1e-4f)
    }

    @Test
    fun zeroCapacityEmitterStepsButSpawnsNothingAndCountsTheDrops() {
        val world = World()
        val particleSystem = Particle2DSystem()
        val entity = world.create()
        val emitter = ParticleEmitter2D(maxParticles = 0, random = Random(4)).apply {
            spawnRate = 100f
            lifetime = 1f
        }
        world.add(entity, Transform())
        world.add(entity, emitter)

        particleSystem.update(world, 0.1f)
        assertEquals(0, emitter.emit(3))
        particleSystem.update(world, 0.1f)

        assertEquals(0, emitter.liveParticleCount)
        assertEquals(3L, emitter.droppedParticleCount, "an explicit emit into an empty pool counts every particle")
        assertTrue(world.collectSpriteDraws().isEmpty())
    }

    @Test
    fun dynamicSpawnRateIsAskedOnlyOnTheContinuousRatePath() {
        val world = World()
        val particleSystem = Particle2DSystem()
        var cycleAsks = 0
        var cappedAsks = 0

        val cycled = ParticleEmitter2D(maxParticles = 8, random = Random(5)).apply {
            burstSchedule = ParticleBurstSchedule(cycleSeconds = 1f, activeSeconds = 0.5f, burstInterval = 0.1f, burstSize = 1)
            dynamicSpawnRate = ParticleSpawnRate { cycleAsks += 1; 50f }
        }
        val capped = ParticleEmitter2D(maxParticles = 8, random = Random(6)).apply {
            spawnRate = 0f
            burstCount = 2
            lifetime = 10f
            dynamicSpawnRate = ParticleSpawnRate { cappedAsks += 1; 100f }
        }
        world.add(world.create().also { world.add(it, Transform()) }, cycled)
        world.add(world.create().also { world.add(it, Transform()) }, capped)

        particleSystem.update(world, 0.1f)
        assertEquals(0, cycleAsks, "a burst cycle replaces the rate, so the provider is never asked")
        assertEquals(1, cappedAsks)
        assertEquals(2, capped.liveParticleCount, "the dynamic rate spawns up to burstCount")

        particleSystem.update(world, 0.1f)
        particleSystem.update(world, 0.1f)
        assertEquals(1, cappedAsks, "a used-up burstCount spawns nothing more, so the provider is not asked again")
    }

    @Test
    fun burstEmitter2DPoolRecyclesResidentSlotsAndSkipsAndCountsOverflow() {
        val world = World()
        val transformSystem = TransformSystem()
        val particleSystem = Particle2DSystem()
        val pool = BurstEmitter2DPool(maxSlots = 2, slotMaxParticles = 8) { Random(it + 1) }

        pool.prepare(world)
        val residentCount = world.family<ParticleEmitter2D>().size
        assertEquals(2, residentCount)
        assertEquals(0, pool.activeSlotCount)

        assertTrue(pool.spawn(world, x = 1f, y = 2f, count = 4) { it.lifetime = 0.25f; it.speed = 0f })
        assertTrue(pool.spawn(world, x = -3f, y = 5f, count = 4) { it.lifetime = 0.25f; it.speed = 0f })
        assertEquals(2, pool.activeSlotCount)
        assertEquals(0L, pool.droppedBurstCount)

        // Third burst while both slots are active skips without allocating or evicting.
        assertFalse(pool.spawn(world, x = 9f, y = 9f, count = 4))
        assertEquals(1L, pool.droppedBurstCount)
        assertEquals(residentCount, world.family<ParticleEmitter2D>().size, "no new entity may be created on overflow")

        // Step past lifetime so both slots recycle automatically.
        transformSystem.update(world, 0.3f)
        particleSystem.update(world, 0.3f)
        assertEquals(0, pool.activeSlotCount)
        assertEquals(residentCount, world.family<ParticleEmitter2D>().size, "pooled entities remain alive for reuse")

        // Subsequent spawn reuses a recycled slot cleanly.
        assertTrue(pool.spawn(world, x = 4f, y = -1f, count = 3) { it.lifetime = 0.5f; it.speed = 0f })
        assertEquals(1, pool.activeSlotCount)

        pool.dispose(world)
        assertEquals(0, world.family<ParticleEmitter2D>().size)
    }

    @Test
    fun collectSpriteDrawsInterpolatesSizeTintFrameAndRanksSortingLayer() {
        val layers = SortingLayers(listOf("Background", "Default", "Foreground"))
        val world = World()
        val entity = world.create()
        val emitter = ParticleEmitter2D(maxParticles = 4, random = Random(0)).apply {
            texture = ""
            spawnRate = 0f
            lifetime = 1f
            size = 0.4f
            endSize = 1.4f
            startAlpha = 1f
            tint = Color(1f, 0f, 0f, 1f)
            endTint = Color(0f, 1f, 0f, 1f)
            speed = 0f
            sortingLayer = "Foreground"
            sortOrder = 5
            additive = true
        }
        world.add(entity, Transform())
        world.add(entity, emitter)

        emitter.emit(1)
        Particle2DSystem().update(world, 0.5f) // halfway through 1s lifetime

        val draw = world.collectSpriteDraws(layers).single()
        assertEquals("", draw.texture)
        assertEquals(0.9f, assertNotNull(draw.worldSize), 1e-4f)
        assertEquals(0.5f, draw.tint.r, 1e-4f)
        assertEquals(0.5f, draw.tint.g, 1e-4f)
        assertEquals(0.5f, draw.tint.a, 1e-4f, "linear fade at t=0.5 scales alpha by 0.5")
        assertEquals(5, draw.sortOrder)
        assertEquals(layers.rank("Foreground"), draw.sortLayer)
        assertTrue(draw.additive)
    }

    @Test
    fun theEmittersDefaultSortingLayerIsTheScenesDefaultLayer() {
        assertEquals(SortingLayers.DEFAULT, DEFAULT_PARTICLE_SORTING_LAYER_2D)
        assertEquals(SortingLayers.DEFAULT, ParticleEmitter2D().sortingLayer)
    }

    @Test
    fun aBurstTemplateIsValidatedOnceAndAPooledBurstDrawsWhereItWasFired() {
        assertFailsWith<IllegalArgumentException> { SceneParticleEmitter2D(lifetime = 0f).toBurstTemplate() }

        val world = World()
        val pool = BurstEmitter2DPool(maxSlots = 1, slotMaxParticles = 4) { Random(it + 3) }
        val template = SceneParticleEmitter2D(
            speed = 0f,
            lifetime = 1f,
            burstCount = 3,
            sortingLayer = "Foreground",
            sortOrder = 7,
            localSpace = true,
        ).toBurstTemplate()
        assertEquals(0, template.maxParticles)

        assertTrue(pool.spawn(world, x = 5f, y = -2f, template = template, z = 0.5f))
        Particle2DSystem(Transform2DPlacement()).update(world, 0.1f)

        val layers = SortingLayers(listOf("Background", "Default", "Foreground"))
        val draws = world.collectSpriteDraws(layers)
        assertEquals(3, draws.size, "the template's burstCount is the burst's size")
        for (draw in draws) {
            assertEquals(5f, draw.model.m03, 1e-4f)
            assertEquals(-2f, draw.model.m13, 1e-4f)
            assertEquals(0.5f, draw.model.m23, 1e-4f, "a pooled burst draws at the depth it was fired at")
            assertEquals(7, draw.sortOrder)
            assertEquals(layers.rank("Foreground"), draw.sortLayer)
        }
    }

    private class NoopResolutionContext(override val world: World) : SceneResolutionContext {
        override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit
        override fun recordRequest(request: Any) = Unit
    }
}

/** The scene's own placement, as the scene runtime wires it. */
private fun Particle2DSystem(isPlaying: () -> Boolean = { true }): Particle2DSystem =
    Particle2DSystem(Transform2DPlacement(), isPlaying)
