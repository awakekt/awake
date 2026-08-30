/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.scene.authoring.SceneAssetsDsl
import io.github.awakelab.awake.scene.runtime.Scene
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.showcase.examples.GltfViewerAssets
import io.github.awakelab.awake.showcase.examples.InstancedCubesExampleDriver
import io.github.awakelab.awake.showcase.examples.InstancedSkinnedExampleDriver
import io.github.awakelab.awake.showcase.examples.NavChaseExampleDriver
import io.github.awakelab.awake.showcase.examples.ParticleEmitterExampleDriver
import io.github.awakelab.awake.showcase.examples.SkinnedExampleDriver
import io.github.awakelab.awake.showcase.examples.StreamedNavExampleDriver
import io.github.awakelab.awake.showcase.terrain.TerrainExampleAsset

/** A focused engine proof asset, independently owned by the engine-showcase sample. */
data class EngineShowcase(
    val id: String,
    val title: String,
    val scenePath: String,
    val driver: (SceneAppLifecycleRuntime.(delta: Float) -> Unit)? = null,
    val onActivated: ((instance: Scene, runtime: SceneAppLifecycleRuntime) -> Unit)? = null,
)

/** Public demonstrations moved out of the editor-integration sample. */
val EngineShowcases = listOf(
    EngineShowcase("empty", "Empty", "assets/examples/empty.scene.json"),
    EngineShowcase("point-lights", "Point lights", "assets/examples/point-lights.scene.json"),
    EngineShowcase("heightfield-terrain", "Heightfield terrain", "assets/examples/heightfield-terrain.scene.json"),
    EngineShowcase(
        id = "gltf-viewer",
        title = "glTF viewer",
        scenePath = "assets/examples/gltf-viewer.scene.json",
        onActivated = { instance, runtime -> GltfViewerAssets.attach(instance, runtime) },
    ),
    EngineShowcase(
        id = "skinned-mesh",
        title = "Skinned mesh",
        scenePath = "assets/examples/skinned-mesh.scene.json",
        onActivated = { instance, runtime -> SkinnedExampleDriver.attachPose(instance, runtime) },
    ),
    EngineShowcase(
        id = "instanced-cubes",
        title = "Instanced cubes",
        scenePath = "assets/examples/instanced-cubes.scene.json",
        onActivated = { instance, runtime -> InstancedCubesExampleDriver.attach(instance, runtime) },
    ),
    EngineShowcase(
        id = "instanced-skinned",
        title = "Instanced skinned",
        scenePath = "assets/examples/instanced-skinned.scene.json",
        driver = { delta -> InstancedSkinnedExampleDriver.advance(this, delta) },
        onActivated = { instance, runtime -> InstancedSkinnedExampleDriver.attach(instance, runtime) },
    ),
    EngineShowcase(
        id = "nav-chase",
        title = "Navigation chase",
        scenePath = "assets/examples/nav-chase.scene.json",
        driver = { delta -> NavChaseExampleDriver.advance(this, delta) },
        onActivated = { instance, runtime -> NavChaseExampleDriver.attach(instance, runtime) },
    ),
    EngineShowcase(
        id = "streamed-nav",
        title = "Streamed navigation",
        scenePath = "assets/examples/streamed-nav.scene.json",
        driver = { delta -> StreamedNavExampleDriver.advance(this, delta) },
        onActivated = { instance, runtime -> StreamedNavExampleDriver.attach(instance, runtime) },
    ),
    EngineShowcase(
        id = "particles",
        title = "Particles",
        scenePath = "assets/examples/particles.scene.json",
        driver = { delta -> ParticleEmitterExampleDriver.advance(delta) },
        onActivated = { instance, runtime -> ParticleEmitterExampleDriver.attach(instance, runtime) },
    ),
)

/** Preloads every asset-backed showcase before a frame system activates one. */
suspend fun preloadEngineShowcases() {
    GltfViewerAssets.preload()
    SkinnedExampleDriver.preload()
    InstancedSkinnedExampleDriver.preload()
    ParticleEmitterExampleDriver.preload()
}

/** Registers the meshes and materials the showcase scene documents and drivers request. */
fun SceneAssetsDsl.registerEngineShowcaseAssets() {
    mesh("duck") { GltfViewerAssets.createMesh(this) }
    material("duck-material") { GltfViewerAssets.createMaterial(this) }
    mesh("skinned-mesh") { SkinnedExampleDriver.createMesh(this) }
    material("skinned-material") { SkinnedExampleDriver.createMaterial(this) }
    mesh("instanced-skinned-mesh") { InstancedSkinnedExampleDriver.createMesh(this) }
    material("instanced-skinned-material") { InstancedSkinnedExampleDriver.createMaterial(this) }
    mesh("particle-quad") { ParticleEmitterExampleDriver.createMesh(this) }
    material("particle") { ParticleEmitterExampleDriver.createMaterial(this) }
    material("particle-flicker") { ParticleEmitterExampleDriver.createFlickerMaterial(this) }
    material("particle-levelup") { ParticleEmitterExampleDriver.createLevelupMaterial(this) }
    mesh("heightfield-terrain") { renderer.createMesh(TerrainExampleAsset.geometry) }
    mesh("nav-terrain") { renderer.createMesh(NavChaseExampleDriver.geometry) }
}
