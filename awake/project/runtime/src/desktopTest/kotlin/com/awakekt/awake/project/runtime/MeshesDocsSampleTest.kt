/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.authoring.dsl.cameraEntity
import com.awakekt.awake.scene.authoring.dsl.meshRenderer
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.rendering.mesh.InstancedMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.LodGroup
import com.awakekt.awake.scene.rendering.mesh.LodLevel
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.spatial.Occluder
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The "Meshes and materials" guide shows one crate as a scene document and in the scene DSL, and
 * the instancing, LOD and occluder components in Kotlin. This keeps them compiling and equal.
 */
class MeshesDocsSampleTest {

    @Test
    fun theSceneDocumentAndTheSceneDslDrawTheSameCrate() = runTest {
        val document = rendering("crate.scene.json")
        val fromDocument = play {
            crateAssets()
            scene(document)
        }
        val fromDsl = play {
            crateAssets()
            // --8<-- [start:crate-dsl]
            onReady {
                world.scene {
                    entity("crate") {
                        transform(y = 0.5f)
                        meshRenderer(requireMesh("cube"), requireMaterial("lit-shadow"), CullMode.Back)
                        with(PbrMaterial(metallic = 0.1f, roughness = 0.8f))
                    }
                }
            }
            // --8<-- [end:crate-dsl]
        }

        val documentCrate = fromDocument.crate()
        val dslCrate = fromDsl.crate()
        assertEquals(documentCrate.renderer.copy(mesh = MESH, material = MATERIAL), dslCrate.renderer.copy(mesh = MESH, material = MATERIAL))
        assertEquals("cube", fromDocument.requireAssetLibrary().meshName(documentCrate.renderer.mesh))
        assertEquals("cube", fromDsl.requireAssetLibrary().meshName(dslCrate.renderer.mesh))
        assertEquals(documentCrate.material, dslCrate.material)
        assertEquals(documentCrate.y, dslCrate.y)
        assertEquals(CullMode.Back, dslCrate.renderer.cullMode)
    }

    @Test
    fun instancingLodAndOccludersAreComponentsOnOneEntityEach() = runTest {
        val renderer = DocsRenderer()
        val game = app {
            scene("props") {
                crateAssets()
                cameraEntity("camera") { transform(y = 2f, z = 8f) }
                onReady {
                    val cube = requireMesh("cube")
                    val lit = requireMaterial("lit-shadow")
                    // --8<-- [start:instancing]
                    world.scene {
                        entity("crates") {
                            // One draw call; each matrix places one copy in world space.
                            with(InstancedMeshRenderer(cube, lit, List(25) { i -> Mat4().translate(i % 5 * 2f, 0f, i / 5 * 2f) }))
                        }
                    }
                    // --8<-- [end:instancing]
                    val (detailed, coarse) = cube to cube
                    // --8<-- [start:lod]
                    world.scene {
                        entity("tower") {
                            transform(x = -4f)
                            // Finest level first; past the last threshold the coarsest still draws.
                            with(LodGroup(listOf(LodLevel(detailed, lit, maxDistance = 20f), LodLevel(coarse, lit, maxDistance = 60f))))
                            with(MeshBounds(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
                        }
                    }
                    // --8<-- [end:lod]
                    // --8<-- [start:occluder]
                    world.scene {
                        entity("wall") {
                            transform(z = 3f, sx = 6f, sy = 3f, sz = 0.2f)
                            meshRenderer(cube, lit, CullMode.Back)
                            with(Occluder(Aabb(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f))))
                        }
                    }
                    // --8<-- [end:occluder]
                }
            }
        }
        game.ready(renderer)
        game.update(FRAME, WIDTH, HEIGHT)

        val instanced = renderer.draws.single { it.instanceModels != null }
        assertEquals(25, instanced.instanceModels!!.size, "25 crates are one draw with 25 instances")
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        assertSame(runtime.requireMesh("cube"), instanced.mesh)
        game.dispose()
    }

    private suspend fun play(block: SceneAppDsl.() -> Unit): SceneAppLifecycleRuntime {
        val game = app { scene("crate", block) }
        game.ready(DocsRenderer())
        return game.requireService<SceneAppLifecycleRuntime>()
    }

    private class Crate(val renderer: MeshRenderer, val material: PbrMaterial?, val y: Float)

    private fun SceneAppLifecycleRuntime.crate(): Crate {
        val entity = requireEntity("crate")
        return Crate(world.get<MeshRenderer>(entity)!!, world.get<PbrMaterial>(entity), requireTransform("crate").position.y)
    }

    private companion object {
        val MESH = DocsRenderer().createMesh(generate { cube() })
        val MATERIAL = DocsRenderer().createMaterial()
    }
}

// --8<-- [start:assets]
/** Registers the names `mesh_renderer` uses: a mesh called "cube" and a material called "lit-shadow". */
fun SceneAppDsl.crateAssets() {
    assets {
        mesh("cube", generate { cube(size = 1f, colored = true) })
        material("lit-shadow") { renderer.createMaterial(LitShadowUniformLayout) }
    }
}
// --8<-- [end:assets]
