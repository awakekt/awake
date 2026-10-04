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
import com.awakekt.awake.scene.rendering.mesh.SceneRenderableRequest
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
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

    @Test
    fun aResolvedRendererCarriesItsMaterialsAuthoredFactors() {
        val world = World()
        val withDefaults = library.resolve(runtime, SceneRenderableRequest(world.create(), SceneMeshRenderer(mesh = "crate", material = "model:crate")))
        val without = library.resolve(runtime, SceneRenderableRequest(world.create(), SceneMeshRenderer(mesh = "crate", material = "model:plain")))

        assertSame(authored, withDefaults.defaultMaterial)
        assertNull(without.defaultMaterial)
    }
}
