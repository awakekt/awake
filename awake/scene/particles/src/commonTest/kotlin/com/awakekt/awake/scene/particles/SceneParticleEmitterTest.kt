/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.particles

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.ParticleAlphaCurve
import com.awakekt.awake.particles.ParticleBurstCycle
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.particles.ParticleFacing
import com.awakekt.awake.particles.ParticleGround
import com.awakekt.awake.particles.ParticleLifecycle
import com.awakekt.awake.particles.ParticleMotion
import com.awakekt.awake.particles.ParticleSpin
import com.awakekt.awake.particles.ParticleSystem
import com.awakekt.awake.particles.ParticleVisual
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.particles.ParticleContentSystem
import com.awakekt.awake.scene.particles.ParticleEmitterBinding
import com.awakekt.awake.scene.particles.ParticleEmitterSource
import com.awakekt.awake.scene.particles.SceneParticleAlphaCurve
import com.awakekt.awake.scene.particles.SceneParticleBox
import com.awakekt.awake.scene.particles.SceneParticleBurstCycle
import com.awakekt.awake.scene.particles.SceneParticleEmitter
import com.awakekt.awake.scene.particles.SceneParticleGround
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class SceneParticleEmitterTest {
    init {
        SceneComponentRegistry.registerGlobal(ParticleEmitterBinding)
    }

    private val sprite = TextureAsset(ByteArray(4) { -1 }, width = 1, height = 1)
    private val dust = SceneParticleEmitter(texture = "dust.png", endScale = 0.5f, radialSpeed = 3f, additive = true)

    @Test
    fun anEmitterSpawnsAtAndFollowsItsNodeOnceItsSpriteIsLoaded() {
        val world = World()
        val node = world.node(dust, at = Vec3f(1f, 2f, 3f))

        ParticleContentSystem(CountingRenderer(), mapOf("dust.png" to sprite)).update(world, 0f)

        val emitter = requireNotNull(world.get<ParticleEmitter>(node))
        assertEquals(Vec3f(1f, 2f, 3f), emitter.origin)
        assertEquals(node, emitter.dynamics.followEntity)
        assertEquals(0.5f, emitter.visual.endScale)
        assertEquals(true, emitter.visual.additive)
        assertEquals(3f, emitter.motion.radialSpeed)
    }

    /** `facing` survives the scene document both ways and reaches the live emitter; left out, it is `Camera`. */
    @Test
    fun facingRoundTripsThroughTheDocumentAndReachesTheLiveEmitter() {
        fun decode(component: String) = SceneLoader.decode("""{"version": 1, "nodes": [{"components": [$component]}]}""")
            .nodes.single().components.single() as SceneParticleEmitter
        val glow = decode("""{"component": "particle_emitter", "texture": "glow.png", "facing": "Flat"}""")
        val reencoded = SceneLoader.decode(SceneLoader.encode(SceneDocument(nodes = listOf(SceneNode("glow", components = listOf(glow))))))
        val world = World()
        val node = world.node(glow)

        ParticleContentSystem(CountingRenderer(), mapOf("glow.png" to sprite)).update(world, 0f)

        assertEquals(ParticleFacing.Flat, glow.facing)
        assertEquals(glow, reencoded.nodes.single().components.single())
        assertEquals(ParticleFacing.Flat, world.get<ParticleEmitter>(node)!!.visual.facing)
        assertEquals(glow, ParticleEmitterBinding.export(world, node, world.get<ParticleEmitterSource>(node)!!))
        assertEquals(ParticleFacing.Camera, decode("""{"component": "particle_emitter", "texture": "dust.png"}""").facing)
    }

    @Test
    fun emittersShareOneQuadAndOneMaterialPerSpriteUntilReleased() {
        val world = World()
        val renderer = CountingRenderer()
        val first = world.node(dust)
        val second = world.node(dust)
        world.node(dust.copy(texture = "spark.png"))
        val content = ParticleContentSystem(renderer, mapOf("dust.png" to sprite, "spark.png" to sprite))

        content.update(world, 0f)
        content.update(world, 0f)

        assertEquals(1, renderer.meshes, "one quad for every emitter")
        assertEquals(2, renderer.materials, "one material per sprite")
        assertSame(world.get<ParticleEmitter>(first)!!.material, world.get<ParticleEmitter>(second)!!.material)
        content.close()
        assertEquals(3, renderer.destroyed)
    }

    @Test
    fun anEmitterWhoseSpriteDidNotLoadDrawsNothing() {
        val world = World()
        val node = world.node(dust)

        ParticleContentSystem(CountingRenderer(), emptyMap()).update(world, 0f)

        assertNull(world.get<ParticleEmitter>(node))
    }

    @Test
    fun theAuthoredComponentExportsAndValidates() {
        val world = World()
        val node = world.node(dust)

        assertEquals(dust, ParticleEmitterBinding.export(world, node, world.get<ParticleEmitterSource>(node)!!))
        assertEquals(
            listOf("particle_emitter.lifetime must be greater than 0", "particle_emitter.scale must be greater than 0"),
            dust.copy(lifetime = 0f, scale = 0f).validate("fx").map { it.message },
        )
    }

    /** Every option set to something other than its default, so one the mapping drops cannot hide. */
    private val everything = SceneParticleEmitter(
        texture = "dust.png",
        maxParticles = 12,
        spawnRate = 33f,
        lifetime = 2.5f,
        startAlpha = 0.75f,
        scale = 0.4f,
        endScale = 1.5f,
        velocity = SceneVec3(1f, 2f, 3f),
        velocityJitter = 0.3f,
        coneHalfAngleDegrees = 25f,
        spawnRadius = 0.6f,
        radialSpeed = 1.2f,
        color = SceneColor(0.1f, 0.2f, 0.3f),
        endColor = SceneColor(0.9f, 0.8f, 0.7f),
        frameCount = 4,
        frameRate = 6f,
        additive = true,
        facing = ParticleFacing.Flat,
        acceleration = SceneVec3(0f, -9.8f, 0.5f),
        inheritOrientation = true,
        alphaCurve = SceneParticleAlphaCurve(fadeInEnd = 0.2f, fadeOutStart = 0.7f),
        burstCycle = SceneParticleBurstCycle(cycleSeconds = 2f, activeSeconds = 1f, burstInterval = 0.25f, burstSize = 5),
        turbulence = 1.5f,
        turbulenceFrequency = 2.5f,
        convergeToOrigin = true,
        stretchWithVelocity = true,
        stretchFactor = 0.2f,
        burstCount = 40,
        ground = SceneParticleGround(
            groundY = -1f,
            restitution = 0.5f,
            friction = 0.8f,
            colliders = listOf(SceneParticleBox(SceneVec3(-1f, 0f, -1f), SceneVec3(1f, 2f, 1f))),
        ),
        spin = SceneParticleSpin(minDegreesPerSecond = -45f, maxDegreesPerSecond = 90f, randomStartAngle = true),
    )

    private fun liveFrom(settings: SceneParticleEmitter): Pair<World, ParticleEmitter> {
        val world = World()
        val node = world.node(settings)
        ParticleContentSystem(CountingRenderer(), mapOf("dust.png" to sprite)).update(world, 0f)
        return world to requireNotNull(world.get<ParticleEmitter>(node))
    }

    private fun problems(settings: SceneParticleEmitter): List<String> = settings.validate("fx").map { it.message }

    @Test
    fun everyOptionOfASceneFileReachesTheLiveEmitter() {
        val (_, live) = liveFrom(everything)

        assertEquals(listOf(12, 33f, 2.5f, 0.75f, 0.4f), listOf(live.maxParticles, live.spawnRate, live.lifetime, live.startAlpha, live.scale))
        assertEquals(
            ParticleMotion(
                baseVelocity = Vec3f(1f, 2f, 3f),
                velocityJitter = 0.3f,
                coneHalfAngleDegrees = 25f,
                spawnRadius = 0.6f,
                convergeToOrigin = true,
                turbulence = 1.5f,
                turbulenceFrequency = 2.5f,
                radialSpeed = 1.2f,
                acceleration = Vec3f(0f, -9.8f, 0.5f),
                inheritOrientation = true,
            ),
            live.motion,
        )
        assertEquals(
            ParticleVisual(
                startColor = Vec3f(0.1f, 0.2f, 0.3f),
                endColor = Vec3f(0.9f, 0.8f, 0.7f),
                frameCount = 4,
                frameRate = 6f,
                stretchWithVelocity = true,
                stretchFactor = 0.2f,
                endScale = 1.5f,
                additive = true,
                facing = ParticleFacing.Flat,
                alphaCurve = ParticleAlphaCurve(fadeInEnd = 0.2f, fadeOutStart = 0.7f),
                spin = ParticleSpin(minDegreesPerSecond = -45f, maxDegreesPerSecond = 90f, randomStartAngle = true),
            ),
            live.visual,
        )
        assertEquals(
            ParticleGround(
                groundY = -1f,
                colliders = listOf(Aabb(Vec3f(-1f, 0f, -1f), Vec3f(1f, 2f, 1f))),
                restitution = 0.5f,
                friction = 0.8f,
            ),
            live.ground,
        )
        assertEquals(
            ParticleLifecycle(burstCount = 40, burstCycle = ParticleBurstCycle(cycleSeconds = 2f, activeSeconds = 1f, burstInterval = 0.25f, burstSize = 5)),
            live.lifecycle,
        )
    }

    /** An emitter that sets nothing extra behaves exactly as the library's own defaults do. */
    @Test
    fun anOptionLeftOutKeepsTheLibrarysOwnDefault() {
        val (_, live) = liveFrom(SceneParticleEmitter(texture = "dust.png"))

        assertEquals(ParticleMotion(), live.motion)
        assertEquals(ParticleVisual(), live.visual)
        assertEquals(ParticleGround(), live.ground)
        assertEquals(ParticleLifecycle(), live.lifecycle)
    }

    @Test
    fun aSceneFileWithEveryOptionSetIsValid() {
        assertEquals(emptyList(), problems(everything))
    }

    @Test
    fun aBadMotionOptionIsRefusedByName() {
        assertEquals(listOf("particle_emitter.acceleration must be finite"), problems(dust.copy(acceleration = SceneVec3(0f, Float.NaN, 0f))))
        assertEquals(listOf("particle_emitter.turbulence must be finite"), problems(dust.copy(turbulence = Float.POSITIVE_INFINITY)))
        assertEquals(
            listOf("particle_emitter.turbulenceFrequency must be finite and not negative"),
            problems(dust.copy(turbulenceFrequency = -1f)),
        )
        assertEquals(listOf("particle_emitter.stretchFactor must be finite and not negative"), problems(dust.copy(stretchFactor = -0.1f)))
        assertEquals(listOf("particle_emitter.burstCount must be greater than 0"), problems(dust.copy(burstCount = 0)))
    }

    @Test
    fun aBadAlphaCurveIsRefused() {
        assertEquals(
            listOf("particle_emitter.alphaCurve.fadeInEnd must not come after fadeOutStart"),
            problems(dust.copy(alphaCurve = SceneParticleAlphaCurve(fadeInEnd = 0.8f, fadeOutStart = 0.2f))),
        )
        assertEquals(
            listOf("particle_emitter.alphaCurve fractions must be within 0..1"),
            problems(dust.copy(alphaCurve = SceneParticleAlphaCurve(fadeInEnd = 1.5f, fadeOutStart = 1.5f))),
        )
    }

    @Test
    fun aBadSpinIsRefused() {
        assertEquals(
            listOf("particle_emitter.spin.minDegreesPerSecond must not be above maxDegreesPerSecond"),
            problems(dust.copy(spin = SceneParticleSpin(minDegreesPerSecond = 10f, maxDegreesPerSecond = -10f))),
        )
        assertEquals(
            listOf("particle_emitter.spin rates must be finite"),
            problems(dust.copy(spin = SceneParticleSpin(minDegreesPerSecond = Float.NaN, maxDegreesPerSecond = 10f))),
        )
        assertEquals(emptyList(), problems(dust.copy(spin = SceneParticleSpin(minDegreesPerSecond = 30f, maxDegreesPerSecond = 30f))))
    }

    @Test
    fun aBadBurstCycleIsRefusedByEachPartThatIsWrong() {
        fun cycle(seconds: Float = 2f, active: Float = 1f, interval: Float = 0.25f, size: Int = 5) =
            dust.copy(burstCycle = SceneParticleBurstCycle(seconds, active, interval, size))

        assertEquals(emptyList(), problems(cycle()))
        assertEquals(
            listOf("particle_emitter.burstCycle.cycleSeconds must be greater than 0", "particle_emitter.burstCycle.activeSeconds must be within 0 and cycleSeconds"),
            problems(cycle(seconds = 0f)),
        )
        assertEquals(
            listOf("particle_emitter.burstCycle.activeSeconds must be within 0 and cycleSeconds"),
            problems(cycle(active = 3f)),
        )
        assertEquals(listOf("particle_emitter.burstCycle.burstInterval must be greater than 0"), problems(cycle(interval = 0f)))
        assertEquals(listOf("particle_emitter.burstCycle.burstSize must be greater than 0"), problems(cycle(size = 0)))
    }

    @Test
    fun aBadGroundIsRefusedByEachPartThatIsWrong() {
        fun ground(ground: SceneParticleGround) = dust.copy(ground = ground)
        val inside = SceneParticleBox(SceneVec3(0f, 0f, 0f), SceneVec3(1f, 1f, 1f))
        val inverted = SceneParticleBox(SceneVec3(1f, 0f, 0f), SceneVec3(0f, 1f, 1f))

        assertEquals(emptyList(), problems(ground(SceneParticleGround(groundY = 0f, colliders = listOf(inside)))))
        assertEquals(listOf("particle_emitter.ground.groundY must be finite"), problems(ground(SceneParticleGround(groundY = Float.NaN))))
        assertEquals(
            listOf("particle_emitter.ground.restitution must be finite and not negative"),
            problems(ground(SceneParticleGround(restitution = -1f))),
        )
        assertEquals(
            listOf("particle_emitter.ground.friction must be finite and not negative"),
            problems(ground(SceneParticleGround(friction = Float.NaN))),
        )
        assertEquals(
            listOf("particle_emitter.ground.colliders[1] must be finite with min at or below max on every axis"),
            problems(ground(SceneParticleGround(colliders = listOf(inside, inverted)))),
        )
    }

    /** The file shape: nested objects and lists a scene author writes by hand decode to the settings they say. */
    @Test
    fun theNewOptionsAreWrittenInASceneFileAsNestedData() {
        val decoded = SceneLoader.decode(
            """
            {"version": 1, "nodes": [{"components": [{
              "component": "particle_emitter", "texture": "dust.png",
              "acceleration": {"x": 0, "y": -9.8, "z": 0}, "inheritOrientation": true,
              "alphaCurve": {"fadeInEnd": 0.2, "fadeOutStart": 0.7},
              "spin": {"minDegreesPerSecond": -45, "maxDegreesPerSecond": 90, "randomStartAngle": true},
              "burstCycle": {"cycleSeconds": 2, "activeSeconds": 1, "burstInterval": 0.25, "burstSize": 5},
              "turbulence": 1.5, "turbulenceFrequency": 2.5, "convergeToOrigin": true,
              "stretchWithVelocity": true, "stretchFactor": 0.2, "burstCount": 40,
              "ground": {"groundY": -1, "restitution": 0.5, "friction": 0.8,
                "colliders": [{"min": {"x": -1, "y": 0, "z": -1}, "max": {"x": 1, "y": 2, "z": 1}}]}
            }]}]}
            """.trimIndent(),
        ).nodes.single().components.single() as SceneParticleEmitter

        assertEquals(SceneVec3(0f, -9.8f, 0f), decoded.acceleration)
        assertEquals(SceneParticleAlphaCurve(0.2f, 0.7f), decoded.alphaCurve)
        assertEquals(SceneParticleSpin(-45f, 90f, randomStartAngle = true), decoded.spin)
        assertEquals(SceneParticleBurstCycle(2f, 1f, 0.25f, 5), decoded.burstCycle)
        assertEquals(40, decoded.burstCount)
        assertEquals(listOf(true, true, true), listOf(decoded.inheritOrientation, decoded.convergeToOrigin, decoded.stretchWithVelocity))
        assertEquals(listOf(1.5f, 2.5f, 0.2f), listOf(decoded.turbulence, decoded.turbulenceFrequency, decoded.stretchFactor))
        val ground = assertNotNull(decoded.ground)
        assertEquals(listOf(-1f, 0.5f, 0.8f), listOf(ground.groundY, ground.restitution, ground.friction))
        assertEquals(listOf(SceneParticleBox(SceneVec3(-1f, 0f, -1f), SceneVec3(1f, 2f, 1f))), ground.colliders)
        assertEquals(emptyList(), decoded.validate("fx"))
    }

    @Test
    fun aSceneFileWithEveryOptionSetSurvivesAnEncodeAndDecode() {
        val document = SceneDocument(nodes = listOf(SceneNode("fx", components = listOf(everything))))

        val back = SceneLoader.decode(SceneLoader.encode(document))

        assertEquals(everything, back.nodes.single().components.single())
    }

    /** `facing` is written in scene files as the runtime enum's constant names, so those names are the file format. */
    @Test
    fun theNamesOfTheFacingsAreTheFileFormat() {
        assertEquals(listOf("Camera", "Flat"), ParticleFacing.entries.map { it.name })
    }

    // --- authored options doing what they say, through the systems a played scene runs

    @Test
    fun anAuthoredAccelerationPullsParticlesThroughBothSystems() {
        val world = World()
        world.node(dust.copy(maxParticles = 1, spawnRate = 1000f, lifetime = 100f, velocity = SceneVec3(0f, 0f, 0f), acceleration = SceneVec3(0f, -10f, 0f)))
        val content = ParticleContentSystem(CountingRenderer(), mapOf("dust.png" to sprite))
        val particles = ParticleSystem(TransformPlacement)
        content.update(world, 0f)

        particles.update(world, 0.1f)
        particles.update(world, 0.1f)

        val live = world.queryOne<ParticleEmitter>()
        var velocityY = Float.NaN
        live.forEachLiveParticle { _, velocity -> velocityY = velocity.y }
        assertEquals(1, live.liveParticleCount)
        assertEquals(-2f, velocityY, 1e-4f, "two 0.1s steps of -10")
    }

    /** Bursts at 0 and 0.25 of each second's first half, two particles each, nothing in the second half. */
    @Test
    fun anAuthoredBurstCycleSpawnsOnItsSchedule() {
        val world = World()
        world.node(
            dust.copy(
                maxParticles = 64,
                lifetime = 100f,
                velocity = SceneVec3(0f, 0f, 0f),
                burstCycle = SceneParticleBurstCycle(cycleSeconds = 1f, activeSeconds = 0.5f, burstInterval = 0.25f, burstSize = 2),
            ),
        )
        ParticleContentSystem(CountingRenderer(), mapOf("dust.png" to sprite)).update(world, 0f)
        val particles = ParticleSystem(TransformPlacement)
        val live = world.queryOne<ParticleEmitter>()
        fun aliveAfter(steps: Int): Int {
            repeat(steps) { particles.update(world, 0.05f) }
            return live.liveParticleCount
        }

        val partway = aliveAfter(7) // 0.35 s: both bursts of the first half have fired
        val pause = aliveAfter(10) // 0.85 s: the second half spawns nothing
        val nextCycle = aliveAfter(6) // 1.15 s: the next cycle's first burst has fired

        assertEquals(listOf(4, 4, 6), listOf(partway, pause, nextCycle))
    }

    /** `burstCount` makes a one-shot effect that removes its node once it is spent and every particle has died. */
    @Test
    fun anAuthoredBurstCountRemovesItsNodeOnceTheBurstIsSpentAndDead() {
        val world = World()
        val node = world.node(dust.copy(maxParticles = 8, spawnRate = 1000f, lifetime = 0.1f, burstCount = 3))
        ParticleContentSystem(CountingRenderer(), mapOf("dust.png" to sprite)).update(world, 0f)
        val particles = ParticleSystem(TransformPlacement)

        particles.update(world, 0.02f)
        val whilePlaying = world.get<ParticleEmitterSource>(node)
        repeat(10) { particles.update(world, 0.02f) }

        assertNotNull(whilePlaying, "the node stays while the burst is alive")
        assertNull(world.get<ParticleEmitterSource>(node), "the whole node is removed afterwards")
    }

    private inline fun <reified T : Any> World.queryOne(): T {
        var found: T? = null
        queryEach(T::class) { _, value -> found = value }
        return requireNotNull(found)
    }

    private fun World.node(settings: SceneParticleEmitter, at: Vec3f = Vec3f(0f, 0f, 0f)): Entity = create().also {
        add(
            it,
            Transform(position = at).apply {
                worldMatrix.m03 = at.x
                worldMatrix.m13 = at.y
                worldMatrix.m23 = at.z
            },
        )
        add(it, ParticleEmitterSource(settings))
    }

    private class CountingRenderer : NoopRenderer() {
        var meshes = 0
        var materials = 0
        var destroyed = 0

        override fun createMesh(geometry: MeshGeometry): Mesh {
            meshes++
            val mesh = super.createMesh(geometry)
            return object : Mesh by mesh {
                override fun destroy() {
                    destroyed++
                }
            }
        }

        override fun createMaterial(
            texture: TextureAsset?,
            renderTarget: RenderTarget?,
            uniformFloatCount: Int,
            pbrTextures: PbrTextureSet?,
        ): Material {
            materials++
            val material = super.createMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)
            return object : Material by material {
                override fun destroy() {
                    destroyed++
                }
            }
        }
    }
}
