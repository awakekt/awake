/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.scene.authoring.SceneAssetsDsl
import com.awakekt.awake.scene.core.plugin.GamePlugin
import com.awakekt.awake.scene.document.Scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.showcase.examples.CharacterExampleDriver
import com.awakekt.awake.showcase.examples.GltfViewerAssets
import com.awakekt.awake.showcase.examples.InstancedCubesExampleDriver
import com.awakekt.awake.showcase.examples.InstancedSkinnedExampleDriver
import com.awakekt.awake.showcase.examples.NavChaseExampleDriver
import com.awakekt.awake.showcase.examples.ParticleEmitterExampleDriver
import com.awakekt.awake.showcase.examples.SkinnedExampleDriver
import com.awakekt.awake.showcase.examples.TerrainPhysicsExampleDriver
import com.awakekt.awake.showcase.terrain.TerrainExampleAsset

/** A focused engine proof asset, independently owned by the engine-showcase sample. */
data class EngineShowcase(
    val id: String,
    val title: String,
    val scenePath: String,
    val plugins: List<GamePlugin> = emptyList(),
    val driver: (SceneAppLifecycleRuntime.(delta: Float) -> Unit)? = null,
    val onActivated: ((instance: Scene, runtime: SceneAppLifecycleRuntime) -> Unit)? = null,
    /**
     * Called before this showcase's scene is closed, for anything it created outside that scene.
     *
     * Closing a scene destroys the entities the scene document produced. A showcase that spawns
     * its own — streamed terrain, say — owns those, and without this they survive into whatever
     * runs next: the ground of one demonstration hanging under another.
     */
    val onDeactivated: ((runtime: SceneAppLifecycleRuntime) -> Unit)? = null,
)

/** Public demonstrations moved out of the editor-integration sample. */
val EngineShowcases = listOf(
    EngineShowcase("empty", "Empty", "assets/examples/empty.scene.json"),
    EngineShowcase("point-lights", "Point lights", "assets/examples/point-lights.scene.json"),
    // The one showcase that runs a real Jolt world: four boxes fall onto the same heightfield
    // samples the terrain mesh is built from, on a fixed timestep.
    EngineShowcase(
        id = "heightfield-terrain",
        title = "Heightfield terrain",
        scenePath = "assets/examples/heightfield-terrain.scene.json",
        onActivated = { instance, runtime ->
            TerrainPhysicsExampleDriver.attach(instance, runtime)
            CharacterExampleDriver.attach(instance, runtime)
        },
        onDeactivated = { runtime ->
            CharacterExampleDriver.detach(runtime.world)
            TerrainPhysicsExampleDriver.detach(runtime)
        },
    ),
    // Casters at 6, -6, -26 and -60 along the view: one per cascade, so the near shadow is sharp
    // and the far one still exists. A single fixed shadow box covers only the first of them,
    // which is the difference this demonstration is for -- turn on "Shadow cascades" to see the
    // boxes the depth pass actually renders from.
    EngineShowcase("cascaded-shadows", "Cascaded shadows", "assets/examples/cascaded-shadows.scene.json"),
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
