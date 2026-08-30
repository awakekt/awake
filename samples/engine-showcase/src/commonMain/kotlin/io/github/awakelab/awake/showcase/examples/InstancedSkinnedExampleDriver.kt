/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.examples

import io.github.awakelab.awake.asset.gltf.GltfParser
import io.github.awakelab.awake.asset.gltf.firstSkinnedAsset
import io.github.awakelab.awake.core.animation.AnimationClip
import io.github.awakelab.awake.core.animation.AnimationPose
import io.github.awakelab.awake.core.animation.Skin
import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.core.host.readResourceBytes
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.render.renderer.InstancedUniformLayout
import io.github.awakelab.awake.render.renderer.createMaterial
import io.github.awakelab.awake.scene.rendering.components.InstancedSkinnedMeshRenderer
import io.github.awakelab.awake.scene.rendering.components.SkinnedInstance
import io.github.awakelab.awake.scene.runtime.Scene
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime

private const val GRID_SIDE = 3
private const val SPACING = 1.2f

/** CesiumMan.gltf, parsed once, resampled per instance each frame -- same shape
 * [SkinnedExampleDriver] uses for the single-instance case, but one [SkinnedInstance] per grid
 * cell instead of one [io.github.awakelab.awake.scene.rendering.components.SkinnedPose].
 * Each instance's clock is phase-offset so their walk cycles visibly drift apart, proving the
 * per-instance POSE varies, not just the per-instance transform. `InstancedSkinnedMeshRenderer`
 * isn't an authorable scene component yet, same reason [InstancedCubesExampleDriver] attaches
 * its component post-instantiate instead of via the scene document. */
internal object InstancedSkinnedExampleDriver {
    private var vertices: FloatArray? = null
    private var indices: IntArray? = null
    private var skin: Skin? = null
    private var clip: AnimationClip? = null
    private var pose: AnimationPose? = null
    private var elapsedSeconds = 0f

    suspend fun preload() {
        if (skin != null) return
        val bytes = readResourceBytes("assets/models/CesiumMan.gltf")
        val loaded = GltfParser.parseSkinned(bytes.decodeToString())
        val asset =
            requireNotNull(loaded.firstSkinnedAsset()) { "CesiumMan.gltf has no skinned node." }
        vertices = asset.mesh.toInterleavedSkinned()
        indices = asset.mesh.indices
        skin = asset.skin
        clip = asset.clip
        pose = AnimationPose(asset.skeleton)
    }

    fun createMesh(runtime: SceneAppLifecycleRuntime): Mesh = runtime.renderer.createMesh(
        MeshGeometry(
            requireNotNull(vertices),
            requireNotNull(indices),
            format = VertexFormat.PositionNormalColorSkin,
        ),
    )

    fun createMaterial(runtime: SceneAppLifecycleRuntime): Material =
        runtime.renderer.createMaterial(InstancedUniformLayout)

    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        val node = instance.roots.find { it.name == "instanced-skinned" } ?: return
        val mesh = runtime.requireMesh("instanced-skinned-mesh")
        val material = runtime.requireMaterial("instanced-skinned-material")
        runtime.world.add(
            node.entity,
            InstancedSkinnedMeshRenderer(mesh, material, sampleInstances()),
        )
    }

    fun advance(runtime: SceneAppLifecycleRuntime, delta: Float) {
        elapsedSeconds += delta
        runtime.world.queryEach<InstancedSkinnedMeshRenderer> { entity, existing ->
            runtime.world.add(entity, existing.copy(instances = sampleInstances()))
        }
    }

    private fun sampleInstances(): List<SkinnedInstance> {
        val currentPose = pose
        val currentSkin = skin
        if (currentPose == null || currentSkin == null) return emptyList()
        val currentClip = clip
        val duration = currentClip?.duration ?: 0f
        val total = GRID_SIDE * GRID_SIDE
        val instances = ArrayList<SkinnedInstance>(total)
        for (index in 0 until total) {
            if (currentClip != null && duration > 0f) {
                val phase = (elapsedSeconds + index * duration / total) % duration
                currentPose.sample(currentClip, phase)
            }
            instances += SkinnedInstance(
                transform = gridTransforms[index],
                jointPalette = currentPose.jointPalette(currentSkin),
            )
        }
        return instances
    }

    private val gridTransforms: List<Mat4> = buildList {
        val offset = (GRID_SIDE - 1) * SPACING / 2f
        for (index in 0 until GRID_SIDE * GRID_SIDE) {
            add(
                Mat4().translate(
                    (index % GRID_SIDE) * SPACING - offset,
                    0f,
                    (index / GRID_SIDE) * SPACING - offset,
                ),
            )
        }
    }
}
