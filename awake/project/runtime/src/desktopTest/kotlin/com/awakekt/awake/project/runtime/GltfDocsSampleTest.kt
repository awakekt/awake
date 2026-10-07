/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.authoring.dsl.meshRenderer
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The "glTF models" guide: a model placed by path, in a scene document and in the scene DSL. */
class GltfDocsSampleTest {

    @Test
    fun theSceneDocumentAndTheSceneDslDrawTheSameModel() = runTest {
        val files = files("assets/models/statue.gltf" to triangleGltf())
        val document = rendering("model.scene.json")

        // --8<-- [start:resolver]
        val models = GltfAssetResolver().apply { setAssetSource(files) }
        models.preload("assets/models/statue.gltf")
        val game = app {
            scene("gallery") {
                assets {
                    builtInSceneAssets() // "lit-shadow" and the built-in meshes
                    resolver(models) // any mesh name ending in .gltf or .glb
                }
                scene(document)
            }
        }
        // --8<-- [end:resolver]
        game.ready(DocsRenderer())
        val fromDocument = game.requireService<SceneAppLifecycleRuntime>().statue()

        val dslGame = app {
            scene("gallery") {
                assets {
                    builtInSceneAssets()
                    resolver(models)
                }
                // --8<-- [start:dsl]
                onReady {
                    world.scene {
                        entity("statue") {
                            meshRenderer(
                                requireMesh("assets/models/statue.gltf"),
                                requireMaterial(models.materialName("assets/models/statue.gltf")),
                            )
                        }
                    }
                }
                // --8<-- [end:dsl]
            }
        }
        dslGame.ready(DocsRenderer())
        val fromDsl = dslGame.requireService<SceneAppLifecycleRuntime>().statue()

        assertEquals("assets/models/statue.gltf", fromDocument.first)
        assertEquals(fromDocument, fromDsl)
        assertEquals("lit-shadow", models.materialName("assets/models/statue.gltf"), "an untextured model draws with lit-shadow")
    }

    @Test
    fun aProjectLoadsTheModelsItsSceneNames() = runTest {
        val files = files(
            "awake.project.json" to MANIFEST,
            "scenes/main.scene.json" to java.io.File(DOCS_SNIPPETS, "rendering/model.scene.json").readText(),
            "assets/models/statue.gltf" to triangleGltf(),
        )
        // --8<-- [start:project]
        val project = loadProject(files)
        val game = app { scene("play") { runProject(project) } }
        // --8<-- [end:project]
        game.ready(DocsRenderer())

        assertEquals("assets/models/statue.gltf", game.requireService<SceneAppLifecycleRuntime>().statue().first)
    }

    /** The statue's mesh name and cull mode: what a scene document and the scene DSL both set. */
    private fun SceneAppLifecycleRuntime.statue(): Pair<String?, Any> {
        val renderer = world.get<MeshRenderer>(requireEntity("statue"))!!
        return requireAssetLibrary().meshName(renderer.mesh) to renderer.cullMode
    }

    private companion object {
        const val MANIFEST =
            """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
    }
}
