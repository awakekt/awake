/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.scene.authoring.SceneAssetsDsl
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneContent
import com.awakekt.awake.showcase.examples.CharacterExampleDriver
import com.awakekt.awake.showcase.examples.EcsStressExampleDriver
import com.awakekt.awake.showcase.examples.GltfViewerAssets
import com.awakekt.awake.showcase.examples.InstancedCubesExampleDriver
import com.awakekt.awake.showcase.examples.InstancedSkinnedExampleDriver
import com.awakekt.awake.showcase.examples.NavChaseExampleDriver
import com.awakekt.awake.showcase.examples.ParticleEmitterExampleDriver
import com.awakekt.awake.showcase.examples.SkinnedExampleDriver
import com.awakekt.awake.showcase.examples.Sprites2dExampleDriver
import com.awakekt.awake.showcase.examples.TerrainPhysicsExampleDriver
import com.awakekt.awake.showcase.terrain.TerrainExampleAsset
import com.awakekt.awake.showcase.ui.EcsStressControls

/**
 * One focused engine demonstration: a scene document plus whatever that document cannot express.
 *
 * [id] names it on the command line (`-Pawake.showcase=<id>`) and must equal the scene document's
 * `name`; [summary] is the one sentence it proves, which the README table repeats.
 */
data class EngineShowcase(
    val id: String,
    val title: String,
    val scenePath: String,
    val summary: String,
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
    /** Diagnostics specific to this sample; engine-wide controls stay visible separately. */
    val debugOptions: Set<ShowcaseDebugOption> = emptySet(),
    /** Controls only this demonstration has, drawn in the debug card under its diagnostics. */
    val controls: SceneContent? = null,
)

