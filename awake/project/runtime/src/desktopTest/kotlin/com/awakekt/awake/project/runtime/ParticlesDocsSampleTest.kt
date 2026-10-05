/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.particles.ParticleMotion
import com.awakekt.awake.particles.ParticleSystem
import com.awakekt.awake.particles.ParticleVisual
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.dsl.cameraEntity
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.particles.TransformPlacement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The "Particles" guide's samples: the quad and material, the emitter, and the system that runs it. */
class ParticlesDocsSampleTest {

    @Test
    fun anEmitterSpawnsParticlesThatDrawAsOneInstancedDraw() = runTest {
        val renderer = DocsRenderer()
        val spark = TextureAsset(ByteArray(4) { -1 }, width = 1, height = 1)
        val game = app {
            scene("sparks") {
                cameraEntity("camera") { transform(y = 1f, z = 6f) }
                // --8<-- [start:assets]
                assets {
                    // A particle is a camera-facing quad: position and UV only.
                    mesh("particle-quad", MeshGeometry(QUAD_VERTICES, QUAD_INDICES, format = VertexFormat.PositionUv))
                    // The sprite each particle shows; tinted per particle by the shader.
                    material("particle") { renderer.createMaterial(ParticleUniformLayout, texture = spark) }
                }
                // --8<-- [end:assets]
                // --8<-- [start:system]
                frameSystem("particles") { ParticleSystem(TransformPlacement) }
                // --8<-- [end:system]
                // --8<-- [start:emitter]
                onReady {
                    world.scene {
                        entity("sparks") {
                            with(
                                ParticleEmitter(
                                    mesh = requireMesh("particle-quad"),
                                    material = requireMaterial("particle"),
                                    origin = Vec3f(0f, 0.5f, 0f),
                                    maxParticles = 200,
                                    spawnRate = 60f,
                                    lifetime = 1.5f,
                                    startAlpha = 1f,
                                    scale = 0.2f,
                                    motion = ParticleMotion(
                                        baseVelocity = Vec3f(0f, 3f, 0f),
                                        velocityJitter = 0.5f,
                                        coneHalfAngleDegrees = 20f,
                                    ),
                                    visual = ParticleVisual(
                                        startColor = Vec3f(1f, 0.8f, 0.3f),
                                        endColor = Vec3f(0.6f, 0.1f, 0f),
                                    ),
                                ),
                            )
                        }
                    }
                }
                // --8<-- [end:emitter]
            }
        }
        game.ready(renderer)
        repeat(30) { game.update(FRAME, WIDTH, HEIGHT) }

        val draw = renderer.draws.single { it.instanceColors != null }
        assertTrue(draw.instanceModels!!.isNotEmpty(), "live particles are instances of one draw")
        assertEquals(draw.instanceModels!!.size, draw.instanceColors!!.size)
        game.dispose()
    }

    private companion object {
        val QUAD_VERTICES = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 1f,
            0.5f, -0.5f, 0f, 1f, 1f,
            0.5f, 0.5f, 0f, 1f, 0f,
            -0.5f, 0.5f, 0f, 0f, 0f,
        )
        val QUAD_INDICES = intArrayOf(0, 1, 2, 2, 3, 0)
    }
}
