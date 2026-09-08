/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.asset.gltf.GltfParser
import com.awakekt.awake.asset.shaderpack.TexturedUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.boundingRadius
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.particles.ParticleDynamics
import com.awakekt.awake.scene.rendering.particles.ParticleEmitter
import com.awakekt.awake.scene.rendering.particles.ParticleMotion
import com.awakekt.awake.scene.rendering.particles.ParticleVisual
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime

private const val TEXTURED_VERTEX_STRIDE_COMPONENTS = 11

/** Duck.gltf, parsed once and exposed to the `assets { }` DSL as named mesh/material
 * factories -- the same real parsing `GltfViewerDemo.preload()` does, repackaged so the scene
 * file can reference "duck"/"duck-material" by name instead of a demo building the entities
 * itself.
 *
 * Camera is fixed in v1 (no runtime centering), but the mesh is still scaled by
 * `1/modelRadius` at load time -- an authored camera has no way to know Duck.gltf's real,
 * unpredictable extents in advance, so the asset itself is normalized to a known unit scale
 * instead, and the scene file's fixed eye/center are authored against that known scale. */
internal object GltfViewerAssets {
    private var interleaved: FloatArray? = null
    private var indices: IntArray? = null
    private var texture: TextureAsset? = null
    private var pbrTextures: PbrTextureSet? = null
    private var pbrMaterial: PbrMaterial? = null

    suspend fun preload() {
        if (interleaved != null) return
        val bytes = readResourceBytes("assets/models/Duck.gltf")
        val gltfMesh = GltfParser.parse(bytes.decodeToString())
        val modelRadius = boundingRadius(gltfMesh.positions)
        interleaved =
            scalePositions(gltfMesh.toInterleavedPositionNormalColorUv(), 1f / modelRadius)
        indices = gltfMesh.indices
        val imageBytes = requireNotNull(gltfMesh.baseColorImageBytes)
        val bitmap = createBitmap(imageBytes)
        texture = TextureAsset(bitmap.toRgba8Bytes(), bitmap.width, bitmap.height)
        pbrTextures = PbrTextureSet(
            metallicRoughness = gltfMesh.metallicRoughnessImageBytes?.let { decodeTexture(it) },
            normal = gltfMesh.normalImageBytes?.let { decodeTexture(it) },
            occlusion = gltfMesh.occlusionImageBytes?.let { decodeTexture(it) },
            emissive = gltfMesh.emissiveImageBytes?.let { decodeTexture(it) },
        )
        pbrMaterial = PbrMaterial(
            metallic = gltfMesh.metallicFactor,
            roughness = gltfMesh.roughnessFactor,
            baseColorFactor = gltfMesh.baseColorFactor,
            emissiveFactor = gltfMesh.emissiveFactor,
        )
    }

    private suspend fun decodeTexture(imageBytes: ByteArray): TextureAsset {
        val bitmap = createBitmap(imageBytes)
        return TextureAsset(bitmap.toRgba8Bytes(), bitmap.width, bitmap.height)
    }

    /** The joint palette's PbrMaterial equivalent: `GltfViewerAssets.createMesh`/
     * `.createMaterial` feed the `assets { }` DSL, but a component this specific to one loaded
     * model isn't authorable in a scene document either, same reasoning
     * [SkinnedExampleDriver.attachPose] documents. */
    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        val node = instance.roots.find { it.name == "duck" } ?: return
        runtime.world.add(node.entity, requireNotNull(pbrMaterial))
        attachFrostAura(instance, runtime, followEntity = node.entity)
    }

    /** A status-effect particle aura around the duck -- [ParticleDynamics.followEntity]
     * re-anchors the emitter's origin to the duck's own [com.awakekt.awake.scene
     * .core.components.Transform] position every frame, so the effect would track the model if
     * it ever moved (fixed in this v1 viewer, but the wiring is real, not a static offset).
     * Frost/ice read: a ring of cyan particles ([ParticleMotion.spawnRadius], no convergence so
     * the ring hovers rather than collapsing to a point) swirled by [ParticleMotion.turbulence],
     * cooling from icy cyan to white as each particle ages -- reuses the shared "particle-quad"/
     * "particle" assets every other particle demo already uses, no new mesh/material needed. */
    private fun attachFrostAura(
        instance: Scene,
        runtime: SceneAppLifecycleRuntime,
        followEntity: Entity,
    ) {
        val auraNode = instance.roots.find { it.name == "duck-frost-aura" } ?: return
        runtime.world.add(
            auraNode.entity,
            ParticleEmitter(
                mesh = runtime.requireMesh("particle-quad"),
                material = runtime.requireMaterial("particle"),
                origin = Vec3f(0f, 0f, 0f),
                maxParticles = 80,
                spawnRate = 20f,
                lifetime = 1.2f,
                startAlpha = 0.7f,
                scale = 0.06f,
                motion = ParticleMotion(
                    baseVelocity = Vec3f(0f, 0.1f, 0f),
                    spawnRadius = 0.85f,
                    turbulence = 1.5f,
                    turbulenceFrequency = 1.5f,
                ),
                visual = ParticleVisual(
                    startColor = Vec3f(0.4f, 0.9f, 1f),
                    endColor = Vec3f(1f, 1f, 1f),
                ),
                dynamics = ParticleDynamics(followEntity = followEntity),
            ),
        )
    }

    private fun scalePositions(source: FloatArray, factor: Float): FloatArray {
        val result = source.copyOf()
        var i = 0
        while (i < result.size) {
            result[i] *= factor
            result[i + 1] *= factor
            result[i + 2] *= factor
            i += TEXTURED_VERTEX_STRIDE_COMPONENTS
        }
        return result
    }

    fun createMesh(runtime: SceneAppLifecycleRuntime): Mesh = runtime.renderer.createMesh(
        MeshGeometry(
            requireNotNull(interleaved),
            requireNotNull(indices),
            format = VertexFormat.PositionNormalColorUv,
        ),
    )

    fun createMaterial(runtime: SceneAppLifecycleRuntime): Material =
        runtime.renderer.createMaterial(
            TexturedUniformLayout,
            texture = requireNotNull(texture),
            pbrTextures = pbrTextures,
        )
}
