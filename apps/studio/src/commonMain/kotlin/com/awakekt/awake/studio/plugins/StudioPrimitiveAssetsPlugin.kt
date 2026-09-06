/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.plugins

import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.editor.EditorPlugin
import com.awakekt.awake.editor.EditorPluginApi
import com.awakekt.awake.editor.EditorPluginId
import com.awakekt.awake.editor.EditorPluginMetadata
import com.awakekt.awake.editor.EditorProvider
import com.awakekt.awake.editor.scene.plugin.AssetResolverPlugin
import com.awakekt.awake.scene.authoring.SceneAssetsDsl
import com.awakekt.awake.studio.fixture.StudioFixtureBounds

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