/** Every demonstration, in the order the switcher lists them. */
val EngineShowcases = listOf(
    EngineShowcase(
        id = "empty",
        title = "Empty",
        scenePath = "assets/examples/empty.scene.json",
        summary = "The scene document, camera and clear pass with nothing in them: the baseline a broken frame is compared against.",
    ),
    EngineShowcase(
        id = "point-lights",
        title = "Point lights",
        scenePath = "assets/examples/point-lights.scene.json",
        summary = "Several coloured point lights over a ground plane and lit cubes.",
    ),
    // The one showcase that runs a real Jolt world: four boxes fall onto the same heightfield
    // samples the terrain mesh is built from, on a fixed timestep.
    EngineShowcase(
        id = "heightfield-terrain",
        title = "Heightfield terrain",
        scenePath = "assets/examples/heightfield-terrain.scene.json",
        summary = "A Heightmap becomes drawable geometry and a Jolt heightfield that boxes and a character land on.",
        onActivated = { instance, runtime ->
            TerrainPhysicsExampleDriver.attach(instance, runtime)
            CharacterExampleDriver.attach(instance, runtime)
        },
        onDeactivated = { runtime ->
            CharacterExampleDriver.detach(runtime.world)
            TerrainPhysicsExampleDriver.detach(runtime)
        },
        debugOptions = setOf(
            ShowcaseDebugOption.Colliders,
            ShowcaseDebugOption.TerrainProbes,
        ),
    ),
    // Casters at 6, -6, -26 and -60 along the view: one per cascade, so the near shadow is sharp
    // and the far one still exists. A single fixed shadow box covers only the first of them,
    // which is the difference this demonstration is for -- turn on "Shadow cascades" to see the
    // boxes the depth pass actually renders from.
    EngineShowcase(
        id = "cascaded-shadows",
        title = "Cascaded shadows",
        scenePath = "assets/examples/cascaded-shadows.scene.json",
        summary = "One shadow caster per cascade, so the near shadow is sharp and the far one still exists.",
    ),
    EngineShowcase(
        id = "gltf-viewer",
        title = "glTF viewer",
        scenePath = "assets/examples/gltf-viewer.scene.json",
        summary = "glTF mesh, material and texture import.",
        onActivated = { instance, runtime -> GltfViewerAssets.attach(instance, runtime) },
    ),
    EngineShowcase(
        id = "skinned-mesh",
        title = "Skinned mesh",
        scenePath = "assets/examples/skinned-mesh.scene.json",
        summary = "Joint palette upload and GPU skinning.",
        onActivated = { instance, runtime -> SkinnedExampleDriver.attachPose(instance, runtime) },
    ),
    EngineShowcase(
        id = "instanced-cubes",
        title = "Instanced cubes",
        scenePath = "assets/examples/instanced-cubes.scene.json",
        summary = "One draw call for a 16x16x4 block of cubes through InstancedMeshRenderer.",
        onActivated = { instance, runtime -> InstancedCubesExampleDriver.attach(instance, runtime) },
    ),
    EngineShowcase(
        id = "instanced-skinned",
        title = "Instanced skinned",
        scenePath = "assets/examples/instanced-skinned.scene.json",
        summary = "Instancing and skinning together, animated per frame.",
        driver = { delta -> InstancedSkinnedExampleDriver.advance(this, delta) },
        onActivated = { instance, runtime -> InstancedSkinnedExampleDriver.attach(instance, runtime) },
    ),
    EngineShowcase(
        id = "nav-chase",
        title = "Navigation chase",
        scenePath = "assets/examples/nav-chase.scene.json",
        summary = "A cube paths around a terrain ridge it cannot climb; bakeNavGrid reads the slope, no collider says a wall exists.",
        driver = { delta -> NavChaseExampleDriver.advance(this, delta) },
        onActivated = { instance, runtime -> NavChaseExampleDriver.attach(instance, runtime) },
        debugOptions = setOf(
            ShowcaseDebugOption.NavGrid,
            ShowcaseDebugOption.Corridor,
        ),
    ),
    EngineShowcase(
        id = "particles",
        title = "Particles",
        scenePath = "assets/examples/particles.scene.json",
        summary = "A CPU emitter driving a quad batch, advanced every frame.",
        driver = { delta -> ParticleEmitterExampleDriver.advance(delta) },
        onActivated = { instance, runtime -> ParticleEmitterExampleDriver.attach(instance, runtime) },
    ),
    EngineShowcase(
        id = "sprites-2d",
        title = "2D sprites",
        scenePath = "assets/examples/sprites-2d.scene.json",
        summary = "Orthographic parallel projection, 2D layer sorting, and animated sprite quads.",
        driver = { delta -> Sprites2dExampleDriver.advance(this, delta) },
        onActivated = { instance, runtime -> Sprites2dExampleDriver.attach(instance, runtime) },
        onDeactivated = { _ -> Sprites2dExampleDriver.detach() },
    ),
    // Ten thousand ordinary entities by default, up to a hundred thousand from the panel. Each is
    // moved by an ECS system every frame; the renderer instances them, so the frame rate shows
    // what the per-entity work costs rather than what a draw call costs.
    EngineShowcase(
        id = "ecs-stress",
        title = "ECS stress",
        scenePath = "assets/examples/ecs-stress.scene.json",
        summary = "Up to 100,000 moving entities, each with Transform and MeshRenderer components, drawn in a few instanced calls.",
        driver = { _ -> EcsStressExampleDriver.advance(this) },
        onActivated = { _, runtime -> EcsStressExampleDriver.attach(runtime) },
        onDeactivated = { runtime -> EcsStressExampleDriver.detach(runtime.world) },
        controls = { EcsStressControls() },
    ),
)

/** Preloads every asset-backed showcase before a frame system activates one. */
suspend fun preloadEngineShowcases() {
    com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers.install()
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
    mesh("sprite-quad") { Sprites2dExampleDriver.createMesh(this) }
    mesh("heightfield-terrain") { renderer.createMesh(TerrainExampleAsset.geometry) }
    mesh("nav-terrain") { renderer.createMesh(NavChaseExampleDriver.geometry) }
    repeat(EcsStressExampleDriver.paletteSize) { index ->
        mesh(EcsStressExampleDriver.meshName(index)) { renderer.createMesh(EcsStressExampleDriver.cubeGeometry(index)) }
    }
}
