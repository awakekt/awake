/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.plugins

import io.github.awakelab.awake.core.geometry.generate.generate
import io.github.awakelab.awake.editor.EditorPlugin
import io.github.awakelab.awake.editor.EditorPluginApi
import io.github.awakelab.awake.editor.EditorPluginId
import io.github.awakelab.awake.editor.EditorPluginMetadata
import io.github.awakelab.awake.editor.EditorProvider
import io.github.awakelab.awake.editor.scene.plugin.AssetResolverPlugin
import io.github.awakelab.awake.scene.authoring.SceneAssetsDsl
import io.github.awakelab.awake.studio.fixture.StudioFixtureBounds

/**
 * Contributes default procedural primitive meshes ("cube" and "ground") for Studio.
 */
class StudioPrimitiveAssetsPlugin : AssetResolverPlugin {
    override val metadata = EditorPluginMetadata(
        id = EditorPluginId("awake.studio.primitives"),
        displayName = "Studio Primitive Meshes",
        version = "1.0.0",
        requiredApiVersion = EditorPluginApi.currentVersion,
    )

    override fun createProviders(): List<EditorProvider> = emptyList()

    override fun registerAssets(dsl: SceneAssetsDsl) {
        dsl.mesh("cube") {
            renderer.createMesh(
                generate { cube(size = 1f, colored = true) }.also {
                    StudioFixtureBounds.register("cube", it)
                },
            )
        }
        dsl.mesh("ground") {
            renderer.createMesh(
                generate { plane(size = 10f, colored = false) }.also {
                    StudioFixtureBounds.register("ground", it)
                },
            )
        }
    }
}
