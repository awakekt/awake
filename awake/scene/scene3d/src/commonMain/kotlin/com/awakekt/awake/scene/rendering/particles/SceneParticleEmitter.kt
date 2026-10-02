/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.particles

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Camera-facing sprites spawned at the node's world position: a [ParticleEmitter] authored as data.
 * [texture] is a project image file. Sizes, speeds and [spawnRadius] are world units; the node's
 * rotation and scale do not apply. Each particle fades from [startAlpha] to 0 over [lifetime]
 * seconds while its tint moves from [color] to [endColor] and its size from [scale] to [endScale].
 * [additive] glows add to what is behind them, which needs the plan's particle pipeline built with
 * `buildAdditive`.
 */
@Serializable
@SerialName("particle_emitter")
data class SceneParticleEmitter(
    val texture: String,
    val maxParticles: Int = 64,
    val spawnRate: Float = 10f,
    val lifetime: Float = 1f,
    val startAlpha: Float = 1f,
    val scale: Float = 0.2f,
    val endScale: Float? = null,
    val velocity: SceneVec3 = SceneVec3(0f, 1f, 0f),
    val velocityJitter: Float = 0f,
    val coneHalfAngleDegrees: Float? = null,
    val spawnRadius: Float = 0f,
    val radialSpeed: Float = 0f,
    val color: SceneColor = SceneColor(),
    val endColor: SceneColor? = null,
    val frameCount: Int = 1,
    val frameRate: Float = 8f,
    val additive: Boolean = false,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        fun require(ok: Boolean, message: String) {
            if (!ok) add(SceneValidationIssue(path, "particle_emitter.$message"))
        }
        require(texture.isNotBlank(), "texture must name an image")
        require(maxParticles > 0, "maxParticles must be greater than 0")
        require(spawnRate >= 0f, "spawnRate must not be negative")
        require(lifetime > 0f, "lifetime must be greater than 0")
        require(startAlpha in 0f..1f, "startAlpha must be within 0..1")
        require(scale > 0f, "scale must be greater than 0")
        require(endScale == null || endScale >= 0f, "endScale must not be negative")
        require(spawnRadius >= 0f, "spawnRadius must not be negative")
        require(frameCount >= 1, "frameCount must be at least 1")
        require(frameRate >= 0f, "frameRate must not be negative")
    }
}

/** A [SceneParticleEmitter] on a live entity; [ParticleContentSystem] gives it its [ParticleEmitter]. */
class ParticleEmitterSource(val authored: SceneParticleEmitter)

object ParticleEmitterBinding : SceneComponentBinding<ParticleEmitterSource, SceneParticleEmitter> {
    override val componentClass: KClass<ParticleEmitterSource> = ParticleEmitterSource::class
    override val schemaClass: KClass<SceneParticleEmitter> = SceneParticleEmitter::class
    override val serializer = SceneParticleEmitter.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneParticleEmitter,
        context: SceneResolutionContext,
    ) {
        world.add(entity, ParticleEmitterSource(component))
    }

    override fun export(world: World, entity: Entity, component: ParticleEmitterSource): SceneParticleEmitter =
        component.authored
}

/**
 * Reads and decodes every image [document]'s `particle_emitter`s name, by path, for a
 * [ParticleContentSystem]. One that fails to read or decode is logged and left out, and its
 * emitters draw nothing.
 */
// TooGenericExceptionCaught: an image that fails to read or decode for any reason only loses its emitters.
@Suppress("TooGenericExceptionCaught")
suspend fun loadParticleSprites(document: SceneDocument, assets: AssetSource): Map<String, TextureAsset> {
    val paths = document.nodes.flatMap { it.particleTextures() }.distinct()
    return buildMap {
        for (path in paths) {
            try {
                val bitmap = createBitmap(assets.read(AssetPath(path)).getOrThrow())
                put(path, TextureAsset(bitmap.toRgba8Bytes(), bitmap.width, bitmap.height))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.warn { "Particle sprite '$path' could not load: ${failure.message}" }
            }
        }
    }
}

private fun SceneNode.particleTextures(): List<String> =
    components.filterIsInstance<SceneParticleEmitter>().map { it.texture } + children.flatMap { it.particleTextures() }

/**
 * Gives each [ParticleEmitterSource] entity its [ParticleEmitter], following the entity, once its
 * sprite is in [sprites] (see [loadParticleSprites]). Emitters share one quad and one material per
 * sprite, created through [renderer] and destroyed by [release]. Run it alongside [ParticleSystem].
 */
class ParticleContentSystem(
    private val renderer: Renderer,
    private val sprites: Map<String, TextureAsset>,
) : System {
    private var quad: Mesh? = null
    private val materials = HashMap<String, Material>()
    private val waiting = ArrayList<Entity>()

    override fun update(world: World, delta: Float) {
        waiting.clear()
        world.queryEach(ParticleEmitterSource::class) { entity, _ ->
            if (!world.has(entity, ParticleEmitter::class)) waiting += entity
        }
        for (entity in waiting) {
            val authored = world.get<ParticleEmitterSource>(entity)?.authored ?: continue
            materialFor(authored.texture)?.let { world.add(entity, emitter(world, entity, authored, it)) }
        }
    }

    /** Destroys the quad and materials this system created. */
    fun release() {
        quad?.destroy()
        quad = null
        materials.values.forEach(Material::destroy)
        materials.clear()
    }

    private fun materialFor(path: String): Material? = materials[path]
        ?: sprites[path]?.let { sprite -> renderer.createMaterial(ParticleUniformLayout, texture = sprite).also { materials[path] = it } }

    private fun emitter(world: World, entity: Entity, authored: SceneParticleEmitter, material: Material): ParticleEmitter {
        val placed = world.get<Transform>(entity)?.worldMatrix
        val mesh = quad ?: renderer.createMesh(QUAD).also { quad = it }
        return ParticleEmitter(
            mesh = mesh,
            material = material,
            origin = Vec3f(placed?.m03 ?: 0f, placed?.m13 ?: 0f, placed?.m23 ?: 0f),
            maxParticles = authored.maxParticles,
            spawnRate = authored.spawnRate,
            lifetime = authored.lifetime,
            startAlpha = authored.startAlpha,
            scale = authored.scale,
            motion = ParticleMotion(
                baseVelocity = authored.velocity.toVec3f(),
                velocityJitter = authored.velocityJitter,
                coneHalfAngleDegrees = authored.coneHalfAngleDegrees,
                spawnRadius = authored.spawnRadius,
                radialSpeed = authored.radialSpeed,
            ),
            visual = ParticleVisual(
                startColor = authored.color.toVec3f(),
                endColor = (authored.endColor ?: authored.color).toVec3f(),
                frameCount = authored.frameCount,
                frameRate = authored.frameRate,
                endScale = authored.endScale,
                additive = authored.additive,
            ),
            dynamics = ParticleDynamics(followEntity = entity),
        )
    }
}

private fun SceneVec3.toVec3f() = Vec3f(x, y, z)

private fun SceneColor.toVec3f() = Vec3f(r, g, b)

private val log = Logger("scene-particles")

/** A unit quad in the XY plane, position and UV, as the particle shader draws it facing the camera. */
private val QUAD = MeshGeometry(
    floatArrayOf(
        -0.5f, -0.5f, 0f, 0f, 1f,
        0.5f, -0.5f, 0f, 1f, 1f,
        0.5f, 0.5f, 0f, 1f, 0f,
        -0.5f, 0.5f, 0f, 0f, 0f,
    ),
    intArrayOf(0, 1, 2, 2, 3, 0),
    format = VertexFormat.PositionUv,
)
