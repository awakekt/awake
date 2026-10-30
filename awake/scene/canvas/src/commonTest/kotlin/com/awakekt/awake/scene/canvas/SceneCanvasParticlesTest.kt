/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneVec2
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasParticleAlphaCurve
import com.awakekt.awake.ui.canvas.CanvasParticleBurstCycle
import com.awakekt.awake.ui.canvas.CanvasParticlePlacement
import com.awakekt.awake.ui.canvas.CanvasParticleShape
import com.awakekt.awake.ui.canvas.CanvasParticleShapeKind
import com.awakekt.awake.ui.canvas.CanvasParticleSpin
import com.awakekt.awake.ui.canvas.CanvasParticles
import com.awakekt.awake.ui.canvas.CanvasRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SceneCanvasParticlesTest {
    @Test
    fun fullSceneCanvasParticlesRoundTripsThroughJsonAndWorldExport() {
        val authored = sampleFullSceneCanvasParticles()
        assertTrue(authored.validate("hud.xp").isEmpty())
        assertFalse(authored.allowsMultiplePerNode)

        val node = SceneNode(
            name = "XpBar",
            components = listOf(
                SceneCanvasRect(width = 200f, height = 24f),
                SceneCanvasBar(),
                authored,
            ),
        )
        val json = CanvasPrefabs.json.encodeToString(SceneNode.serializer(), node)
        assertTrue("\"component\":\"canvas_particles\"" in json || "\"component\": \"canvas_particles\"" in json, json)

        val decoded = CanvasPrefabs.json.decodeFromString(SceneNode.serializer(), json)
        val world = loadCanvas(listOf(decoded))
        val element = world.canvasElements().getValue("XpBar")
        val live = assertNotNull(element.particles)
        assertEquals(96, live.maxParticles)
        assertEquals(Color(1f, 0.8f, 0.2f, 1f), live.tint)
        assertEquals(Color(1f, 0.2f, 0.05f, 0.5f), live.endTint)
        assertEquals(4f, live.gravityX)
        assertEquals(120f, live.gravityY)

        val exported = world.saveCanvas().single()
        val savedParticles = exported.components.filterIsInstance<SceneCanvasParticles>().single()
        assertEquals(authored, savedParticles)
    }

    private fun sampleFullSceneCanvasParticles(): SceneCanvasParticles = SceneCanvasParticles(
        anchor = CanvasAnchor.TopRight,
        offsetX = -6f,
        offsetY = 4f,
        placement = CanvasParticlePlacement.BehindContent,
        clipped = false,
        localSpace = false,
        additive = true,
        texture = "ui/spark_sheet.png",
        columns = 4,
        rows = 2,
        frameCount = 6,
        frameRate = 16f,
        enabled = false,
        maxParticles = 96,
        spawnRate = 24f,
        lifetime = 0.35f,
        maxLifetime = 0.75f,
        size = 10f,
        endSize = 2f,
        startAlpha = 0.9f,
        alphaCurve = CanvasParticleAlphaCurve(fadeInEnd = 0.1f, fadeOutStart = 0.65f),
        tint = SceneColor(1f, 0.8f, 0.2f, 1f),
        endTint = SceneColor(1f, 0.2f, 0.05f, 0.5f),
        shape = CanvasParticleShape(
            kind = CanvasParticleShapeKind.Ring,
            radius = 18f,
            innerRadius = 8f,
            halfWidth = 12f,
            halfHeight = 6f,
        ),
        speed = 32f,
        maxSpeed = 64f,
        directionDegrees = -45f,
        spreadDegrees = 90f,
        radialSpeed = 12f,
        convergeToOrigin = true,
        gravity = SceneVec2(4f, 120f),
        drag = 1.5f,
        turbulence = 8f,
        turbulenceFrequency = 2.5f,
        spin = CanvasParticleSpin(minDegreesPerSecond = -180f, maxDegreesPerSecond = 180f, randomStartAngle = true),
        burstCount = 18,
        burstCycle = CanvasParticleBurstCycle(
            cycleSeconds = 2f,
            activeSeconds = 0.6f,
            burstInterval = 0.2f,
            burstSize = 6,
        ),
    )

    @Test
    fun minimalCanvasParticlesJsonUsesDefaultsAndOmitsUnsetFields() {
        val node = CanvasPrefabs.json.decodeFromString(
            SceneNode.serializer(),
            """{"name":"Spark","components":[{"component":"canvas_rect"},{"component":"canvas_particles"}]}""",
        )
        val saved = node.components.filterIsInstance<SceneCanvasParticles>().single()
        assertEquals(SceneCanvasParticles(), saved)
        assertTrue(saved.validate("Spark").isEmpty())
    }

    @Test
    fun syncCanvasElementsPushesAndPullsParticlesAndCleansUpOnRemoval() {
        val world = World()
        val entity = world.create()
        world.add(entity, CanvasRect().apply { width = 80f; height = 32f })
        val initial = CanvasParticles(maxParticles = 16).apply {
            shape = CanvasParticleShape(kind = CanvasParticleShapeKind.ElementEdge)
            speed = 0f
        }
        world.add(entity, initial)

        world.syncCanvasElements()

        val element = assertNotNull(world.get<CanvasElement>(entity))
        assertSame(initial, element.particles)
        // Linked to element: ElementEdge resolves against element's 80x32 perimeter (|x|=40 or |y|=16).
        assertEquals(1, initial.emit(1))
        assertTrue(
            kotlin.math.abs(initial.simulation.posX[0]) > 1f || kotlin.math.abs(initial.simulation.posY[0]) > 1f,
        )

        val replacement = CanvasParticles(maxParticles = 24).apply {
            shape = CanvasParticleShape(kind = CanvasParticleShapeKind.ElementEdge)
            speed = 0f
        }
        element.particles = replacement
        world.syncCanvasElements()
        assertSame(replacement, world.get<CanvasParticles>(entity))
        // Unlinked initial falls back to 0x0 element dimensions on subsequent emit.
        initial.reset()
        initial.emit(1)
        assertEquals(0f, initial.simulation.posX[0])
        assertEquals(0f, initial.simulation.posY[0])
        replacement.emit(1)
        assertTrue(
            kotlin.math.abs(replacement.simulation.posX[0]) > 1f || kotlin.math.abs(replacement.simulation.posY[0]) > 1f,
        )

        world.remove<CanvasParticles>(entity)
        world.syncCanvasElements()
        assertNull(element.particles)

        element.particles = CanvasParticles(maxParticles = 8)
        world.syncCanvasElements()
        assertNotNull(world.get<CanvasParticles>(entity))

        world.removeCanvasElement(entity)
        assertNull(world.get<CanvasParticles>(entity))
    }

    @Test
    fun validateCatchesInvalidSheetPoolLifetimeVelocityForceAndNestedConfigs() {
        fun messages(particles: SceneCanvasParticles): List<String> =
            particles.validate("node").map { it.message }

        assertTrue(messages(SceneCanvasParticles(offsetX = Float.NaN)).any { "offset" in it })
        assertTrue(messages(SceneCanvasParticles(texture = "   ")).any { "texture must not be blank" in it })
        assertTrue(messages(SceneCanvasParticles(columns = 0)).any { "columns" in it })
        assertTrue(messages(SceneCanvasParticles(rows = 0)).any { "rows" in it })
        assertTrue(messages(SceneCanvasParticles(frameCount = 0)).any { "frameCount must be at least 1" in it })
        assertTrue(messages(SceneCanvasParticles(texture = "s.png", columns = 2, rows = 2, frameCount = 5)).any { "4 cells" in it })
        assertTrue(messages(SceneCanvasParticles(texture = "", columns = 2)).any { "require a texture" in it })
        assertTrue(messages(SceneCanvasParticles(frameRate = -1f)).any { "frameRate" in it })
        assertTrue(messages(SceneCanvasParticles(maxParticles = 0)).any { "maxParticles" in it })
        assertTrue(messages(SceneCanvasParticles(spawnRate = -0.1f)).any { "spawnRate" in it })
        assertTrue(messages(SceneCanvasParticles(burstCount = 0)).any { "burstCount" in it })
        assertTrue(messages(SceneCanvasParticles(lifetime = 0f)).any { "lifetime" in it })
        assertTrue(messages(SceneCanvasParticles(lifetime = 0.8f, maxLifetime = 0.4f)).any { "maxLifetime" in it })
        assertTrue(messages(SceneCanvasParticles(size = 0f)).any { "size" in it })
        assertTrue(messages(SceneCanvasParticles(endSize = -1f)).any { "endSize" in it })
        assertTrue(messages(SceneCanvasParticles(startAlpha = 1.2f)).any { "startAlpha" in it })
        assertTrue(messages(SceneCanvasParticles(speed = -1f)).any { "speed" in it })
        assertTrue(messages(SceneCanvasParticles(speed = 20f, maxSpeed = 10f)).any { "maxSpeed" in it })
        assertTrue(messages(SceneCanvasParticles(directionDegrees = Float.POSITIVE_INFINITY)).any { "directionDegrees" in it })
        assertTrue(messages(SceneCanvasParticles(spreadDegrees = 400f)).any { "spreadDegrees" in it })
        assertTrue(messages(SceneCanvasParticles(radialSpeed = Float.NaN)).any { "radialSpeed" in it })
        assertTrue(messages(SceneCanvasParticles(gravity = SceneVec2(0f, Float.NaN))).any { "gravity" in it })
        assertTrue(messages(SceneCanvasParticles(drag = -1f)).any { "drag" in it })
        assertTrue(messages(SceneCanvasParticles(turbulence = Float.NaN)).any { "turbulence" in it })
        assertTrue(messages(SceneCanvasParticles(turbulenceFrequency = -0.5f)).any { "turbulenceFrequency" in it })
        assertTrue(
            messages(
                SceneCanvasParticles(
                    shape = CanvasParticleShape(kind = CanvasParticleShapeKind.Ring, radius = 4f, innerRadius = 8f),
                ),
            ).any { "innerRadius" in it },
        )
        assertTrue(
            messages(
                SceneCanvasParticles(alphaCurve = CanvasParticleAlphaCurve(fadeInEnd = 0.7f, fadeOutStart = 0.3f)),
            ).any { "fadeInEnd" in it },
        )
        assertTrue(
            messages(
                SceneCanvasParticles(spin = CanvasParticleSpin(minDegreesPerSecond = 90f, maxDegreesPerSecond = -90f)),
            ).any { "minDegreesPerSecond" in it },
        )
        assertTrue(
            messages(
                SceneCanvasParticles(
                    burstCycle = CanvasParticleBurstCycle(
                        cycleSeconds = 1f,
                        activeSeconds = 2f,
                        burstInterval = 0.2f,
                        burstSize = 4,
                    ),
                ),
            ).any { "activeSeconds" in it },
        )
    }
}
