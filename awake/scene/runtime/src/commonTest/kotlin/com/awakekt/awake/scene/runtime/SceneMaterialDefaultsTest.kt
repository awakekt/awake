/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneRenderableRequest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A material authored with its own factors (an imported model's) draws with them unless the entity overrides. */
class SceneMaterialDefaultsTest {
    private val authored = PbrMaterial(metallic = 0f, roughness = 1f)

    private val resolver = object : SceneAssetResolver {
        override fun canResolveMaterial(name: String) = name.startsWith("model:")
        override fun createMaterial(runtime: SceneAppLifecycleRuntime, name: String): Material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }
        override fun materialDefaults(name: String) = authored.takeIf { name == "model:crate" }
    }

    private val library = SceneAssetLibrary(
        meshFactories = mapOf(
            "crate" to {
                object : Mesh {
                    override val format = VertexFormat.PositionNormalColorUv
                    override val sizeBytes = 0L
                    override fun destroy() = Unit
                }
            },
        ),
        materialFactories = emptyMap(),
        rendererFactories = emptyMap(),
        dynamicResolvers = listOf(resolver),
    )

    private val runtime = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = {},
            renderableFactory = { error("no renderable requested in this test") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        ),
    )

    /** Several meshes can draw with one material name (a skinned model's untextured parts), so a resolver can answer by mesh. */
    @Test
    fun aResolverThatKeepsFactorsPerMeshGivesEachMeshItsOwn() {
        val arm = PbrMaterial(metallic = 0.1f, roughness = 0.9f)
        val leg = PbrMaterial(metallic = 0.8f, roughness = 0.2f)
        val perMesh = object : SceneAssetResolver {
            override fun canResolveMaterial(name: String) = name == "shared:skin"
            override fun createMaterial(runtime: SceneAppLifecycleRuntime, name: String): Material = object : Material {
                override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
                override fun destroy() = Unit
            }
            override fun materialDefaults(mesh: String, material: String) = when (mesh) {
                "arm" -> arm
                "leg" -> leg
                else -> null
            }
        }
        fun mesh() = object : Mesh {
            override val format = VertexFormat.PositionNormalColorUv
            override val sizeBytes = 0L
            override fun destroy() = Unit
        }
        val meshFactory: SceneAppLifecycleRuntime.() -> Mesh = { mesh() }
        val limbs = SceneAssetLibrary(
            meshFactories = mapOf("arm" to meshFactory, "leg" to meshFactory, "torso" to meshFactory),
            materialFactories = emptyMap(),
            rendererFactories = emptyMap(),
            dynamicResolvers = listOf(perMesh),
        )
        val world = World()
        fun resolve(mesh: String) = limbs.resolve(runtime, SceneRenderableRequest(world.create(), SceneMeshRenderer(mesh = mesh, material = "shared:skin")))

        assertSame(arm, resolve("arm").defaultMaterial)
        assertSame(leg, resolve("leg").defaultMaterial)
        assertNull(resolve("torso").defaultMaterial, "a mesh the resolver has no factors for draws untouched")
    }

    @Test
    fun aResolverThatOnlyKnowsMaterialsStillAnswersByMaterialForAnyMesh() {
        val world = World()
        val byName = library.resolve(runtime, SceneRenderableRequest(world.create(), SceneMeshRenderer(mesh = "crate", material = "model:crate")))

        assertSame(authored, byName.defaultMaterial, "the default for a mesh is the material's, so nothing changes for resolvers that predate it")
    }

    @Test
    fun aResolvedRendererCarriesItsMaterialsAuthoredFactors() {
        val world = World()
        val withDefaults = library.resolve(runtime, SceneRenderableRequest(world.create(), SceneMeshRenderer(mesh = "crate", material = "model:crate")))
        val without = library.resolve(runtime, SceneRenderableRequest(world.create(), SceneMeshRenderer(mesh = "crate", material = "model:plain")))

        assertSame(authored, withDefaults.defaultMaterial)
        assertNull(without.defaultMaterial)
    }
}
